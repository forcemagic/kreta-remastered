package com.speedyblur.kretaremastered.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.speedyblur.kretaremastered.R;
import com.speedyblur.kretaremastered.models.AllDayEvent;
import com.speedyblur.kretaremastered.models.Clazz;
import com.speedyblur.kretaremastered.models.ClazzDeserializer;
import com.speedyblur.kretaremastered.models.Institute;
import com.speedyblur.kretaremastered.models.Profile;
import com.speedyblur.kretaremastered.shared.AccountStore;
import com.speedyblur.kretaremastered.shared.Common;
import com.speedyblur.kretaremastered.shared.DataStore;
import com.speedyblur.kretaremastered.shared.DecryptionException;
import com.speedyblur.kretaremastered.shared.HttpHandler;

import net.sqlcipher.database.SQLiteConstraintException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

public class NewProfileActivity extends AppCompatActivity {

    private boolean doShowMenu = true;
    private Institute selectedInstitute;
    private ArrayList<Institute> institutes = new ArrayList<>();
    private ArrayAdapter<Institute> instituteArrayAdapter;

    // UI references.
    private ViewFlipper mLoginFlipperView;
    private EditText mFriendlyNameView;
    private EditText mIdView;
    private EditText mPasswordView;
    private ProgressBar mProgressBar;
    private TextView mProgressStatusView;
    private TextView mInstituteTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newprofile);

        assert getSupportActionBar() != null;
        getSupportActionBar().setTitle(R.string.profile_add);
        if (!getIntent().getBooleanExtra("doOpenMainActivity", false))
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // UI setup
        mIdView = findViewById(R.id.studentid);
        mPasswordView = findViewById(R.id.password);
        mFriendlyNameView = findViewById(R.id.friendlyname);
        mLoginFlipperView = findViewById(R.id.login_flipper);
        mProgressStatusView = findViewById(R.id.login_progress_status);
        mProgressBar = findViewById(R.id.login_progress);
        mInstituteTextView = findViewById(R.id.instituteSelectorText);
        final RelativeLayout mInstituteSelectorView = findViewById(R.id.instituteSelector);

        mFriendlyNameView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mInstituteSelectorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (institutes.size() == 0 || instituteArrayAdapter == null) {
                    showOnSnackbar(R.string.didnt_load_institutes, Snackbar.LENGTH_LONG);
                    return;
                }

                View toSet = LayoutInflater.from(NewProfileActivity.this).inflate(R.layout.dialog_choose_institute, null);

                ListView lv = toSet.findViewById(R.id.instituteList);
                SearchView sv = toSet.findViewById(R.id.instituteSearch);

                lv.setAdapter(instituteArrayAdapter);

                sv.setIconifiedByDefault(false);
                sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        instituteArrayAdapter.getFilter().filter(newText);
                        return true;
                    }
                });

                final AlertDialog dialog = new AlertDialog.Builder(NewProfileActivity.this).setView(toSet).show();
                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        selectedInstitute = (Institute) parent.getAdapter().getItem(position);
                        assert selectedInstitute != null;

                        mInstituteTextView.setText(selectedInstitute.toString());
                        mInstituteTextView.setTextColor(ContextCompat.getColor(NewProfileActivity.this, android.R.color.black));
                        dialog.dismiss();
                    }
                });

                sv.requestFocus();
            }
        });

        // TODO: CACHE
        HttpHandler.getJson(Common.APIBASE+"/institutes", new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JsonElement resp) throws JsonParseException {
                for (int i=0; i<resp.getAsJsonArray().size(); i++) {
                    institutes.add(new Gson().fromJson(resp.getAsJsonArray().get(i), Institute.class));
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        instituteArrayAdapter = new ArrayAdapter<>(NewProfileActivity.this, android.R.layout.simple_spinner_dropdown_item, institutes);
                    }
                });
            }

            @Override
            public void onFailure(int localizedError) {
                showOnSnackbar(localizedError, Snackbar.LENGTH_LONG);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (doShowMenu) {
            getMenuInflater().inflate(R.menu.new_profile, menu);
            return true;
        } else return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_create_profile) {
            attemptLogin();
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sign in method
     */
    private void attemptLogin() {
        mIdView.setError(null); mPasswordView.setError(null);

        String studentId = mIdView.getText().toString();
        String password = mPasswordView.getText().toString();
        String friendlyName = mFriendlyNameView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Validations
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView; cancel = true;
        }
        if (TextUtils.isEmpty(studentId)) {
            mIdView.setError(getString(R.string.error_field_required));
            focusView = mIdView; cancel = true;
        }
        if (selectedInstitute == null) {
            mInstituteTextView.setError(getString(R.string.error_field_required));
            focusView = mInstituteTextView; cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            showProgress(true);
            doLoginSaveIfValid(studentId, password, friendlyName, selectedInstitute);
        }
    }

    /**
     * This function ONLY CHECKS for login data validity and saves it into the cryptSql DB. It DOES NOT save the authentication token!
     * @param studentId the student's card's ID
     * @param passwd the password to use
     * @param friendlyName a friendly name for the profile
     */
    private void doLoginSaveIfValid(final String studentId, final String passwd, final String friendlyName, final Institute institute) {
        mProgressBar.setIndeterminate(true);
        mProgressBar.setProgress(0);
        mProgressStatusView.setText(R.string.loading_logging_in);

        // Populate a JSONObject with the payload
        JSONObject payload = new JSONObject();
        try {
            payload.put("username", studentId);
            payload.put("password", passwd);
            payload.put("baseuri", institute.getId());
        } catch (JSONException e) { e.printStackTrace(); }

        // Enqueue request
        HttpHandler.postJson(Common.APIBASE + "/auth", payload, new HttpHandler.JsonRequestCallback() {
            @Override
            public void onComplete(JsonElement resp) throws JsonParseException {
                final Profile p = new Profile(studentId, passwd, friendlyName, institute);

                ArrayMap<String, String> headers = new ArrayMap<>();
                headers.put("X-Auth-Token", resp.getAsJsonObject().get("token").getAsString());
                HttpHandler.getJson(Common.APIBASE + "/schedule", headers, new HttpHandler.JsonRequestCallback() {
                    @Override
                    public void onComplete(JsonElement resp) throws JsonParseException {
                        try {
                            ArrayList<AllDayEvent> allDayEvents = new ArrayList<>();
                            JsonArray unparsedAllDayEvents = resp.getAsJsonObject().get("data").getAsJsonObject().get("allday").getAsJsonArray();
                            for (int i = 0; i < unparsedAllDayEvents.size(); i++) {
                                AllDayEvent ade = new Gson().fromJson(unparsedAllDayEvents.get(i).toString(), AllDayEvent.class);
                                allDayEvents.add(ade);
                            }

                            ArrayList<Clazz> clazzes = new ArrayList<>();
                            JsonArray unparsedClazzes = resp.getAsJsonObject().get("data").getAsJsonObject().get("classes").getAsJsonArray();
                            for (int i = 0; i < unparsedClazzes.size(); i++) {
                                GsonBuilder gsonBuilder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE);
                                gsonBuilder.registerTypeAdapter(Clazz.class, new ClazzDeserializer());
                                Clazz c = gsonBuilder.create().fromJson(unparsedClazzes.get(i).toString(), Clazz.class);
                                clazzes.add(c);
                            }

                            // I do not like setting these every time an INSERT INTO completes
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mProgressBar.setIndeterminate(false);
                                    mProgressBar.setMax(10000);
                                }
                            });
                            // Commit
                            DataStore ds = new DataStore(getApplicationContext(), p.getCardid(), Common.SQLCRYPT_PWD);
                            ds.putAllDayEventsData(allDayEvents);
                            ds.putClassesData(clazzes, new DataStore.InsertProcessCallback() {
                                @Override
                                public void onInsertComplete(final int current, final int total) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            float percentage = (float) current / total * 100;
                                            mProgressBar.setProgress(Math.round(percentage * 100));
                                            mProgressStatusView.setText(getString(R.string.filling_schedule, percentage));
                                        }
                                    });
                                }
                            });
                            ds.close();

                            AccountStore ash = new AccountStore(getApplicationContext(), Common.SQLCRYPT_PWD);
                            ash.addAccount(p);
                            ash.close();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setResult(RESULT_OK, getIntent().putExtra("profileId", p.getCardid()));
                                    finish();
                                    if (getIntent().getBooleanExtra("doOpenMainActivity", false)) {
                                        // Birthday collection
                                        String[] exploded = p.getPasswd().split("-");
                                        if (exploded.length == 3) {
                                            Calendar c = Calendar.getInstance();
                                            c.set(Calendar.HOUR_OF_DAY, 9);
                                            c.set(Calendar.MINUTE, 0);
                                            c.set(Calendar.SECOND, 0);
                                            c.set(Calendar.MONTH, Integer.valueOf(exploded[1])-1);
                                            c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(exploded[2]));
                                            if (c.getTimeInMillis() < Calendar.getInstance().getTimeInMillis())
                                                c.add(Calendar.YEAR, 1);

                                            getSharedPreferences("main", MODE_PRIVATE).edit().putLong("birthday", c.getTimeInMillis()).apply();
                                            Common.registerBirthdayReminder(NewProfileActivity.this, c);
                                        }

                                        Intent mainIntent = new Intent(NewProfileActivity.this, MainActivity.class);
                                        startActivity(mainIntent);
                                    }
                                }
                            });
                        } catch (DecryptionException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showProgress(false);
                                }
                            });
                            showOnSnackbar(R.string.decrypt_database_fail, Snackbar.LENGTH_LONG);
                        } catch (SQLiteConstraintException e) {
                            // TODO: Check this beforehand
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showProgress(false);
                                }
                            });
                            showOnSnackbar(R.string.profile_exists, Snackbar.LENGTH_LONG);
                        }
                    }

                    @Override
                    public void onFailure(int localizedError) {
                        showOnSnackbar(localizedError, Snackbar.LENGTH_LONG);
                    }
                });
            }

            @Override
            public void onFailure(final int localizedError) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showProgress(false);
                    }
                });
                showOnSnackbar(localizedError, Snackbar.LENGTH_LONG);
            }
        });
    }

    /**
     * Shows a message on the Snackbar.
     * @param message the message to show
     * @param length Snackbar.LENGTH_*
     */
    private void showOnSnackbar(@StringRes final int message, @SuppressWarnings("SameParameterValue") final int length) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar.make(findViewById(R.id.login_coord_view), message, length).show();
            }
        });
    }


    /**
     * Shows the progress UI and hides the login form (or, if you want, the other way around)
     * @param show whether to show the progress or not
     */
    private void showProgress(boolean show) {
        doShowMenu = !show;
        invalidateOptionsMenu();
        mLoginFlipperView.setDisplayedChild(show ? 1 : 0);
    }
}

