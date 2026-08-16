package com.crdroid.keyboxupdater;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class KeyboxPushService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Notificacion Push de GitHub recibida: Lanzar sincronizacion de inmediato (OneTimeWorkRequest)
        OneTimeWorkRequest immediateSyncRequest = new OneTimeWorkRequest.Builder(KeyboxSyncWorker.class).build();
        WorkManager.getInstance(getApplicationContext()).enqueue(immediateSyncRequest);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        SharedPreferences prefs = getSharedPreferences("crDroidKeyboxPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("fcm_token", token).apply();
    }
}
