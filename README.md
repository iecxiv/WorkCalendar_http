# Calendar

A small Android app that displays a remote calendar image in a zoomable viewer and keeps a private offline copy.

## English

### Features

- First-run setup for a direct HTTP or HTTPS image URL; no hardcoded server address.
- The URL is saved after the first successful image download and reused for initial downloads and update checks.
- Cached image shown immediately on subsequent launches, including while offline.
- Remote image downloaded and compared by SHA-256 when opening the app; a dialog offers to reload if its contents changed. This is a launch-time check, not a background polling service.
- Failed downloads and invalid image responses do not replace the existing calendar.
- Pinch zoom, pan and rotation; the view transformation is saved and restored across launches. Double-tap resets the view.
- English by default; Spanish UI on devices using Spanish. The app name is Calendar in both languages.

### Setup and usage

1. Open Calendar and enter a direct URL, for example `https://example.com/calendar.jpg`. Use an image format supported by Android, such as JPEG or PNG, not a web page or a PDF.
2. Tap **Save**. If downloading fails, retry or edit the URL.
3. After a successful download, the URL is stored in private app preferences. It survives app restarts and in-place updates. Clearing app data or uninstalling removes it; reinstalling may restore data if Android backup is available.
4. On later launches the saved image opens immediately. If the remote content changed, choose **Reload** or **Later**.

Existing installations without a configured URL are also prompted once, even if they already have an image cached from the previous hardcoded server. The old image is only replaced after a valid download.

There is currently no settings screen to change a successfully saved URL. Clearing app data restarts setup, but also removes the cached image and saved view.

### Network and privacy

HTTP is supported for local/self-hosted servers, but it is unencrypted; prefer HTTPS when possible. The server must be reachable from the device, for example through the same LAN or a VPN. URLs containing embedded username/password credentials are not accepted. The URL and downloaded image are stored in the private app data directory; the URL is not a secret vault.

### Build

Application ID and namespace: `iecxiv.workcalendarhttp`.

Use Android Studio with JDK 17 and the Android SDK required by the Gradle project, or run:

```bash
bash ./gradlew :app:assembleDebug
```

Android 8.0 (API 26) or newer is required.

---

## Español

Aplicación Android sencilla que muestra la imagen remota de un calendario con zoom y conserva una copia privada para consultarla sin conexión.

### Funciones

- Configuración inicial de una URL directa HTTP o HTTPS, sin una dirección de servidor fija en el código.
- La URL se guarda tras la primera descarga válida y se reutiliza para las descargas iniciales y la búsqueda de actualizaciones.
- La imagen guardada se muestra inmediatamente en los siguientes inicios, también sin conexión.
- Al abrir la app se descarga la imagen remota y se compara mediante SHA-256. Si cambia su contenido, se ofrece recargarla. No hay un servicio de sondeo en segundo plano.
- Las descargas fallidas o las respuestas que no sean imágenes válidas no sustituyen el calendario existente.
- Zoom con dos dedos, desplazamiento y rotación; la vista se guarda y restaura entre aperturas. El doble toque restablece la vista.
- Interfaz en español cuando el dispositivo utiliza español; inglés como idioma predeterminado. El nombre es Calendar en ambos idiomas.

### Configuración y uso

1. Abre Calendar e introduce una URL directa, por ejemplo `https://example.com/calendar.jpg`. Usa una imagen compatible con Android, como JPEG o PNG, no una página web ni un PDF.
2. Pulsa **Guardar**. Si falla la descarga, puedes reintentar o editar la URL.
3. Tras descargar correctamente, la URL se guarda en las preferencias privadas de la app. Se conserva al cerrar y al actualizar la aplicación. Borrar los datos o desinstalar la elimina; una reinstalación podría restaurar los datos si existe una copia de seguridad de Android.
4. En los siguientes inicios aparece la imagen guardada. Si cambia el contenido remoto, elige **Recargar** o **Más tarde**.

Las instalaciones anteriores sin URL configurada también la solicitan una vez, aunque tengan una imagen del servidor fijo anterior. Esa imagen solo se sustituye tras una descarga válida.

Actualmente no hay una pantalla de ajustes para cambiar una URL guardada correctamente. Borrar los datos reinicia la configuración, pero también elimina la imagen local y la vista guardada.

### Red y privacidad

Se admite HTTP para servidores locales o autogestionados, pero no cifra el tráfico; utiliza HTTPS cuando sea posible. El servidor debe ser accesible desde el dispositivo, por ejemplo mediante la misma red local o una VPN. No se admiten URLs con usuario y contraseña integrados. La URL y la imagen se guardan en los datos privados de la aplicación; la URL no se almacena en una caja fuerte de secretos.

### Compilación

Identificador y namespace: `iecxiv.workcalendarhttp`.

Usa Android Studio con JDK 17 y el SDK requerido por el proyecto Gradle, o ejecuta:

```bash
bash ./gradlew :app:assembleDebug
```

Requiere Android 8.0 (API 26) o posterior.
