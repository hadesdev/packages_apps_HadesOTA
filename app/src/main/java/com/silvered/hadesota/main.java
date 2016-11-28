/*
 * Copyright (C) 2015 Chandra Poerwanto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silvered.hadesota;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.silvered.hadesota.configs.LinkConfig;
import com.silvered.hadesota.dialogs.WaitDialogFragment;
import com.silvered.hadesota.fragments.HadesOTAFragment;

public class main extends PreferenceActivity implements
        WaitDialogFragment.OTADialogListener, LinkConfig.LinkConfigListener {

    private static final String FRAGMENT_TAG = HadesOTAFragment.class.getName();
    private HadesOTAFragment mFragment;
    Boolean showLauncherShortcut = true;
    Boolean hideLaucherIcon = false;
    String settingsPackageName= "com.android.settings";
    String settingsHadesDrawableName = "ic_hades_ota_exposed";

    Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragment = (HadesOTAFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (mFragment == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new HadesOTAFragment(), FRAGMENT_TAG)
                    .commit();
        }

        getActionBar().setDisplayHomeAsUpEnabled(true);


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
            case R.id.hide:
                if (item.isChecked()){
                    item.setChecked(false);
                    unhideIcon();
                }else{
                    item.setChecked(true);
                    hideIcon();
                }
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        // Inflate the main; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if(mShowLauncherShortcut()) {
            menu.clear();
        }else{
            SharedPreferences hide_icon = getSharedPreferences("hide_icon", Context.MODE_PRIVATE);

            hideLaucherIcon = hide_icon.getBoolean("hide_icon", false);
            Log.d("hide", hideLaucherIcon + "");

            if (hideLaucherIcon) {
                Log.d("icon", hideLaucherIcon + "");
                menu.getItem(0).setChecked(true);
            } else {
                menu.getItem(0).setChecked(false);
            }
        }
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("hide_launcher", hideLaucherIcon);
    }

    public boolean mShowLauncherShortcut() {

        try {
            Resources res = getApplicationContext().getPackageManager().getResourcesForApplication(settingsPackageName);
            int drawableid = res.getIdentifier(settingsPackageName+":drawable/"+settingsHadesDrawableName, "drawable", settingsPackageName);
            if ( drawableid != 0 ) {
                showLauncherShortcut = false;
                Log.d("LayersManager", "checked settings for icon, true");
            } else {
                Log.d("LayersManager", "checked settings for icon, false");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            Log.e("LayersManager", "System Settings apk not found!");
        }

        return showLauncherShortcut;
    }

    @Override
    public void onProgressCancelled() {
        Fragment fragment = getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment instanceof WaitDialogFragment.OTADialogListener) {
            ((WaitDialogFragment.OTADialogListener) fragment).onProgressCancelled();
        }
    }

    @Override
    public void onConfigChange() {
        Fragment fragment = getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment instanceof LinkConfig.LinkConfigListener) {
            ((LinkConfig.LinkConfigListener) fragment).onConfigChange();
        }
    }

    private void hideIcon(){

        hideLaucherIcon = true;
        SharedPreferences.Editor prefEditor = getSharedPreferences("hide_icon", 0).edit();
        prefEditor.putBoolean("hide_icon", hideLaucherIcon);
        prefEditor.apply();
        PackageManager p = getPackageManager();
        ComponentName componentName = new ComponentName(this, com.silvered.hadesota.MainActivity.class); // activity which is first time open in manifiest file which is declare as <category android:name="android.intent.category.LAUNCHER" />
        p.setComponentEnabledSetting(componentName,PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    private void unhideIcon(){
        hideLaucherIcon = false;
        SharedPreferences.Editor prefEditor = getSharedPreferences("hide_icon", 0).edit();
        prefEditor.putBoolean("hide_icon", hideLaucherIcon);
        prefEditor.apply();
        PackageManager p = getPackageManager();
        ComponentName componentName = new ComponentName(this, com.silvered.hadesota.MainActivity.class);
        p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }
}
