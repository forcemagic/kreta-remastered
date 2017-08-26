package com.speedyblur.kretaremastered.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.speedyblur.kretaremastered.R;
import com.speedyblur.kretaremastered.fragments.WelcomeSlideEndFragment;
import com.speedyblur.kretaremastered.fragments.WelcomeSlideFirstFragment;
import com.speedyblur.kretaremastered.fragments.WelcomeSlideSetsqlpassFragment;
import com.speedyblur.kretaremastered.shared.DecryptionException;
import com.speedyblur.kretaremastered.views.CustomViewPager;
import com.speedyblur.kretaremastered.shared.AccountStore;
import com.speedyblur.kretaremastered.shared.Vars;

public class WelcomeActivity extends AppCompatActivity {

    private CustomViewPager mPager;
    private SharedPreferences shPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shPrefs = getSharedPreferences("main", MODE_PRIVATE);
        if (shPrefs.getBoolean("isFirstStart", true)) {
            setContentView(R.layout.activity_welcome);

            mPager = (CustomViewPager) findViewById(R.id.welcomePager);
            mPager.setPagingEnabled(false);

            PagerAdapter mPgAdapter = new WelcomeSlidePageAdapter(getSupportFragmentManager());
            mPager.setAdapter(mPgAdapter);
        } else {
            if (!canDecryptSqlite(Vars.SQLCRYPT_PWD)) {
                setContentView(R.layout.activity_unlockdb);

                // Set up "Enter" key action
                EditText mPasswdView = (EditText) findViewById(R.id.unlockDbPassword);
                mPasswdView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        if (i == R.id.unlockDb || i == EditorInfo.IME_NULL) {
                            tryDecryptSqlite(null);
                            return true;
                        }
                        return false;
                    }
                });
            } else {
                // We can use the default password, as nothing else has been provided previously
                finish();
                Intent it = new Intent(WelcomeActivity.this, ProfileListActivity.class);
                startActivity(it);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            mPager.setCurrentItem(mPager.getCurrentItem() - 1);
        }
    }

    public void commitSqlPassword(View v) {
        EditText mSqlPass = (EditText) findViewById(R.id.sqlPass);
        String gotPasswd = mSqlPass.getText().toString().trim();
        if (!TextUtils.isEmpty(gotPasswd)) {
            Vars.SQLCRYPT_PWD = gotPasswd;
            Log.d("Setup", "Password set.");
        }
        goToNext(v);
    }

    public void goToNext(View v) {
        if (mPager.getCurrentItem() < 2) {
            mPager.setCurrentItem(mPager.getCurrentItem() + 1, true);
        } else {
            shPrefs.edit().putBoolean("isFirstStart", false).apply();
            finish();
            Intent it = new Intent(WelcomeActivity.this, ProfileListActivity.class);
            startActivity(it);
        }
    }

    public void tryDecryptSqlite(View v) {
        EditText mDbPasswd = (EditText) findViewById(R.id.unlockDbPassword);
        if (canDecryptSqlite(mDbPasswd.getText().toString())) {
            Vars.SQLCRYPT_PWD = mDbPasswd.getText().toString();
            finish();
            Intent it = new Intent(WelcomeActivity.this, ProfileListActivity.class);
            startActivity(it);
        } else {
            mDbPasswd.setError(getResources().getString(R.string.decrypt_database_fail));
        }
    }

    private boolean canDecryptSqlite(String passwd) {
        try {
            AccountStore ash = new AccountStore(this, passwd);
            ash.close();
            Log.d("DecryptDB", "Decryption succeeded.");
            return true;
        } catch (DecryptionException e) {
            Log.w("DecryptDB", "Decryption failed.");
            return false;
        }
    }

    private class WelcomeSlidePageAdapter extends FragmentStatePagerAdapter {
        public WelcomeSlidePageAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int pos) {
            if (pos == 0) {
                return new WelcomeSlideFirstFragment();
            } else if (pos == 1) {
                return new WelcomeSlideSetsqlpassFragment();
            } else {
                return new WelcomeSlideEndFragment();
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}
