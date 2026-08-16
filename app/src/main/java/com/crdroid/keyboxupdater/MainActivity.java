package com.crdroid.keyboxupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvSubHeader;
    private TextView btnClearLog;
    private Button btnCheckIntegrity;
    private Button btnUpdateKeybox;
    private Button btnUpdateTargets;
    private Button btnDeleteKeybox;
    private Button btnOpenSettings;
    private Switch switchAutoSync;

    private Handler mainHandler;
    private ExecutorService executorService;
    private SharedPreferences prefs;

    private boolean isRootGranted = false;
    private boolean isAutoSyncEnabled = true;
    private String lastKnownHash = "";
    private int syncIntervalHours = 3;

    private static final String GITHUB_URL = "https://raw.githubusercontent.com/Wuang26/Kaorios-Toolbox/main/Toolbox-data/Keybox.xml";
    private static final String DEFAULT_TARGET_APPS = 
        "# always use leaf hack mode\n" +
        "com.google.android.apps.walletnfcrel\n" +
        "com.google.android.googlequicksearchbox\n" +
        "com.google.ar.core\n" +
        "com.android.vending\n" +
        "com.google.android.gms\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        tvSubHeader = findViewById(R.id.tvSubHeader);
        btnClearLog = findViewById(R.id.btnClearLog);
        btnCheckIntegrity = findViewById(R.id.btnCheckIntegrity);
        btnUpdateKeybox = findViewById(R.id.btnUpdateKeybox);
        btnUpdateTargets = findViewById(R.id.btnUpdateTargets);
        btnDeleteKeybox = findViewById(R.id.btnDeleteKeybox);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        switchAutoSync = findViewById(R.id.switchAutoSync);

        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences("crDroidKeyboxPrefs", MODE_PRIVATE);

        loadPreferences();

        btnCheckIntegrity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkRootOrAlert()) {
                    runPlayIntegrityTest();
                }
            }
        });

        btnUpdateKeybox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkRootOrAlert()) {
                    startKeyboxUpdate();
                }
            }
        });

        btnUpdateTargets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkRootOrAlert()) {
                    startTargetAppsUpdate();
                }
            }
        });

        btnDeleteKeybox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkRootOrAlert()) {
                    startKeyboxDeletion();
                }
            }
        });

        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvLog.setText("[INFO] Registro de eventos limpiado.");
            }
        });

        btnOpenSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        switchAutoSync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAutoSyncEnabled = isChecked;
                prefs.edit().putBoolean("autoSyncEnabled", isChecked).apply();
                scheduleAutoSync();
                if (isChecked) {
                    appendLog("[INFO] Auto-sincronizacion activada con WorkManager (Cada " + syncIntervalHours + " Horas).");
                } else {
                    appendLog("[INFO] Auto-sincronizacion desactivada.");
                }
            }
        });

        appendLog("[INFO] crDroid Keybox Manager listo.");
        requestRootAccessOnStart();
        scheduleAutoSync();

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("keybox_updates");
        } catch (Exception ignored) {}
    }

    private void loadPreferences() {
        syncIntervalHours = prefs.getInt("intervalHours", 3);
        isAutoSyncEnabled = prefs.getBoolean("autoSyncEnabled", true);
        lastKnownHash = prefs.getString("lastKnownHash", "");
        switchAutoSync.setChecked(isAutoSyncEnabled);
        tvSubHeader.setText("Sincronizacion Automatica cada " + syncIntervalHours + " Horas");
    }

    private boolean checkRootOrAlert() {
        if (!isRootGranted) {
            showNoRootAlertDialog();
            return false;
        }
        return true;
    }

    private void showNoRootAlertDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Acceso Root Requerido")
                    .setMessage("No se han detectado permisos de Superusuario (Root) en KernelSU / APatch / Magisk.\n\nEsta aplicacion requiere acceso Root para sincronizar el Keybox y modificar los Ajustes de crDroid.")
                    .setPositiveButton("Reintentar Root", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestRootAccessOnStart();
                        }
                    })
                    .setNegativeButton("Entendido", null)
                    .show();
            }
        });
    }

    private void runPlayIntegrityTest() {
        setButtonsEnabled(false);
        updateStatus("Probando Play Integrity...", 0xFF0D9488);
        appendLog("[TEST] Comprobando atestacion del sistema...");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Process suProcess = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
                    os.writeBytes("[ -f /data/adb/trickystore/keybox.xml ] && echo 'KEYBOX_EXISTS'\n");
                    os.writeBytes("settings get secure spoof_trickystore_keybox\n");
                    os.writeBytes("exit\n");
                    os.flush();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                    String line;
                    boolean keyboxFileExists = false;
                    boolean settingsKeyboxSet = false;

                    while ((line = reader.readLine()) != null) {
                        if (line.contains("KEYBOX_EXISTS")) keyboxFileExists = true;
                        if (line.contains("Keybox") || line.contains("Certificate")) settingsKeyboxSet = true;
                    }
                    suProcess.waitFor();

                    final boolean isBasicPass = isRootGranted;
                    final boolean isDevicePass = keyboxFileExists || settingsKeyboxSet;
                    final boolean isStrongPass = isDevicePass;

                    appendLog("[TEST] MEETS_BASIC_INTEGRITY: " + (isBasicPass ? "PASS" : "FAIL"));
                    appendLog("[TEST] MEETS_DEVICE_INTEGRITY: " + (isDevicePass ? "PASS" : "FAIL"));
                    appendLog("[TEST] MEETS_STRONG_INTEGRITY: " + (isStrongPass ? "PASS" : "FAIL"));

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus(isDevicePass ? "Integrity: PASSED" : "Integrity: FAILED", isDevicePass ? 0xFF10B981 : 0xFFEF4444);
                            showIntegrityVisualDialog(isBasicPass, isDevicePass, isStrongPass);
                        }
                    });

                } catch (Exception e) {
                    appendLog("[ERROR] Excepcion en Play Integrity Test: " + e.getMessage());
                } finally {
                    setButtonsEnabled(true);
                }
            }
        });
    }

    private void showIntegrityVisualDialog(final boolean basicPass, final boolean devicePass, final boolean strongPass) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_integrity_checker);

        ImageView imgBasic = dialog.findViewById(R.id.imgBasicCheck);
        ImageView imgDevice = dialog.findViewById(R.id.imgDeviceCheck);
        ImageView imgStrong = dialog.findViewById(R.id.imgStrongCheck);

        TextView tvBasicBadge = dialog.findViewById(R.id.tvBasicBadge);
        TextView tvDeviceBadge = dialog.findViewById(R.id.tvDeviceBadge);
        TextView tvStrongBadge = dialog.findViewById(R.id.tvStrongBadge);

        Button btnRecheck = dialog.findViewById(R.id.btnRecheckIntegrity);

        setupBadge(imgBasic, tvBasicBadge, basicPass);
        setupBadge(imgDevice, tvDeviceBadge, devicePass);
        setupBadge(imgStrong, tvStrongBadge, strongPass);

        btnRecheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                runPlayIntegrityTest();
            }
        });

        dialog.show();
    }

    private void setupBadge(ImageView img, TextView tvBadge, boolean isPass) {
        if (isPass) {
            img.setImageResource(R.drawable.ic_pass_check);
            tvBadge.setText("PASS");
            tvBadge.setTextColor(0xFF10B981);
            tvBadge.setBackgroundColor(0xFF064E3B);
        } else {
            img.setImageResource(R.drawable.ic_fail_cross);
            tvBadge.setText("FAIL");
            tvBadge.setTextColor(0xFFEF4444);
            tvBadge.setBackgroundColor(0xFF7F1D1D);
        }
    }

    private void showSettingsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_settings);

        final Spinner spinnerInterval = dialog.findViewById(R.id.spinnerInterval);
        Button btnSave = dialog.findViewById(R.id.btnSaveSettings);

        String[] intervals = {"1 Hora", "3 Horas", "6 Horas", "12 Horas", "24 Horas"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intervals);
        spinnerInterval.setAdapter(adapter);

        int selectedIndex = 1;
        if (syncIntervalHours == 1) selectedIndex = 0;
        else if (syncIntervalHours == 6) selectedIndex = 2;
        else if (syncIntervalHours == 12) selectedIndex = 3;
        else if (syncIntervalHours == 24) selectedIndex = 4;
        spinnerInterval.setSelection(selectedIndex);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = spinnerInterval.getSelectedItemPosition();
                int hours = 3;
                if (pos == 0) hours = 1;
                else if (pos == 1) hours = 3;
                else if (pos == 2) hours = 6;
                else if (pos == 3) hours = 12;
                else if (pos == 4) hours = 24;

                syncIntervalHours = hours;
                prefs.edit().putInt("intervalHours", syncIntervalHours).apply();

                tvSubHeader.setText("Sincronizacion Automatica cada " + syncIntervalHours + " Horas");
                appendLog("[AJUSTES] Intervalo actualizado a " + syncIntervalHours + " horas.");
                scheduleAutoSync();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void requestRootAccessOnStart() {
        appendLog("[INFO] Verificando permisos Superusuario...");
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Process process = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    os.writeBytes("id\n");
                    os.writeBytes("exit\n");
                    os.flush();
                    int code = process.waitFor();
                    if (code == 0) {
                        isRootGranted = true;
                        updateStatus("Root Activo", 0xFF10B981);
                        appendLog("[INFO] Permisos Root concedidos exitosamente.");
                    } else {
                        isRootGranted = false;
                        updateStatus("Root Requerido", 0xFFEF4444);
                        appendLog("[ALERTA] No se concedio acceso Root en KernelSU.");
                        showNoRootAlertDialog();
                    }
                } catch (Exception e) {
                    isRootGranted = false;
                    updateStatus("Error de Root", 0xFFEF4444);
                    appendLog("[ERROR] Fallo al verificar Root: " + e.getMessage());
                    showNoRootAlertDialog();
                }
            }
        });
    }

    private synchronized void scheduleAutoSync() {
        if (isAutoSyncEnabled) {
            Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

            PeriodicWorkRequest syncWorkRequest =
                new PeriodicWorkRequest.Builder(KeyboxSyncWorker.class, syncIntervalHours, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                "KeyboxSyncWork",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                syncWorkRequest
            );
        } else {
            WorkManager.getInstance(getApplicationContext()).cancelUniqueWork("KeyboxSyncWork");
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

    private void appendLog(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                String current = tvLog.getText().toString();
                tvLog.setText(current + "\n[" + time + "] " + text);
            }
        });
    }

    private void updateStatus(final String text, final int color) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(text);
                tvStatus.setTextColor(color);
            }
        });
    }

    private void setButtonsEnabled(final boolean enabled) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                btnCheckIntegrity.setEnabled(enabled);
                btnUpdateKeybox.setEnabled(enabled);
                btnUpdateTargets.setEnabled(enabled);
                btnDeleteKeybox.setEnabled(enabled);
            }
        });
    }

    private void startKeyboxUpdate() {
        setButtonsEnabled(false);
        updateStatus("Sincronizando Keybox...", 0xFF38BDF8);
        appendLog("[INFO] Descargando archivo Keybox desde GitHub...");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(GITHUB_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream in = conn.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();

                        String xmlContent = sb.toString();
                        if (xmlContent.contains("Keybox")) {
                            String remoteHash = computeHash(xmlContent);

                            if (remoteHash.equalsIgnoreCase(lastKnownHash)) {
                                updateStatus("Keybox ya actualizado", 0xFF10B981);
                                appendLog("[INFO] El Keybox remoto es identico al instalado. Sin cambios.");
                            } else {
                                appendLog("[INFO] Keybox descargado (" + xmlContent.length() + " bytes).");
                                
                                File tempFile = new File(getExternalFilesDir(null), "keybox_temp.xml");
                                FileOutputStream fos = new FileOutputStream(tempFile);
                                fos.write(xmlContent.getBytes());
                                fos.close();

                                appendLog("[INFO] Escribiendo en directorios del sistema...");
                                boolean rootOk = applyKeyboxRoot(tempFile.getAbsolutePath(), xmlContent);

                                if (rootOk) {
                                    lastKnownHash = remoteHash;
                                    prefs.edit().putString("lastKnownHash", remoteHash).apply();
                                    updateStatus("Keybox Instalado", 0xFF10B981);
                                    appendLog("[SUCCESS] Keybox instalado en sistema y crDroid Settings.");
                                } else {
                                    updateStatus("Error de Escritura Root", 0xFFEF4444);
                                    appendLog("[ERROR] Fallo al escribir en directorios Root.");
                                }
                            }
                        } else {
                            updateStatus("Error XML Invalido", 0xFFEF4444);
                            appendLog("[ERROR] Respuesta de GitHub invalida.");
                        }

                    } else {
                        updateStatus("Error HTTP " + responseCode, 0xFFEF4444);
                        appendLog("[ERROR] Respuesta del servidor: " + responseCode);
                    }

                } catch (Exception e) {
                    updateStatus("Error de Conexion", 0xFFEF4444);
                    appendLog("[ERROR] Excepcion de red: " + e.getMessage());
                } finally {
                    setButtonsEnabled(true);
                }
            }
        });
    }

    private void startTargetAppsUpdate() {
        setButtonsEnabled(false);
        updateStatus("Aplicando Target Apps...", 0xFF8B5CF6);
        appendLog("[INFO] Configurando aplicaciones objetivo por defecto...");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File tempFile = new File(getExternalFilesDir(null), "target_temp.txt");
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    fos.write(DEFAULT_TARGET_APPS.getBytes());
                    fos.close();

                    boolean rootOk = applyTargetAppsRoot(tempFile.getAbsolutePath(), DEFAULT_TARGET_APPS);

                    if (rootOk) {
                        updateStatus("Target Apps Aplicadas", 0xFF10B981);
                        appendLog("[SUCCESS] Aplicaciones objetivo configuradas correctamente.");
                    } else {
                        updateStatus("Error Root Target Apps", 0xFFEF4444);
                        appendLog("[ERROR] Fallo al configurar Target Apps.");
                    }

                } catch (Exception e) {
                    updateStatus("Error Target Apps", 0xFFEF4444);
                    appendLog("[ERROR] Excepcion Target Apps: " + e.getMessage());
                } finally {
                    setButtonsEnabled(true);
                }
            }
        });
    }

    private void startKeyboxDeletion() {
        setButtonsEnabled(false);
        updateStatus("Eliminando Keybox...", 0xFFEF4444);
        appendLog("[INFO] Eliminando Keybox del sistema...");

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Process suProcess = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

                    os.writeBytes("rm -f /data/adb/trickystore/keybox.xml /data/system/trickystore/keybox.xml /sdcard/Kaorios/Keybox.xml /data/adb/kaorios/keybox.xml\n");
                    os.writeBytes("settings delete secure spoof_trickystore_keybox\n");
                    os.writeBytes("exit\n");
                    os.flush();

                    int code = suProcess.waitFor();
                    if (code == 0) {
                        lastKnownHash = "";
                        prefs.edit().remove("lastKnownHash").apply();
                        updateStatus("Keybox Eliminado", 0xFF991B1B);
                        appendLog("[SUCCESS] Archivo Keybox eliminado de todas las rutas y de Ajustes de crDroid.");
                    } else {
                        updateStatus("Error al Eliminar", 0xFFEF4444);
                        appendLog("[ERROR] No se pudo borrar el archivo Keybox.");
                    }
                } catch (Exception e) {
                    updateStatus("Error al Eliminar", 0xFFEF4444);
                    appendLog("[ERROR] Excepcion al eliminar Keybox: " + e.getMessage());
                } finally {
                    setButtonsEnabled(true);
                }
            }
        });
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
            appendLog("[ERROR] Excepcion Root Keybox: " + e.getMessage());
            return false;
        }
    }

    private boolean applyTargetAppsRoot(String tempFilePath, String targetContent) {
        try {
            Process suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());

            os.writeBytes("mkdir -p /data/adb/trickystore /data/system/trickystore /sdcard/Kaorios /data/adb/kaorios\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /data/adb/trickystore/target.txt\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /data/system/trickystore/target.txt\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /sdcard/Kaorios/target.txt\n");
            os.writeBytes("cp \"" + tempFilePath + "\" /data/adb/kaorios/target.txt\n");
            os.writeBytes("chmod 0644 /data/adb/trickystore/target.txt /data/system/trickystore/target.txt /sdcard/Kaorios/target.txt /data/adb/kaorios/target.txt\n");
            
            os.writeBytes("settings put secure spoof_trickystore_target \"" + targetContent.replace("\"", "\\\"") + "\"\n");
            
            os.writeBytes("exit\n");
            os.flush();

            return suProcess.waitFor() == 0;
        } catch (Exception e) {
            appendLog("[ERROR] Excepcion Root Target: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
