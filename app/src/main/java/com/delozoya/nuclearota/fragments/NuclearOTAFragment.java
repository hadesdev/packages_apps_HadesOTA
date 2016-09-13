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

package com.delozoya.nuclearota.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import com.delozoya.nuclearota.R;
import com.delozoya.nuclearota.configs.AppConfig;
import com.delozoya.nuclearota.configs.LinkConfig;
import com.delozoya.nuclearota.configs.OTAVersion;
import com.delozoya.nuclearota.dialogs.WaitDialogFragment;
import com.delozoya.nuclearota.tasks.CheckUpdateTask;
import com.delozoya.nuclearota.tasks.DownloadTask;
import com.delozoya.nuclearota.utils.OTAUtils;
import com.delozoya.nuclearota.xml.OTALink;

import java.util.ArrayList;
import java.util.List;

public class NuclearOTAFragment extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener ,
        WaitDialogFragment.OTADialogListener,
        LinkConfig.LinkConfigListener {

    private static final String KEY_ROM_INFO = "key_rom_info";
    private static final String KEY_CHECK_UPDATE = "key_check_update";
    private static final String KEY_UPDATE_INTERVAL = "key_update_interval";
    private static final String CATEGORY_LINKS = "category_links";
    private static final String KEY_LINK_ROM = "rom";

    private PreferenceScreen mRomInfo;
    private PreferenceScreen mCheckUpdate;
    private ListPreference mUpdateInterval;
    private PreferenceCategory mLinksCategory;

    private CheckUpdateTask mTask;

    private ArrayList<String> ids = new ArrayList<>();

    private Boolean link_rom =false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.nuclearota);

        mRomInfo = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_ROM_INFO);
        mCheckUpdate = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_CHECK_UPDATE);

        mUpdateInterval = (ListPreference) getPreferenceScreen().findPreference(KEY_UPDATE_INTERVAL);
        if (mUpdateInterval != null) {
            mUpdateInterval.setOnPreferenceChangeListener(this);
        }

        mLinksCategory = (PreferenceCategory) getPreferenceScreen().findPreference(CATEGORY_LINKS);
    }

    private void updatePreferences() {
        updateRomInfo();
        updateLastCheckSummary();
        updateIntervalSummary();
        updateLinks(false);
    }

    private void updateLinks(boolean force) {
        ids.clear();
        List<OTALink> links = LinkConfig.getInstance().getLinks(getActivity(), force);
        for (OTALink link : links) {
            String id = link.getId();
            Log.d("test",id);
            PreferenceScreen linkPref = (PreferenceScreen) getPreferenceScreen().findPreference(id);
            if (linkPref == null && mLinksCategory != null) {
                linkPref = getPreferenceManager().createPreferenceScreen(getActivity());
                linkPref.setKey(id);
                mLinksCategory.addPreference(linkPref);
            }
            if (linkPref != null) {
                String title = link.getTitle();
                linkPref.setTitle(title.isEmpty() ? id : title);
                linkPref.setSummary(link.getDescription());
            }
        }
    }

    private void updateRomInfo() {
        if (mRomInfo != null) {
            String fullLocalVersion = OTAVersion.getFullLocalVersion(getActivity());
            String shortLocalVersion = OTAVersion.extractVersionFrom(fullLocalVersion, getActivity());
            mRomInfo.setTitle(fullLocalVersion);

            String prefix = getActivity().getResources().getString(R.string.latest_version);
            String fullLatestVersion = AppConfig.getFullLatestVersion(getActivity());
            String shortLatestVersion = OTAVersion.extractVersionFrom(fullLatestVersion, getActivity());
            if (fullLatestVersion.isEmpty()) {
                fullLatestVersion = getActivity().getResources().getString(R.string.unknown);
                mRomInfo.setSummary(String.format(prefix, fullLatestVersion));
            } else if (!OTAVersion.compareVersion(shortLatestVersion, shortLocalVersion, getActivity())) {
                mRomInfo.setSummary(getActivity().getResources().getString(R.string.system_uptodate));
            } else {
                mRomInfo.setSummary(String.format(prefix, fullLatestVersion));
            }
        }
    }

    private void updateLastCheckSummary() {
        if (mCheckUpdate != null) {
            mCheckUpdate.setSummary(AppConfig.getLastCheck(getActivity()));
        }
    }

    private void updateIntervalSummary() {
        if (mUpdateInterval != null) {
            mUpdateInterval.setValueIndex(AppConfig.getUpdateIntervalIndex(getActivity()));
            mUpdateInterval.setSummary(mUpdateInterval.getEntry());
        }
    }

    @Override
     public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updatePreferences();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onProgressCancelled() {
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
    }

    @Override
    public void onConfigChange() {
        updateLinks(true);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        final String key = preference.getKey();
        switch (key) {
            case KEY_CHECK_UPDATE:
                link_rom=false;
                mTask = CheckUpdateTask.getInstance(false);
                if (!mTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                    mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getActivity());
                }
                return true;
            case KEY_UPDATE_INTERVAL:
                return true;
            case KEY_LINK_ROM:
                final String link = LinkConfig.getInstance().findLinkFile();
                String version = OTAVersion.getLocalVersion(getActivity());
                if (link != null) {
                    final DownloadTask downloadTask = new DownloadTask(getActivity());
                    downloadTask.execute(link,version);
                }else{
                    Toast.makeText(getActivity(),getActivity().getString(R.string.check_update),Toast.LENGTH_LONG).show();
                }
                break;
            default:
                OTALink link1 = LinkConfig.getInstance().findLink(key, getActivity());
                if (link1 != null) {
                    OTAUtils.launchUrl(link1.getUrl(), getActivity());
                }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference == mUpdateInterval) {
            AppConfig.persistUpdateIntervalIndex(Integer.valueOf((String) value), getActivity());
            return true;
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(AppConfig.getLatestVersionKey())) {
            updateRomInfo();
        }
        if (key.equals(AppConfig.getLastCheckKey())) {
            updateLastCheckSummary();
        }
        if (key.equals(AppConfig.getUpdateIntervalKey())) {
            updateIntervalSummary();
        }
    }
}
