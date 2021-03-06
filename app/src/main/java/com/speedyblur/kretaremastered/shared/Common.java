package com.speedyblur.kretaremastered.shared;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.speedyblur.kretaremastered.models.Bulletin;
import com.speedyblur.kretaremastered.models.Average;
import com.speedyblur.kretaremastered.models.AvgGraphData;
import com.speedyblur.kretaremastered.models.AvgGraphDataDeserializer;
import com.speedyblur.kretaremastered.models.Clazz;
import com.speedyblur.kretaremastered.models.ClazzDeserializer;
import com.speedyblur.kretaremastered.models.Grade;
import com.speedyblur.kretaremastered.models.Profile;
import com.speedyblur.kretaremastered.receivers.BirthdayReceiver;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class Common {
    public static final String APIBASE = "https://www.speedyblur.com/kretaapi/v6";
    public static String SQLCRYPT_PWD = "weeee";
    private static String API_HOLDER;
    private static String APIKEY;
    private static long API_EXPIRY = -1;

    public static String getLocalizedSubjectName(Context context, String subject) {
        int gotResxId = context.getResources().getIdentifier("subject_" + subject, "string", context.getPackageName());
        return gotResxId == 0 ? subject : context.getResources().getString(gotResxId);
    }

    // Account fetching
    public static void fetchAccountAsync(final Activity a, final Profile profile, final IFetchAccount ifa) {
        if (API_EXPIRY < Calendar.getInstance().getTimeInMillis() || !profile.getCardid().equals(API_HOLDER)) {
            Log.v("KretaApi", "Renewing API key.");

            JSONObject payload = new JSONObject();
            try {
                payload.put("username", profile.getCardid());
                payload.put("password", profile.getPasswd());
                payload.put("baseuri", profile.getInstitute().getId());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            HttpHandler.postJson(Common.APIBASE + "/auth", payload, new HttpHandler.JsonRequestCallback() {
                @Override
                public void onComplete(JsonElement resp) throws JsonParseException {
                    APIKEY = resp.getAsJsonObject().get("token").getAsString();

                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.MINUTE, 30);
                    API_EXPIRY = c.getTimeInMillis();
                    API_HOLDER = profile.getCardid();

                    realFetchAccountAsync(a, profile, ifa);
                }

                @Override
                public void onFailure(final int localizedError) {
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ifa.onFetchError(localizedError);
                        }
                    });
                }
            });
        } else realFetchAccountAsync(a, profile, ifa);
    }

    private static void realFetchAccountAsync(final Activity a, final Profile profile, final IFetchAccount ifa) {
        ArrayMap<String, String> headers = new ArrayMap<>();
        headers.put("X-Auth-Token", APIKEY);

        final long loadBeginTime = System.currentTimeMillis();
        HttpHandler.getJson(Common.APIBASE + "/bundle", headers, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JsonElement resp) throws JsonParseException {
                ArrayList<Grade> grades = new ArrayList<>();
                ArrayList<Average> averages = new ArrayList<>();
                ArrayList<AvgGraphData> avgGraphDatas = new ArrayList<>();
                ArrayList<Clazz> clazzes = new ArrayList<>();
                ArrayList<Bulletin> bulletins = new ArrayList<>();

                JsonArray rawGrades = resp.getAsJsonObject().get("grades").getAsJsonObject().get("data").getAsJsonArray();
                JsonArray rawAvg = resp.getAsJsonObject().get("avg").getAsJsonObject().get("data").getAsJsonArray();
                JsonArray rawGraphData = resp.getAsJsonObject().get("avggraph").getAsJsonObject().get("data").getAsJsonArray();
                JsonArray rawClazzes = resp.getAsJsonObject().get("schedule").getAsJsonObject().get("data").getAsJsonObject().get("classes").getAsJsonArray();
//                                JsonArray rawAlldayEvents = resp.getAsJsonObject().get("schedule").getAsJsonObject().get("data").getAsJsonObject().get("allday").getAsJsonArray();
                JsonArray rawBulletins = resp.getAsJsonObject().get("announcements").getAsJsonObject().get("data").getAsJsonArray();
                for (int i = 0; i < rawGrades.size(); i++) {
                    Grade g = new Gson().fromJson(rawGrades.get(i), Grade.class);
                    grades.add(g);
                }
                for (int i = 0; i < rawAvg.size(); i++) {
                    Average avg = new Gson().fromJson(rawAvg.get(i), Average.class);
                    averages.add(avg);
                }
                for (int i = 0; i < rawGraphData.size(); i++) {
                    GsonBuilder gsonBuilder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE);
                    gsonBuilder.registerTypeAdapter(AvgGraphData.class, new AvgGraphDataDeserializer());
                    AvgGraphData agd = gsonBuilder.create().fromJson(rawGraphData.get(i), AvgGraphData.class);
                    avgGraphDatas.add(agd);
                }
                for (int i = 0; i < rawClazzes.size(); i++) {
                    GsonBuilder gsonBuilder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE);
                    gsonBuilder.registerTypeAdapter(Clazz.class, new ClazzDeserializer());
                    Clazz c = gsonBuilder.create().fromJson(rawClazzes.get(i), Clazz.class);
                    clazzes.add(c);
                }
                for (int i = 0; i < rawBulletins.size(); i++) {
                    Bulletin a = new Gson().fromJson(rawBulletins.get(i), Bulletin.class);
                    bulletins.add(a);
                }

                try {
                    DataStore ds = new DataStore(a, profile.getCardid(), Common.SQLCRYPT_PWD);
                    ds.putGradesData(grades);
                    ds.putAveragesData(averages);
                    ds.putAverageGraphData(avgGraphDatas);
                    ds.upsertClassData(clazzes);
                    ds.upsertAnnouncementsData(bulletins);
                    ds.close();
                } catch (DecryptionException e) {e.printStackTrace();}

                final long loadEndTime = System.currentTimeMillis();
                Log.v("KretaApi", "Fetch completed in " + String.valueOf(loadEndTime - loadBeginTime) + "ms");
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ifa.onFetchComplete();
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ifa.onFetchError(localizedError);
                    }
                });
            }
        });
    }

    public interface IFetchAccount {
        void onFetchComplete();
        void onFetchError(int localizedErrorMsg);
    }

    // Happy birthday reminder
    public static void registerBirthdayReminder(Context ctxt, Calendar date) {
        if (PendingIntent.getBroadcast(ctxt, 0, new Intent(ctxt, BirthdayReceiver.class), PendingIntent.FLAG_NO_CREATE) != null) {
            Log.w("AlarmManager", "Alarm already set, not setting another one.");
            return;
        }
        AlarmManager alarmMgr = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        Intent it = new Intent(ctxt, BirthdayReceiver.class);
        PendingIntent pit = PendingIntent.getBroadcast(ctxt, 0, it, 0);

        alarmMgr.set(AlarmManager.RTC_WAKEUP, date.getTimeInMillis(), pit);
        Log.v("AlarmManager", String.format(Locale.ENGLISH, "Will fire BDay notfication at T-%d", (date.getTimeInMillis() - Calendar.getInstance().getTimeInMillis())));
    }
}
