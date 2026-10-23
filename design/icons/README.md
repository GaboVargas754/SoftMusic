# SoftMusic — 91 íconos con estados normal y seleccionado

Familia para la interfaz oscura de SoftMusic: gris `#A8AFBD` en estado normal y
turquesa `#2CE4D4` en estado seleccionado. Los fondos son transparentes. Se permite
el uso personal y comercial, la modificación y el uso sin atribución.

Cada diseño incluye dos variantes explícitas. Diez controles principales usan
una silueta rellena al seleccionarse; los demás usan un trazo turquesa más grueso.
La aplicación decide qué variante mostrar según el estado real del control.

## Recursos integrados

- `../../app/src/main/res/drawable/ic_sm_*_normal.xml`: 91 VectorDrawable grises.
- `../../app/src/main/res/drawable/ic_sm_*_selected.xml`: 91 VectorDrawable turquesa.
- `svg/normal/` y `svg/selected/`: los 182 originales SVG con sus colores explícitos.
- `manifest.json`: los 91 identificadores, etiquetas, colores y estilos seleccionados.
- `Catalogo.html`: catálogo completo sin conexión con búsqueda y comparación de estados.
- `Vista_previa.png` y `Vista_previa.svg`: comparación de 24 diseños del paquete.
- `LEEME_original.md`: documento de entrega conservado con los permisos originales.

La familia se integra en `drawable/` para la interfaz oscura de la aplicación.
Las antiguas variantes claras de `drawable/`, las oscuras de `drawable-night/` y
los 91 SVG con `currentColor` se reemplazaron por esta familia de dos estados.
El documento original también describe selectores para Android Views; la
integración de SoftMusic usa los vectores individuales indicados aquí.

## Compose: elegir el estado explícitamente

`MusicIcons.kt` conserva los 35 getters utilizados por la app para la variante
normal y añade un getter `Selected` para cada uno, por ejemplo `Home` y
`HomeSelected`. Ambos devuelven `ImageVector.vectorResource`.

```kotlin
NavigationBarItem(
    selected = selected,
    onClick = onClick,
    icon = {
        Icon(
            imageVector = if (selected) MusicIcons.HomeSelected else MusicIcons.Home,
            contentDescription = null,
            tint = Color.Unspecified,
        )
    },
    label = { Text("Inicio") },
)
```

`Color.Unspecified` conserva el gris y el turquesa incluidos en el vector.
Los controles que necesitan contraste con un botón relleno o con el fondo de una
portada pueden aplicar su color de contenido. La forma del estado seleccionado
se obtiene eligiendo su recurso `Selected`.

Los otros 56 diseños también están disponibles mediante sus dos recursos:

```kotlin
val resource = if (selected) R.drawable.ic_sm_artist_selected
    else R.drawable.ic_sm_artist_normal
val image = ImageVector.vectorResource(resource)
```

Los selectores `StateListDrawable` del paquete se omitieron porque
`ImageVector.vectorResource` carga vectores individuales. La selección accesible
corresponde al botón, pestaña o elemento de navegación. Conserva una etiqueta
accesible para cada acción y áreas táctiles de al menos 48dp.

## Geometría y dirección

Cuadrícula de 24 × 24 y tamaño de 24dp. La variante normal usa trazo redondeado de
1.8; la seleccionada usa trazo de 2.1 o la silueta rellena entregada. Las rutas,
rellenos y colores conservan la geometría del paquete. Ambos vectores `back`
añaden `android:autoMirrored="true"` para respetar la dirección RTL.

## Migración del paquete

| Archivos de entrega | Cantidad | Destino |
| --- | ---: | --- |
| Vectores normales y seleccionados | 182 | `app/src/main/res/drawable/` |
| SVG normales y seleccionados | 182 | `design/icons/svg/normal/` y `svg/selected/` |
| Catálogo HTML y vistas PNG/SVG | 3 | `design/icons/` |
| Manifest | 1 | `design/icons/manifest.json` |
| Documento y permisos originales | 1 | `design/icons/LEEME_original.md` |
| Selectores para Views omitidos | 91 | Sin incorporar |

De los 460 archivos entregados, 369 se reubicaron y 91 selectores se omitieron.
Las carpetas de entrega se retiraron después de validar la migración. El launcher
y el icono de notificación del panel web conservan sus recursos independientes.
