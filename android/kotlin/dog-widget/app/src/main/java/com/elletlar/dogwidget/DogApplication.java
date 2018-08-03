package com.elletlar.dogwidget;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.elletlar.dogwidget.service.DogService;

import java.util.Arrays;
import java.util.List;

/**
 * Dog Application
 */
public class DogApplication extends Application {

    private SharedPreferences mPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        updateBreeds();
    }

    /**
     * Ensure the service periodically updates the breed information for the dogs
     */
    private void updateBreeds() {
        Intent serviceIntent = new Intent(getApplicationContext(), DogService.class);
        serviceIntent.setAction(DogService.ACTION_UPDATE_BREEDS);
        getApplicationContext().startService(serviceIntent);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!mPrefs.getBoolean(Config.Keys.SETUP, false)) {
            setup();
        }
    }

    private void setup() {
        mPrefs.edit().putBoolean(Config.Keys.SETUP, true).apply();

        // Some work to translate form Kotlin string array to set
        List<String> ls = Arrays.asList(Config.Urls.getDefaultImages());
        Config.Keys.putList(getApplicationContext(), Config.Keys.CURRENT_IMAGE_URLS, ls);
    }
}