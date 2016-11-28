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

package com.silvered.hadesota.tasks;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.silvered.hadesota.MainActivity;
import com.silvered.hadesota.R;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadTask extends AsyncTask<String, Integer, String> {


    private Context context;
    private PowerManager.WakeLock mWakeLock;

    private ProgressDialog mProgressDialog;
    private NotificationManager mNotifyManager;
    private Notification.Builder mBuilder;
    private String version;
    private boolean reboot;
    private boolean backup;
    private boolean wipe;
    int id = 1;

    public DownloadTask(Context context) {
        this.context = context;
    }

    @Override
    protected String doInBackground(String... params) {
        version = params[1];
        if (!isConnectivityAvailable(context)) {
            return null;
        }

        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(params[0]);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                mBuilder.setContentText("Download Error");
                // Removes the progress bar
                mBuilder.setProgress(0, 0, false);
                Intent intent = new Intent(context, MainActivity.class);
                intent.setAction(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT);
                mBuilder.setContentIntent(pendingIntent);
                mBuilder.setAutoCancel(false);
                mNotifyManager.notify(id, mBuilder.build());
                return "Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage();
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            File f = new File(Environment.getExternalStorageDirectory()+"/HadesRom/tmp/");
            f.mkdirs();
            output = new FileOutputStream(Environment.getExternalStorageDirectory()+"/HadesRom/tmp/update.zip");

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            int currentProgress=0;
            int previousProgress=0;
            while ((count = input.read(data)) != -1) {
                // allow canceling with back button
                if (isCancelled()) {
                    mNotifyManager.cancel(id);
                    input.close();
                    return null;
                }
                total += count;
                currentProgress = (int) (total * 100 / fileLength);
                // Publish only on increments of 1%
                if (currentProgress >= previousProgress + 1) {
                    this.publishProgress(currentProgress);
                    previousProgress = currentProgress;
                }
                // publishing the progress....
               /* if ((count % 10) == 0) {
                    publishProgress((int) (total * 100 / fileLength));
                }*/
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            return e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // take CPU lock to prevent CPU from going off if the user
        // presses the power button during download
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getClass().getName());
        mWakeLock.acquire();
        // instantiate it within the onCreate method
        mProgressDialog = new ProgressDialog(context);
        mProgressDialog.setMessage(context.getString(R.string.notification_download_title));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DownloadTask.this.cancel(true);
                dialog.dismiss();
            }
        });
        mProgressDialog.show();
        showNotification(context);
    }

    @Override
    protected void onPostExecute(String result) {

        mWakeLock.release();
        mProgressDialog.dismiss();
        mBuilder.setContentText("Download complete");
        // Removes the progress bar
        mBuilder.setProgress(0, 0, false);
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setAutoCancel(false);
        mNotifyManager.notify(id, mBuilder.build());

        final StringBuilder script = new StringBuilder();
        final String filePath_ = Environment.getExternalStorageDirectory()+"/HadesRom/tmp/update.zip";
        final String LINE_RETURN = "\n";

        final String SCRIPT_PATH = "/cache/recovery/openrecoveryscript";
        if (result != null)
            Toast.makeText(context,"Download error: "+result, Toast.LENGTH_LONG).show();
        else {
            Toast.makeText(context, "File downloaded", Toast.LENGTH_SHORT).show();
            Log.d("TEST",version);

            if(checkRootMethod()) {
                AlertDialog.Builder builder_reboot = new AlertDialog.Builder(context);
                builder_reboot.setTitle(context.getString(R.string.dialog_reboot_title));
                builder_reboot.setMessage(context.getString(R.string.dialog_reboot_message));
                builder_reboot.setPositiveButton(context.getString(R.string.reboot), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        AlertDialog.Builder builder_backup = new AlertDialog.Builder(context);
                        builder_backup.setTitle(context.getString(R.string.dialog_backup_title));
                        builder_backup.setMessage(context.getString(R.string.dialog_backup_message));
                        builder_backup.setPositiveButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                AlertDialog.Builder builder_wipe = new AlertDialog.Builder(context);
                                builder_wipe.setTitle(context.getString(R.string.dialog_wipe_title));
                                builder_wipe.setMessage(context.getString(R.string.dialog_wipe_message));
                                builder_wipe.setPositiveButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        String[] commands = {"su", "-c", "echo backup SDB " + version + " > /cache/recovery/openrecoveryscript"};
                                        String[] commands2 = {"su", "-c", "echo wipe data >> /cache/recovery/openrecoveryscript"};
                                        String[] commands6 = {"su", "-c", "echo wipe dalvik >> /cache/recovery/openrecoveryscript"};
                                        String[] commands3 = {"su", "-c", "echo wipe cache >> /cache/recovery/openrecoveryscript"};
                                        String[] commands4 = {"su", "-c", "echo install " + Environment.getExternalStorageDirectory() + "/HadesRom/tmp/update.zip" + " >> /cache/recovery/openrecoveryscript"};
                                        String[] commands5 = {"su", "-c", "reboot recovery"};

                                        try {
                                            Process p = Runtime.getRuntime().exec(commands);

                                            Process p2 = Runtime.getRuntime().exec(commands2);
                                            Process p6 = Runtime.getRuntime().exec(commands6);
                                            Process p3 = Runtime.getRuntime().exec(commands3);
                                            Process p4 = Runtime.getRuntime().exec(commands4);
                                            Process p5 = Runtime.getRuntime().exec(commands5);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                                builder_wipe.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        try {
                                            String[] commands = {"su", "-c", "echo backup SDB" + version + " >> /cache/recovery/openrecoveryscript"};
                                            String[] commands2 = {"su", "-c", "echo wipe dalvik >> /cache/recovery/openrecoveryscript"};
                                            String[] commands3 = {"su", "-c", "echo wipe cache >> /cache/recovery/openrecoveryscript"};
                                            String[] commands4 = {"su", "-c", "echo install " + Environment.getExternalStorageDirectory() + "/HadesRom/tmp/update.zip" + " >> /cache/recovery/openrecoveryscript"};
                                            String[] commands5 = {"su", "-c", "reboot recovery"};

                                            Process p = Runtime.getRuntime().exec(commands);
                                            Process p2 = Runtime.getRuntime().exec(commands2);
                                            Process p3 = Runtime.getRuntime().exec(commands3);
                                            Process p4 = Runtime.getRuntime().exec(commands4);
                                            Process p5 = Runtime.getRuntime().exec(commands5);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });

                                AlertDialog dialog_wipe = builder_wipe.create();
                                dialog_wipe.show();

                            }
                        });
                        builder_backup.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                AlertDialog.Builder builder_wipe = new AlertDialog.Builder(context);
                                builder_wipe.setTitle(context.getString(R.string.dialog_wipe_title));
                                builder_wipe.setMessage(context.getString(R.string.dialog_wipe_message));
                                builder_wipe.setPositiveButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        try {
                                            String[] commands1 = {"su", "-c", "echo wipe data > /cache/recovery/openrecoveryscript"};
                                            String[] commands2 = {"su", "-c", "echo wipe dalvik >> /cache/recovery/openrecoveryscript"};
                                            String[] commands3 = {"su", "-c", "echo wipe cache >> /cache/recovery/openrecoveryscript"};
                                            String[] commands4 = {"su", "-c", "echo install " + Environment.getExternalStorageDirectory() + "/HadesRom/tmp/update.zip" + " >> /cache/recovery/openrecoveryscript"};
                                            String[] commands5 = {"su", "-c", "reboot recovery"};

                                            Process p1 = Runtime.getRuntime().exec(commands1);
                                            Process p2 = Runtime.getRuntime().exec(commands2);
                                            Process p3 = Runtime.getRuntime().exec(commands3);
                                            Process p4 = Runtime.getRuntime().exec(commands4);
                                            Process p5 = Runtime.getRuntime().exec(commands5);

                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                });
                                builder_wipe.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        try {
                                            String[] commands2 = {"su", "-c", "echo wipe dalvik > /cache/recovery/openrecoveryscript"};
                                            String[] commands3 = {"su", "-c", "echo wipe cache >> /cache/recovery/openrecoveryscript"};
                                            String[] commands4 = {"su", "-c", "echo install " + Environment.getExternalStorageDirectory() + "/HadesRom/tmp/update.zip" + " >> /cache/recovery/openrecoveryscript"};
                                            String[] commands5 = {"su", "-c", "reboot recovery"};

                                            Process p2 = Runtime.getRuntime().exec(commands2);
                                            Process p3 = Runtime.getRuntime().exec(commands3);
                                            Process p4 = Runtime.getRuntime().exec(commands4);
                                            Process p5 = Runtime.getRuntime().exec(commands5);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });

                                AlertDialog dialog_wipe = builder_wipe.create();
                                dialog_wipe.show();
                            }
                        });

                        AlertDialog dialog_backup = builder_backup.create();
                        dialog_backup.show();
                    }
                });
                builder_reboot.setNegativeButton(context.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mNotifyManager.cancel(id);
                        AlertDialog.Builder builder_info = new AlertDialog.Builder(context);
                        builder_info.setTitle(context.getString(R.string.dialog_info_title));
                        builder_info.setMessage(context.getString(R.string.dialog_info_message, Environment.getExternalStorageDirectory() + "/HadesRom/tmp/update.zip"));
                        builder_info.setPositiveButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });

                        AlertDialog dialog_info = builder_info.create();
                        dialog_info.show();
                    }
                });
                AlertDialog dialog_reboot = builder_reboot.create();
                dialog_reboot.show();

            }else{
                mNotifyManager.cancel(id);
                AlertDialog.Builder builder_info = new AlertDialog.Builder(context);
                builder_info.setTitle(context.getString(R.string.dialog_info_title));
                builder_info.setMessage(context.getString(R.string.dialog_info_message, Environment.getExternalStorageDirectory() + "/HadesRom/tmp/update.zip"));
                builder_info.setPositiveButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });

                AlertDialog dialog_info = builder_info.create();
                dialog_info.show();
            }
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        mBuilder.setProgress(100, progress[0], false)
                .setContentText(context.getString(R.string.notification_download_message,progress[0]).concat("%"));
        mBuilder.setAutoCancel(false);

        mNotifyManager.notify(id, mBuilder.build());
        super.onProgressUpdate(progress);
        // if we get here, length is known, now set indeterminate to false
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setMax(100);
        mProgressDialog.setProgress(progress[0]);



    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
      //  mInstance = null;
    }

    private static boolean checkRootMethod() {
        Process p;
        try {
            // Preform su to get root privledges
            p = Runtime.getRuntime().exec("su");

            // Attempt to write a file to a root-only
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("echo \"Do I have root?\" >/system/sd/temporary.txt\n");

            // Close the terminal
            os.writeBytes("exit\n");
            os.flush();
            try {
                p.waitFor();
                if (p.exitValue() != 255) {
                    // TODO Code to run on success
                    return true;
                }
                else {
                    // TODO Code to run on unsuccessful
                    return false;
                }
            } catch (InterruptedException e) {
                // TODO Code to run in interrupted exception
                return false;
            }
        } catch (IOException e) {
            // TODO Code to run in input/output exception
            return false;
        }
    }

    private static boolean isConnectivityAvailable(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    private void showNotification(Context context) {

        mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new Notification.Builder(context);
        mBuilder.setContentTitle(context.getString(R.string.notification_download_title))
                .setContentText(context.getString(R.string.notification_download_message,0))
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.notification_hades_large))
                .setSmallIcon(R.drawable.notification_hades);

        mBuilder.setAutoCancel(false);



        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);
        mBuilder.setContentIntent(pendingIntent);

        mNotifyManager.notify(id, mBuilder.build());
        mNotifyManager.cancel(1000001);
    }
}
