# Calendar

## English

Android viewer for a remote calendar image, with a private offline copy. Application ID: `iecxiv.workcalendarhttp`. Requires Android 8.0 / API 26 or newer.

### Setup and settings

On first launch, enter a direct HTTP/HTTPS image URL, optionally enable HTTP Basic authentication (username/password) and/or custom HTTP headers, then save. Settings are saved before downloading, so failures can be retried or corrected.

To reopen settings, tap each of the four corners of the image viewer frame once, in any order, within five seconds. Repeated corners do not count twice; drags, pinches and long presses do not count as taps. Each corner has a touch area of up to 48 dp. This gesture is a shortcut, not an authentication mechanism.

- Add and remove custom header name/value pairs, including Authorization for bearer tokens when Basic is disabled.
- Basic and custom headers are applied to both initial downloads and update checks. Duplicate headers, control characters, selected transport headers and conflicting Authorization settings are rejected.
- Remember zoom, pan and rotation is enabled by default. Disable it to clear the saved view and start subsequent openings at the default view. Gestures remain available.
- Configuration is encrypted with an Android Keystore key and stored outside Android backup. It survives in-place updates, but not clearing app data or uninstalling. View state and cached image are private app data.
- Prefer HTTPS. HTTP sends credentials without transport encryption. Redirects are followed only within the same scheme, host and effective port, up to five redirects.

### Image behavior

The cached image is displayed immediately when it belongs to the configured URL. Each launch checks the remote image using the saved URL and authentication, compares SHA-256 and offers Reload/Later if it changed. Saving settings triggers an immediate refresh. Failed downloads do not overwrite the old file. Requests have timeouts and downloads are limited to 30 MiB. Supply an Android-supported raster image, such as JPEG or PNG, not HTML or PDF. There is no periodic background service.

Pinch to zoom/rotate and drag to pan. Double-tap resets the view. English is the default UI language; Spanish resources are included.

### Build

Use Android Studio with JDK 17 and the SDK required by Gradle, or run:

```bash
bash ./gradlew :app:assembleDebug
```

## Español

Visor Android de una imagen remota de calendario con copia privada sin conexión. Identificador: `iecxiv.workcalendarhttp`. Requiere Android 8.0 / API 26 o posterior.

### Configuración y ajustes

En el primer inicio introduce una URL directa HTTP/HTTPS de la imagen. Opcionalmente activa HTTP Basic con usuario y contraseña y/o headers HTTP personalizados y guarda. Los ajustes se guardan antes de descargar, de modo que los fallos pueden reintentarse o corregirse.

Para abrir los ajustes toca una vez cada una de las cuatro esquinas del recuadro del visor, en cualquier orden, dentro de cinco segundos. Repetir una esquina no suma; arrastres, pellizcos y pulsaciones largas no cuentan. Cada esquina tiene un área táctil de hasta 48 dp. El gesto es un acceso directo, no una medida de autenticación.

- Permite añadir y eliminar pares nombre/valor de headers, incluido Authorization para tokens bearer si Basic está desactivado.
- Basic y los headers se aplican tanto a la descarga inicial como a la búsqueda de actualizaciones. Se rechazan duplicados, caracteres de control, determinados headers de transporte y conflictos con Authorization.
- Recordar zoom, desplazamiento y rotación está activado por defecto. Al desactivarlo se borra la vista guardada y las siguientes aperturas empiezan con la vista predeterminada. Los gestos siguen disponibles.
- La configuración se cifra con una clave de Android Keystore y se excluye de las copias de seguridad de Android. Se conserva al actualizar, pero no al borrar datos o desinstalar. La vista y la imagen local son datos privados de la app.
- Usa HTTPS preferentemente. HTTP transmite credenciales sin cifrado de transporte. Solo se siguen redirecciones con el mismo protocolo, servidor y puerto efectivo, hasta cinco saltos.

### Comportamiento de la imagen

La copia local se muestra inmediatamente si corresponde a la URL configurada. Cada inicio comprueba la imagen remota con la URL y autenticación guardadas, compara SHA-256 y ofrece Recargar/Más tarde si cambia. Guardar ajustes fuerza una actualización inmediata. Una descarga fallida no sobrescribe el archivo anterior. Hay tiempos de espera y un límite de 30 MiB por descarga. Usa una imagen raster compatible con Android, como JPEG o PNG, no HTML ni PDF. No hay servicio periódico en segundo plano.

Pellizca para ampliar o girar y arrastra para desplazar. El doble toque restablece la vista. Inglés como idioma predeterminado y recursos en español incluidos.

### Compilación

Usa Android Studio con JDK 17 y el SDK requerido por Gradle, o ejecuta:

```bash
bash ./gradlew :app:assembleDebug
```
