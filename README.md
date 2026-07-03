# SoftMusic

SoftMusic es un reproductor Android nativo offline inspirado en Spotify.

## Funciones del MVP

- Escaneo de canciones locales con `MediaStore`.
- Seleccion de una carpeta especifica para filtrar la biblioteca y la cola visible.
- Reproduccion offline con Media3 / ExoPlayer.
- Play, pausa, anterior y siguiente.
- Mini reproductor inferior.
- Reproductor completo con progreso, volumen y cola.
- Modos de reproduccion: orden, repetir lista, repetir cancion actual y aleatorio.
- Vistas por canciones, artistas y albumes.
- Ordenamiento por recientes o titulo.
- Favoritos y playlists locales.
- Temas claro, oscuro y variantes visuales.
- MediaSessionService para reproduccion en segundo plano y controles del sistema.

## Stack

- Kotlin
- Jetpack Compose
- AndroidX Media3
- Coil para caratulas locales

## Estructura

- `app`: aplicacion Android.
- `shared`: modelos y reglas comunes reutilizables.

## Requisitos

- Android Studio o Gradle instalado.
- Android SDK con `compileSdk 35`.
- Min SDK 26.

## Ejecutar

Abre el proyecto en Android Studio y ejecuta el modulo `app` en un dispositivo o emulador Android.

Si tienes Gradle configurado localmente:

```bash
./gradlew :app:assembleDebug
```

Para ejecutar las pruebas del modulo compartido:

```bash
./gradlew :shared:test
```

## Notas

La app necesita permiso para leer audio local. En Android 13 o superior usa `READ_MEDIA_AUDIO`; en Android 12 o menor usa `READ_EXTERNAL_STORAGE`.
