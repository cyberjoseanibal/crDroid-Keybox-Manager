# crDroid Keybox Manager App

Aplicación companion para **crDroid (Android 16)** encargada de gestionar y sincronizar automáticamente la clave **Keybox.xml** y las **Target Apps** desde GitHub.

## 🚀 Novedades y Optimizaciones

- **Segundo plano con WorkManager:** Reemplazado el temporizador de hilo por `PeriodicWorkRequest` nativo con restricciones de red activa (`NetworkType.CONNECTED`).
- **Control de Hash Criptográfico:** Persistencia de `lastKnownHash` en `SharedPreferences` para evitar escrituras en bucle si el archivo remoto es idéntico al local.
- **Acceso Root Seguro:** Ejecución limpia de comandos `su` tanto en primer plano como en el worker de segundo plano.

## 🛠️ Tecnologías

- Java / Android SDK 34 (Android 16)
- AndroidX WorkManager 2.9.0
- Material Design 3
