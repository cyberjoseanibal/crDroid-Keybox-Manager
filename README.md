# crDroid Keybox Manager

Un gestor y sincronizador automático de **Keybox.xml** y **Target Apps** diseñado para la ROM **crDroid 12.1 (Android 16)** y dispositivos con **KernelSU / APatch / Magisk**.

![crDroid Keybox Manager](https://raw.githubusercontent.com/Wuang26/Kaorios-Toolbox/main/Toolbox-data/Keybox.xml)

---

## 🌟 Características Principales

- **Sincronización Automática cada 3 Horas:** Monitorea de forma ligera el repositorio de GitHub y reemplaza la clave `Keybox.xml` automáticamente si detecta revocaciones o actualizaciones.
- **Integración Directa con Ajustes de crDroid:** Escribe en `Settings.Secure` (`spoof_trickystore_keybox` y `spoof_trickystore_target`) para que el estado aparezca inmediatamente reflejado en **Ajustes de crDroid -> Varios -> TrickyStore**.
- **Configuración de Target Apps en 1-clic:** Aplica automáticamente la lista de aplicaciones objetivo (Google Wallet, GMS, Play Store, ARCore).
- **Opción para Eliminar Keybox:** Permite borrar la clave del sistema y restaurar el estado inicial en cualquier momento.
- **Interfaz Material 3 Dark:** Diseño limpio y sin elementos innecesarios, con registro de eventos en tiempo real.
- **Eficiencia de Batería (0.0%):** Utiliza firmas criptográficas SHA-256 para evitar escrituras y consumo de recursos innecesarios.

---

## 🚀 Instalación y Uso

1. Descarga el archivo APK listo para instalar desde la sección de [Releases](../../releases).
2. Instala el APK en tu dispositivo Android.
3. Abre la aplicación y concédele acceso **Superusuario (Root)** cuando KernelSU / APatch / Magisk lo solicite.
4. Presiona **Sincronizar Keybox Manualmente** o activa la **Auto-Sincronización** para dejar que la app gestione todo en segundo plano.

---

## 🛠️ Estructura del Proyecto

```text
crDroidKeyboxApp/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/crdroid/keyboxupdater/MainActivity.java
│   │   └── res/
│   │       ├── drawable/
│   │       ├── layout/activity_main.xml
│   │       └── values/
```

---

## 📜 Licencia

Este proyecto está distribuido bajo la licencia MIT.
