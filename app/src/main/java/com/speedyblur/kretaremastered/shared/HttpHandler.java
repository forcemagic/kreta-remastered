package com.speedyblur.kretaremastered.shared;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.speedyblur.kretaremastered.BuildConfig;
import com.speedyblur.kretaremastered.R;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CertificatePinner;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpHandler {

    private static final String hostname = "www.speedyblur.com";
    private static final CertificatePinner certPinner = new CertificatePinner.Builder()
            .add(hostname, "sha256/3mbCPRv7AYBP30hhmhMuNVF5ePn1Tm9BLWtQ4TiTCTU=")
            .add(hostname, "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=")
            .add(hostname, "sha256/Vjs8r4z+80wjNcr1YKepWQboSIRi63WsWXhIMN+eWys=")
            .build();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder().certificatePinner(certPinner).build();

    /**
     * Issues a GET request against url. Parses JSON for the callback.
     * @param url the target URL
     * @param callback the callback to return to when the request has been executed
     */
    @SuppressWarnings("unused")
    public static void getJson(String url, JsonRequestCallback callback) {
        getJson(url, new ArrayMap<String, String>(), callback);
    }

    /**
     * Issues a GET request against url with added headers. Parses JSON for the callback.
     * @param url the target URL
     * @param headers ArrayMap of headers
     * @param callback the callback to return to when the request has been executed
     */
    public static void getJson(String url, ArrayMap<String, String> headers, final JsonRequestCallback callback) {
        Request req = buildReq("GET", url, null, headers);
        Log.v("HttpHandler", "Dispatching GET "+url);
        httpClient.newCall(req).enqueue(new MainCallbackHandler(callback));
    }

    /**
     * Issues a POST request against url. Parses JSON for the callback.
     * @param url the target URL
     * @param payload the payload (request body) to send
     * @param callback the callback to return to when the request has been executed
     */
    public static void postJson(String url, JSONObject payload, JsonRequestCallback callback) {
        postJson(url, payload, new ArrayMap<String, String>(), callback);
    }

    /**
     * Issues a POST request against url with added headers. Parses JSON for the callback.
     * @param url the target URL
     * @param payload the payload (request body) to send
     * @param headers ArrayMap of headers
     * @param callback the callback to return to when the request has been executed
     */
    @SuppressWarnings("WeakerAccess")
    public static void postJson(String url, JSONObject payload, ArrayMap<String, String> headers, final JsonRequestCallback callback) {
        Request req = buildReq("POST", url, RequestBody.create(MediaType.parse("application/json"), payload.toString()), headers);
        Log.v("HttpHandler", "Dispatching POST "+url);
        httpClient.newCall(req).enqueue(new MainCallbackHandler(callback));
    }

    private static Request buildReq(String method, String url, @Nullable RequestBody payload, ArrayMap<String, String> headers) {
        Request.Builder reqBuild = new Request.Builder().url(url);
        reqBuild.header("User-Agent", "zsirkreta/" + BuildConfig.VERSION_NAME + " " + okhttp3.internal.Version.userAgent());
        for (int i=0; i<headers.size(); i++) {
            reqBuild.header(headers.keyAt(i), headers.valueAt(i));
        }

        if (method.equals("GET")) {
            return reqBuild.get().build();
        } else if (method.equals("DELETE")) {
            // DELETE can have a body
            if (payload != null) {
                return reqBuild.delete(payload).build();
            } else {
                return reqBuild.delete().build();
            }
        } else if (payload != null) {
            // POST, PUT and PATCH (should) all have bodies
            return reqBuild.method(method, payload).build();
        } else {
            throw new UnsupportedOperationException("You have specified a request (either POST, PUT or PATCH), which needs a body. (Other request types are not supported.)");
        }
    }

    /**
     * Master callback for a JSON request.
     */
    public interface JsonRequestCallback {
        /**
         * Function is called when a request completes successfully.
         * (The status code must be 200 OK)
         * @param resp the response body (parsed JSON)
         */
        void onComplete(JsonElement resp) throws JsonParseException;

        /**
         * Function is called when a request fails.
         * @param localizedError a string resource ID containing the (localized) error explanation
         */
        void onFailure(@StringRes final int localizedError);
    }

    private static class MainCallbackHandler implements Callback {

        private final JsonRequestCallback jsCallback;

        private MainCallbackHandler(JsonRequestCallback callback) {
            this.jsCallback = callback;
        }

        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            if (e.getMessage().equals("timeout")) {
                Log.e("HttpHandler", "Timeout error.");
                jsCallback.onFailure(R.string.http_timeout);
            } else if (e.getMessage().equals("connect timed out")) {
                Log.e("HttpHandler", "Request timeout error (server not responding).");
                jsCallback.onFailure(R.string.http_connect_timeout);
            } else if (e.getMessage().contains("No address associated with hostname")) {
                Log.e("HttpHandler", "Unable to connect: Can't resolve hostname.");
                jsCallback.onFailure(R.string.http_cant_resolve);
            } else {
                Log.e("HttpHandler", "Unknown error: "+e.getMessage());
                jsCallback.onFailure(R.string.http_unknown);
            }
        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
            if (response.isSuccessful()) {
                try {
                    //noinspection ConstantConditions
                    jsCallback.onComplete(new JsonParser().parse(response.body().string()));
                } catch (JsonParseException e) {
                    Log.e("HttpHandler", "Unable to parse JSON (or tried to get a nonexistent object). Dumping request...");
                    jsCallback.onFailure(R.string.http_server_error);
                    e.printStackTrace();
                }
            } else {
                if (response.code() == HttpURLConnection.HTTP_FORBIDDEN) {
                    Log.e("HttpHandler", "Got 403 (Forbidden) from server.");
                    jsCallback.onFailure(R.string.http_unauthorized);
                } else if (response.code() == HttpURLConnection.HTTP_BAD_GATEWAY) {
                    Log.e("HttpHandler", "Got 502 (Bad Gateway) from server.");
                    jsCallback.onFailure(R.string.http_bad_gateway);
                } else {
                    Log.e("HttpHandler", "Got unknown error code from server: "+response.code());
                    jsCallback.onFailure(R.string.http_server_error);
                }
            }
        }
    }
}
