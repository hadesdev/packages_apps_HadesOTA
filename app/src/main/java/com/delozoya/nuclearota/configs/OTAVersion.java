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

package com.delozoya.nuclearota.configs;

import android.content.Context;
import android.util.Log;

import com.delozoya.nuclearota.utils.OTAUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class OTAVersion {

    private static final String UNAME_R = "uname -r";

    public static String version_server_android = "";
    public static String version_local_android = "";

    public static String getFullLocalVersion(Context context) {
        String source = OTAConfig.getInstance(context).getVersionSource();
        String sourceString = "";
        if (source.equalsIgnoreCase(UNAME_R)) {
            sourceString = OTAUtils.runCommand(UNAME_R);
        } else {
            sourceString = OTAUtils.getBuildProp(source);
        }
        return sourceString;
    }

    public static String getLocalVersion(Context context){
        String localVersion = getFullLocalVersion(context);

        return extractVersionFrom(localVersion, context);

    }

    public static boolean checkServerVersion(String serverVersion, Context context) {
        String localVersion = getFullLocalVersion(context);
        version_local_android =  extractVersionAndroidFrom(localVersion, context);
        version_server_android = extractVersionAndroidFrom(serverVersion, context);

        localVersion = extractVersionFrom(localVersion, context);
        serverVersion = extractVersionFrom(serverVersion, context);

        OTAUtils.logInfo("serverVersion: " + serverVersion);
        OTAUtils.logInfo("localVersion: " + localVersion);

        if(versionCompare(version_local_android,version_server_android)<0){
            return compareVersion(serverVersion, localVersion, context);

        }else if(versionCompare(version_local_android,version_server_android)==0){
            return compareVersion(serverVersion, localVersion, context);

        } else{
            return false;

        }
    }

    /**
     * Compares two version strings.
     *
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     *         The result is a positive integer if str1 is _numerically_ greater than str2.
     *         The result is zero if the strings are _numerically_ equal.
     */
    public static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

    public static boolean compareVersion(String serverVersion, String localVersion, Context context) {
        boolean versionIsNew = false;

        Log.d("version_android",version_local_android);
        Log.d("version_android",version_server_android);

        if (serverVersion.isEmpty() || localVersion.isEmpty()) {
            return false;
        }

        final SimpleDateFormat format = OTAConfig.getInstance(context).getFormat();
        if (format == null) {
            try {
                int serverNumber = Integer.parseInt(serverVersion.replaceAll("[\\D]", ""));
                Log.d("server_Number",serverNumber+"");
                int currentNumber = Integer.parseInt(localVersion.replaceAll("[\\D]", ""));
                Log.d("currentNumber",currentNumber+"");
                versionIsNew = serverNumber > currentNumber;
            } catch (NumberFormatException e) {
                OTAUtils.logError(e);
            }
        } else {
            try {
                Date serverDate = format.parse(serverVersion);
                Date currentDate = format.parse(localVersion);
                versionIsNew = serverDate.after(currentDate);
            } catch (ParseException e) {
                OTAUtils.logError(e);
            }
        }


        return versionIsNew;

    }

    public static String extractVersionFrom(String str, Context context) {
        String version = "";

        if (!str.isEmpty()) {
            String delimiter = OTAConfig.getInstance(context).getDelimiter();
            int position = OTAConfig.getInstance(context).getPosition();

            if (delimiter.isEmpty()) {
                version = str;
            } else {
                if (delimiter.equals(".")) {
                    delimiter = Pattern.quote(".");
                }
                String[] tokens = str.split(delimiter);
                if (position > -1 && position < tokens.length) {
                    version = tokens[position];
                }
            }
        }

        return version;
    }

    public static String extractVersionAndroidFrom(String str, Context context) {
        String version = "";

        if (!str.isEmpty()) {
            String delimiter = OTAConfig.getInstance(context).getDelimiter();
            int position = 2;

            if (delimiter.isEmpty()) {
                version = str;
            } else {
                if (delimiter.equals(".")) {
                    delimiter = Pattern.quote(".");
                }
                String[] tokens = str.split(delimiter);
                if (position > -1 && position < tokens.length) {
                    version = tokens[position];
                    //Log.d("test_version", version.substring(version.indexOf("[")+1,version.indexOf("]")));
                }
            }
        }

        return version.substring(version.indexOf("[")+1,version.indexOf("]"));
    }
}
