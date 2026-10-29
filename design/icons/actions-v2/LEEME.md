# SoftMusic — Acciones V2

49 diseños para acciones de canciones y playlists. Este paquete amplía los íconos de interfaz; no incluye íconos de la aplicación ni versiones festivas.

## Variantes
- `svg/normal_dark`: gris #A8AFBD para interfaces oscuras.
- `svg/normal_light`: gris #374151 para interfaces claras.
- `svg/selected`: turquesa #2CE4D4 seleccionado/activo.

Fondos transparentes, cuadrícula 24 × 24, trazo redondeado 1.8 normal y 2.1 seleccionado. Cada acción mantiene su significado y geometría al seleccionarse. Los íconos de selección contienen marcas de verificación como parte de su dibujo; el estado azul indica la activación adicional. No toda acción necesita selección persistente: renombrar o guardar suelen ser comandos puntuales, mientras que seleccionar y ordenar sí pueden mantener un estado activo.

## Android
Copia las carpetas `android/res/drawable` y `android/res/drawable-night` a las equivalentes de tu app. Los recursos usan el prefijo `ic_sm_action_` para evitar colisiones con el paquete anterior.

En Views, usa el selector sin sufijo: `@drawable/ic_sm_action_rename` y activa `imageView.isSelected = true`. También se admite `state_checked` si tu componente lo propaga. Quita tint fijos que sobrescriban los colores. Android elegirá el recurso normal adecuado según el modo claro/oscuro.

En Compose usa los vectores directamente:

```kotlin
val resource = if (selected) R.drawable.ic_sm_action_rename_selected
               else R.drawable.ic_sm_action_rename_normal
Icon(painterResource(resource), contentDescription = "Renombrar", tint = Color.Unspecified)
```

La aplicación debe gestionar selección, estado y acciones. No uses painterResource con el selector XML. Mantén objetivos táctiles de al menos 48dp y etiquetas accesibles en los botones.

Abre `Catalogo.html` para ver todos los pares y buscar acciones sin conexión. `Vista_previa.png` permite comparar todo el set.

Uso personal y comercial permitido, modificable sin atribución. XML analizado, referencias de selectores comprobadas, vista previa renderizada y ZIP verificado. No se compiló una app Android.
