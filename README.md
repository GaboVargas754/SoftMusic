# SoftMusic

SoftMusic es un reproductor Android nativo offline con biblioteca local e interfaz oscura en turquesa.

## Respaldo y restauración (3.2.0)

En **Ajustes → Respaldo y restauración**, crear un respaldo abre el selector de Android para guardar un archivo `.softmusic.json`. Incluye playlists con su orden y referencia de portada, favoritos y ajustes portables. Los archivos de música, imágenes externas, historial y permisos del dispositivo no se incluyen.

Al abrir un respaldo se muestran las canciones encontradas, faltantes y ambiguas antes de guardar. Las playlists se crean como copias; los favoritos se suman sin borrar los actuales. **Aplicar preferencias** está desmarcado inicialmente. Las rutas de carpetas importadas solo se aplican si existen en la biblioteca del dispositivo.

Las referencias sin coincidencia segura se conservan aunque no puedan reproducirse. Después de copiar los audios al dispositivo y actualizar la biblioteca, **Revisar pendientes** permite vincular las coincidencias únicas, conservando el orden de las listas. No se adivinan correspondencias por título o identificador local. Renombrar el archivo o cambiar sus metadatos puede impedir la vinculación automática.

El formato `SoftMusicBackup` versión 1 admite hasta 10 MiB, 10 000 referencias de canciones, 500 playlists y 100 000 entradas. El archivo contiene nombres y rutas de la biblioteca en texto legible; se guarda únicamente en el destino elegido por el usuario.

## Funciones principales

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
- Menú de Inicio con Actualizaciones, Notificaciones, Ajustes y Cerrar aplicación; Notificaciones abre la configuración de Android de esta app.
- Fila de reproducción compacta con origen real, reordenamiento por arrastre, confirmación al quitar canciones y accesos a aleatorio y temporizador.
- Modos de reproduccion: orden, repetir lista, repetir cancion actual y aleatorio.
- Aleatorio activable desde la cabecera de Inicio antes de reproducir; Inicio, Ajustes y reproductor comparten el modo y guardan la elección.
- Logo horizontal de Inicio automático con diez variantes transparentes en WebP, sincronizado con el calendario del ícono; aleatorio y menú en una misma fila. Las fuentes y la preparación reproducible están en [design/logos](design/logos/README.md).
- Inicio prioriza la sesión actual, seguida de accesos rápidos, playlists e historial.
- Vistas por canciones, artistas y albumes.
- Ordenamiento por recientes o titulo.
- Favoritos y playlists locales.
- Respaldo y restauración desde Ajustes: archivo JSON portable con playlists (orden y referencia de portada), favoritos y preferencias opcionales. Restaura listas como copias y conserva canciones sin localizar para vincularlas después; no incluye audios, carátulas externas ni historial.
- Reconocimiento conservador de canciones por nombre de archivo, metadatos, tamaño y duración, sin depender del identificador de MediaStore; coincidencias ambiguas quedan pendientes.
- Edición de playlists: renombrar, guardar el orden por arrastre, añadir varias canciones con búsqueda y quitar selecciones con confirmación.
- Acciones de playlists en una sola fila contextual de cabecera, con opciones secundarias en el menú y un [estándar compartido de íconos](design/icons/README.md).
- Biblioteca automática con detección de cambios, actualización al volver a la app y refresco manual; conserva preferencias y colecciones ante archivos temporalmente inaccesibles.
- Historial local de escucha con recientes y más reproducidas, acceso desde Inicio y controles para borrar o desactivar el registro.
- Tema oscuro fijo con turquesa como color base.
- Inicio propio con tarjetas de favoritos/canciones, álbumes recientes, progreso de escucha y creación directa de playlists; biblioteca con búsqueda y filtros de colecciones.
- Tus canciones presenta la carpeta seleccionada como colección, con collage, duración, reproducción, aleatorio y controles de carpeta y orden; álbumes y artistas se consultan en Biblioteca.
- Portadas en collage y cabeceras con degradados para playlists, favoritos, álbumes y artistas.
- Animaciones breves de navegación, filtros y reproductor; cambios suaves de portada y color, respetando la escala de Android y el modo de alto rendimiento.
- Íconos vectoriales locales con estado normal gris y selección turquesa en navegación, favoritos y modos de reproducción; el reproductor conserva los colores de portada.
- Cambio de modo con actualización de cola por bloques, conservación de la canción actual y guardado de sesión fuera del hilo principal.
- MediaSessionService para reproduccion en segundo plano y controles del sistema.
- Modo DJ clásico y experto para enlazar canciones desde los ajustes de reproducción.
- Íconos festivos automáticos en el lanzador y dentro de la app, según la fecha local y sin conexión.

## Calendario de íconos

Los períodos incluyen el primer y el último día. Fuera de estas temporadas se utiliza el original.

| Diseño | Período |
| --- | --- |
| Año Nuevo | 31 de diciembre al 10 de enero |
| San Valentín | 7 al 21 de febrero |
| Pascua | Siete días antes y siete después del Domingo de Pascua, calculado cada año |
| Aniversario de SoftMusic, 5 de junio | 29 de mayo al 12 de junio |
| Día de la Música | 15 al 30 de junio |
| Independencia de México | 1 al 30 de septiembre |
| Halloween | 15 al 31 de octubre |
| Día de Muertos | 1 al 10 de noviembre |
| Navidad | 1 al 30 de diciembre |

La selección se revisa al abrir o volver a la app, al cambiar la hora o zona horaria,
al reiniciar el teléfono y después de actualizar la aplicación. Una alarma no exacta
programa el siguiente cambio de temporada sin despertar el teléfono ni solicitar
permisos de alarmas exactas. Android puede retrasar esa revisión si el dispositivo
está dormido o ahorrando batería; al volver a la app se aplica la fecha vigente.
El lanzador puede tardar en actualizar su caché visual.
Mientras SoftMusic está visible se actualiza el logo interno y se difiere el cambio
del acceso del lanzador hasta pasar a segundo plano, para evitar que Android cierre
la pantalla que se abrió mediante el acceso anterior.

Las variantes de acceso mantienen nombres de componente estables y una sola entrada
activa. En Android 13 o posterior el cambio es atómico; en versiones anteriores se
habilita primero el reemplazo para conservar siempre un acceso disponible.

## Modo DJ

Clásico y Experto están disponibles en Ajustes con opciones de 5 a 8 segundos.
En Experto, ese valor es el **máximo**: la energía y el ataque de las canciones determinan
una mezcla habitual de 3 segundos hasta el máximo elegido. Los silencios y la preparación
tardía pueden reducirla para respetar el final audible.

Experto compensa el nivel de la canción entrante únicamente durante la transición, usando
el nivel típico de sus primeros 15 segundos con audio. La compensación inicial va de 0.4 a
1.0 y se retira suavemente dentro del fundido hasta recuperar el volumen normal al terminar.
No aplica ajustes de nivel al inicio ni durante la reproducción ordinaria, y no es una medición
LUFS de la canción completa. Un intercambio progresivo de graves acompaña el fundido y
desaparece al terminar o cancelar, sin dejar una atenuación permanente.

Si ambos pulsos tienen confianza suficiente, tiempos compatibles y cobertura del tramo,
se alinean los comienzos de la transición cerca del punto elegido. La velocidad permanece
original: no se promete sincronización sostenida entre ritmos distintos. La detección dudosa
o una preparación tardía mantienen el fundido seguro sin indicar alineación rítmica.

**Probar transición**, dentro de Configurar mezcla → Experto, permite escuchar la canción
actual y la siguiente sin esperar hasta el final. Pausa temporalmente la sesión original y
usa reproductores separados; al terminar, detener, cerrar o salir de la pantalla se recupera
la posición y el estado original, siempre que el usuario no haya modificado la sesión.
La prueba no se registra en el historial y requiere una canción actual con sucesora en la cola.
El modo Experto prepara el análisis hasta 120 segundos antes del final, con un presupuesto
combinado de 15 segundos. Examina hasta 90 segundos de cola y 15 de entrada; solo recorta
silencios respaldados por muestras decodificadas suficientes. Los resultados incompletos
usan la transición clásica como alternativa. Los inicios y finales suaves se detectan con
un umbral conservador independiente del nivel del cuerpo de la canción.

La siguiente canción se precarga en pausa y el tiempo de fundido se recalcula cuando está
lista, respetando el final audible y el tiempo restante. El fundido experto usa un reloj
monotónico y actualizaciones de volumen cada 20 ms; una demora de ejecución no alarga
artificialmente la mezcla. Pausas, búsquedas, cambios de cola y errores descartan el trabajo
anterior, detienen el reproductor auxiliar y recuperan el volumen del principal.

La caché de análisis comprueba tamaño, modificación y generación del proveedor cuando
están disponibles. Las fuentes sin una revisión comprobable no se almacenan en caché.
Las llamadas nativas al proveedor o al decodificador no siempre pueden interrumpirse;
los límites se comprueban al recuperar el control y durante la decodificación.

La validación de dispositivo se ejecuta con `:app:assembleDebugAndroidTest` y el runner
`com.softmusic.app.player.DjValidationInstrumentation`; genera WAVs de prueba y deja
resultados y trazas en `files/dj-validation/` de la app. Las muestras reconstruidas con
`tools/render_dj_validation_audio.py` ilustran las ganancias medidas, no son una grabación
de la salida de audio del dispositivo.
Auto DJ por colección se retiró de Favoritos y playlists; sus configuraciones antiguas
no se aplican a la reproducción ni al orden de las canciones.
Al añadir, quitar o reordenar canciones de la cola se descarta la preparación DJ
anterior y se calcula la siguiente mezcla desde la cola vigente, sin reiniciar la canción actual.

## Orden inteligente

El botón de aleatorio de cada playlist recorre **Desactivado → Aleatorio → Aleatorio
inteligente → Desactivado**. La selección se guarda por ID de playlist y se recupera al
reproducirla. Configurar otra playlist no modifica la que está sonando. No requiere activar
DJ ni un interruptor global en Ajustes: el modo DJ controla las transiciones de audio,
mientras el aleatorio inteligente controla el orden. Compara el pulso detectado y
el nivel RMS de la entrada de las canciones; no reconoce tonalidad, género ni mide LUFS.
Se conserva la variedad entre candidatas similares y las canciones sin datos fiables
mantienen su oportunidad de reproducirse. No necesita internet ni cambia los archivos.

El icono **Ordenar siguientes por compatibilidad**, en la fila de reproducción, analiza
las canciones pendientes con progreso y permite cancelar desde el mismo control. Respeta
la canción actual, su posición y las canciones anteriores. El análisis automático trabaja
por grupos limitados; el ordenamiento solicitado abarca toda la parte pendiente. Si no
hay mediciones suficientes, conserva el orden y lo informa. Las ediciones manuales de la
cola tienen prioridad sobre la selección automática.

El modo automático conserva cada grupo ya ordenado hasta consumirlo, para evitar volver
a posponer canciones desconocidas. Al cerrar un ciclo completo, la primera elección del
nuevo ciclo conserva el aleatorio normal; las siguientes vuelven a analizarse por grupos.
Si queda poco tiempo para mezclar, se mantiene la cola preparada. Los límites actuales
son 12 candidatas y 15 segundos totales por grupo automático, y 2 segundos de análisis
por canción; el ordenamiento manual puede cancelarse sin aplicar resultados parciales.

## Historial de escucha

Una escucha se registra tras 30 segundos de reproducción audible, o la mitad de la canción
si dura menos de un minuto. Las pausas, el buffering, los saltos de posición y las precargas
silenciosas no suman tiempo. Las mezclas DJ siguen ambos reproductores y conservan la misma
escucha al cambiar el reproductor principal.
El registro funciona desde el servicio, también con la pantalla apagada, y se guarda en
una base SQLite local. Comienza a construirse al utilizar esta función; no se deduce de
la fecha de incorporación de los archivos. Ajustes permite desactivarlo o borrarlo.

## Actualizaciones

La aplicación consulta `https://softmusic.cgvslab.online/update.json` y usa
`https://softmusic.cgvslab.online/downloads/SoftMusic.apk` como descarga predeterminada.
Se conservan la carpeta `downloads` y el nombre `SoftMusic.apk`.
El aviso muestra la versión instalada y la nueva, con novedades desplazables y acciones adaptadas a actualizaciones opcionales u obligatorias.
La búsqueda manual distingue una versión al día de los errores de conexión, tiempo de espera,
servidor o información de versión inválida. Los fallos de la consulta automática al abrir
la app permanecen silenciosos y se puede volver a consultar desde Ajustes.

## Stack

- Kotlin
- Jetpack Compose
- AndroidX Media3
- Coil para caratulas locales

## Estructura

- `app`: aplicacion Android.
- `shared`: modelos y reglas comunes reutilizables.
- `design/icons`: originales SVG normales/seleccionados y catálogo de los 91 íconos; los 182 VectorDrawable están en `app/src/main/res/drawable`.

Dentro de `app/src/main/java/com/softmusic/app`:

- `ui/SoftMusicApp.kt` conecta las pantallas y sus acciones; `AppNavigation.kt` reúne las decisiones de navegación. Biblioteca, búsqueda, favoritos, playlists, reproductor y diálogos tienen componentes propios.
- `player/MusicViewModel.kt` coordina las acciones y el estado de la interfaz. `ActivePlaybackQueue` mantiene el orden y origen de la cola; `PlaybackQueueController` aplica cambios a Media3 conservando la canción y posición actuales.
- `PlaybackSessionCoordinator` gestiona la restauración y la política de guardado; `MusicPersistenceStore` conserva los datos locales y `MediaControllerConnection` administra la conexión al reproductor.
- `player/MusicService.kt` integra el ciclo de vida de Android, la sesión y las notificaciones. `DjDeckController` administra los reproductores, `DjPlaybackCoordinator` coordina las mezclas y `ExpertDjPlanner` calcula las transiciones.

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

Para ejecutar las pruebas del módulo compartido y las pruebas unitarias de la app:

```bash
./gradlew :shared:test :app:testDebugUnitTest
```

Para revisar los diagnósticos de Android Lint:

```bash
./gradlew :app:lintDebug
```

## Notas

La app necesita permiso para leer audio local. En Android 13 o superior usa `READ_MEDIA_AUDIO`; en Android 12 o menor usa `READ_EXTERNAL_STORAGE`.
