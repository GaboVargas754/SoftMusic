# SoftMusic — acciones V2 integradas

Paquete de origen: `SoftMusic_49_Acciones_y_Seleccionables.zip`, carpeta
`SoftMusic_Acciones_V2`. Se conservan los 49 identificadores y todos sus vectores
sin modificar rutas, colores, trazos ni dimensiones.

## Recursos de ejecución

Los 98 `VectorDrawable` están en `app/src/main/res/drawable/`:

- `ic_sm_action_*_normal.xml`: 49 originales de
  `android/res/drawable-night/`, con gris `#A8AFBD`.
- `ic_sm_action_*_selected.xml`: 49 originales de
  `android/res/drawable/`, con turquesa `#2CE4D4`.

Los normales oscuros se instalan en `drawable/` porque SoftMusic mantiene su tema
oscuro aunque el dispositivo use modo claro. Así, el recurso normal conserva el
contraste de la interfaz en ambos modos del sistema.

`MusicActionIcons.kt` ofrece 98 getters `ImageVector`, dos por identificador,
como `Rename`/`RenameSelected`, `SelectSongs`/`SelectSongsSelected` y
`SortCustom`/`SortCustomSelected`. Cada getter carga un vector individual con
`ImageVector.vectorResource`. El llamador elige la variante normal o `Selected`
y la pasa en el parámetro `icon` del componente compartido
`MusicActionIconButton`. El botón recibe `selected: Boolean?`: `null` para
comandos puntuales y `true`/`false` para estados persistentes, con su semántica
accesible. Aplica `colorScheme.primary` (`#2CE4D4`) cuando `selected == true` y
`colorScheme.onSurfaceVariant` (`#A8AFBD`) en los demás casos. Cuando está
deshabilitado, aplica el gris con alfa 0.38. Estos colores del tema coinciden con
los del paquete; los vectores originales permanecen intactos.

El icono usa una cuadrícula de 24 × 24 y tamaño de 24dp; el objetivo táctil mínimo
es de 48 × 48dp. El turquesa representa un estado persistente de selección u orden.
Acciones puntuales como renombrar, guardar o quitar permanecen en su estado
normal. Las acciones frecuentes se muestran en la cabecera y las secundarias en
menús con texto e icono. Las destructivas requieren confirmación. Quitar de una
playlist y borrar un archivo son acciones distintas.

## Fuentes y catálogo

| Archivo o carpeta | Contenido |
| --- | --- |
| [manifest.json](manifest.json) | 49 IDs, etiquetas, grupos y rutas originales |
| [LEEME.md](LEEME.md) | Documento original y permisos de uso |
| [Catalogo.html](Catalogo.html) | Catálogo completo sin conexión |
| [Vista_previa.png](Vista_previa.png) y [Vista_previa.svg](Vista_previa.svg) | Vistas originales del conjunto |
| `svg/normal_dark/` | 49 originales grises para fondo oscuro |
| `svg/normal_light/` | 49 originales grises para fondo claro, archivados como fuente |
| `svg/selected/` | 49 originales turquesa |

El documento original explica una instalación genérica para Android Views y dos
temas. La integración de SoftMusic aplica el [estándar compartido](../README.md):
tema oscuro estable, selección explícita y vectores individuales. Se omiten los
49 selectores `StateListDrawable` y los normales claros del runtime. Los recursos
Android se almacenan únicamente en la aplicación.

Los 49 diseños constituyen un catálogo disponible. Solo se conectan a acciones
que la aplicación ya puede ejecutar.
