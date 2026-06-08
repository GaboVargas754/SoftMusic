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

## Notas

La app necesita permiso para leer audio local. En Android 13 o superior usa `READ_MEDIA_AUDIO`; en Android 12 o menor usa `READ_EXTERNAL_STORAGE`.
