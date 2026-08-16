package com.crdroid.keyboxupdater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class KeyboxSyncWorker extends Worker {

    private static final String GITHUB_URL = "https://raw.githubusercontent.com/Wuang26/Kaorios-Toolbox/main/Toolbox-data/Keybox.xml";
    private final Context context;

    public KeyboxSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = context.getSharedPreferences("crDroidKeyboxPrefs", Context.MODE_PRIVATE);
        boolean isAutoSyncEnabled = prefs.getBoolean("autoSyncEnabled", true);

        if (!isAutoSyncEnabled) {
            return Result.success();
        }

        String storedHash = prefs.getString("lastKnownHash", "");

        try {
            URL url = new URL(GITHUB_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                String remoteXml = sb.toString();
                if (remoteXml.contains("Keybox")) {
                    String remoteHash = computeHash(remoteXml);

                    // Bugfix: Evitar actualizar todo el tiempo si la clave es identica a la ya instalada
                    if (remoteHash.equalsIgnoreCase(storedHash)) {
                        return Result.success();
                    }

                    // Intentar escribir via Root
                    File tempFile = new File(context.getExternalFilesDir(null), "keybox_temp.xml");
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    fos.write(remoteXml.getBytes());
                    fos.close();

                    boolean success = applyKeyboxRoot(tempFile.getAbsolutePath(), remoteXml);
                    if (success) {
                        prefs.edit().putString("lastKnownHash", remoteHash).apply();
                        sendUpdateNotification();
                        return Result.success();
                    } else {
                        return Result.retry();
                    }
                }
            }
            return Result.retry();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void sendUpdateNotification() {
        SharedPreferences prefs = context.getSharedPreferences("crDroidKeyboxPrefs", Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("notificationsEnabled", true);
        if (!notificationsEnabled) return;

        try {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "keybox_updates_channel";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Notificaciones de Keybox",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Notifica cuando Keybox.xml ha sido actualizado exitosamente desde GitHub");
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_pass_check)
                .setContentTitle("🔔 Keybox Actualizado")
                .setContentText("Keybox.xml actualizado desde GitHub")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Keybox.xml ha sido actualizado e instalado exitosamente desde GitHub. Tu dispositivo esta protegido."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

            if (notificationManager != null) {
                notificationManager.notify(777, builder.build());
            }
        } catch (Exception ignored) {}
    }

    private boolean applyKeyboxRoot(String tempFilePath, String xmlContent) {
        try {
            Process suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

            os.writeBytes("mkdir -p /data/adb/trickystore /data/system/trickystore /sdcard/Kaorios /data/adb/kaorios\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /data/adb/trickystore/keybox.xml\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /data/system/trickystore/keybox.xml\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /sdcard/Kaorios/Keybox.xml\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /data/adb/kaorios/keybox.xml\n");
            os.writeBytes("chmod 0644 /data/adb/trickystore/keybox.xml /data/system/trickystore/keybox.xml /sdcard/Kaorios/Keybox.xml /data/adb/kaorios/keybox.xml\n");
            os.writeBytes("settings put secure spoof_trickystore_keybox \"" + xmlContent.replace("\"", "\\\"") + "\"\n");
            os.writeBytes("exit\n");
            os.flush();

            return suProcess.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String computeHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
