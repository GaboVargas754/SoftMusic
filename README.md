# SoftMusic

SoftMusic es un reproductor Android nativo offline inspirado en Spotify.

## Funciones del MVP

- Escaneo de canciones locales con `MediaStore`.
- Seleccion de una carpeta especifica para filtrar la biblioteca y la cola visible.
- Reproduccion offline con Media3 / ExoPlayer.
- Play, pausa, anterior y siguiente.
- Mini reproductor inferior.
- Reproductor completo con progreso y volumen.
- Modos de reproduccion: reproducir en orden, repetir lista, repetir cancion actual, aleatorio y aleatorio absoluto.
- Cambio de orden de reproduccion por recientes, titulo, artista o album.
- MediaSessionService para reproduccion en segundo plano y controles del sistema.

## Stack

- Kotlin
- Jetpack Compose
- AndroidX Media3
- Coil para caratulas locales

## Estructura

- `app`: aplicacion Android.
- `shared`: modelos y estado compartidos entre Android y desktop.
- `desktop`: version PC/Linux con Compose Desktop.

## Desktop MVP

- Seleccion de carpeta local de musica.
- Escaneo recursivo de subcarpetas con `java.nio.file`.
- Lectura de metadatos con Jaudiotagger: titulo, artista, album, duracion y caratulas embebidas.
- Listado de archivos de audio compatibles: MP3, FLAC, WAV, OGG, OPUS, M4A, AAC, AIFF, ALAC y WMA.
- Reproduccion desktop inicial con VLC externo (`cvlc`): play, pausa, anterior, siguiente y progreso.
- Cola desktop con autoavance al terminar la cancion y modos orden, repetir lista, repetir cancion y aleatorio.
- Busqueda desktop por cancion, artista, album y carpeta.
- Ordenamiento desktop por recientes o titulo.
- Vistas desktop por canciones, artistas, albumes y carpetas.
- Filtro desktop por carpeta.
- UI desktop con acciones principales mediante iconos y controles de reproductor visuales.
- Persistencia desktop de carpeta seleccionada, favoritos y playlists.
- Panel de configuracion desktop con tema sistema/oscuro/claro, caratulas, autoescaneo al iniciar y volumen inicial.
- Temas desktop equivalentes al telefono: claro, oscuro, noche azul, bosque, atardecer, lavanda y grafito.
- Modo DJ desktop con crossfade usando dos procesos VLC externos y mezcla configurable entre 5 y 8 segundos.

La configuracion desktop se guarda en `$XDG_CONFIG_HOME/SoftMusic/desktop.properties` o `~/.config/SoftMusic/desktop.properties`.

En Arch Linux instala VLC para que libVLC este disponible:

```bash
sudo pacman -S vlc
```

## Requisitos

- Android Studio o Gradle instalado.
- Android SDK con `compileSdk 35`.
- Min SDK 26.

## Ejecutar

Abre el proyecto en Android Studio y ejecuta el modulo `app` en un dispositivo o emulador Android.

Si tienes Gradle configurado localmente:

```bash
gradle :app:assembleDebug
```

Para compilar la base desktop:

```bash
./gradlew :desktop:classes
```

Para ejecutar desktop:

```bash
./gradlew :desktop:run
```

## Notas

La app necesita permiso para leer audio local. En Android 13 o superior usa `READ_MEDIA_AUDIO`; en Android 12 o menor usa `READ_EXTERNAL_STORAGE`.
