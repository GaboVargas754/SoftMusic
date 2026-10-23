# SoftMusic

SoftMusic es un reproductor Android nativo offline con biblioteca local e interfaz oscura en turquesa.

## Funciones del MVP

- Escaneo de canciones locales con `MediaStore`.
- Seleccion de una carpeta especifica para filtrar la biblioteca y la cola visible.
- Reproduccion offline con Media3 / ExoPlayer.
- Play, pausa, anterior y siguiente.
- Mini reproductor inferior.
- Creación de playlists a pantalla completa, con nombre sugerido, validación y controles adaptados al teclado.
- En Inicio, Seguir escuchando sustituye al mini reproductor con controles de transporte; las otras ventanas conservan el mini reproductor.
- Fondos de reproducción derivados de la portada con tonos oscuros para mantener los controles blancos y los estados activos en turquesa.
- Reproductor completo con progreso, volumen y cola.
- Reproductor adaptable que distribuye portada, información y controles por la altura de la pantalla, con nota simple de DJ activo y sin bloque de letras.
- Ajustes organizados por secciones para consultar reproducción, biblioteca y configuración de la aplicación.
- Fila de reproducción compacta con origen real, reordenamiento por arrastre, confirmación al quitar canciones y accesos a aleatorio y temporizador.
- Modos de reproduccion: orden, repetir lista, repetir cancion actual y aleatorio.
- Vistas por canciones, artistas y albumes.
- Ordenamiento por recientes o titulo.
- Favoritos y playlists locales.
- Tema oscuro fijo con turquesa como color base.
- Inicio propio con tarjetas de favoritos/canciones, álbumes recientes, progreso de escucha y creación directa de playlists; biblioteca con búsqueda y filtros de colecciones.
- Tus canciones presenta la carpeta seleccionada como colección, con collage, duración, reproducción, aleatorio y controles de carpeta y orden; álbumes y artistas se consultan en Biblioteca.
- Portadas en collage y cabeceras con degradados para playlists, favoritos, álbumes y artistas.
- Animaciones breves de navegación, filtros y reproductor; cambios suaves de portada y color, respetando la escala de Android y el modo de alto rendimiento.
- Íconos vectoriales locales con estado normal gris y selección turquesa en navegación, favoritos y modos de reproducción; el reproductor conserva los colores de portada.
- Cambio de modo con actualización de cola por bloques, conservación de la canción actual y guardado de sesión fuera del hilo principal.
- MediaSessionService para reproduccion en segundo plano y controles del sistema.
- Modo DJ clásico y experto para enlazar canciones desde los ajustes de reproducción.

## Modo DJ

Clásico y Experto están disponibles en Ajustes con opciones de 5 a 8 segundos.
Auto DJ por colección se retiró de Favoritos y playlists; sus configuraciones antiguas
no se aplican a la reproducción ni al orden de las canciones.

## Actualizaciones

La aplicación consulta `https://softmusic.cgvslab.online/update.json` y usa
`https://softmusic.cgvslab.online/downloads/SoftMusic.apk` como descarga predeterminada.
Se conservan la carpeta `downloads` y el nombre `SoftMusic.apk`.
El aviso muestra la versión instalada y la nueva, con novedades desplazables y acciones adaptadas a actualizaciones opcionales u obligatorias.

## Stack

- Kotlin
- Jetpack Compose
- AndroidX Media3
- Coil para caratulas locales

## Estructura

- `app`: aplicacion Android.
- `shared`: modelos y reglas comunes reutilizables.
- `desktop-panel`: panel HTML experimental para controlar la app Android en red local.
- `design/icons`: originales SVG normales/seleccionados y catálogo de los 91 íconos; los 182 VectorDrawable están en `app/src/main/res/drawable`.

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
