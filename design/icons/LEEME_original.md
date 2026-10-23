# SoftMusic — 91 íconos seleccionables en turquesa

Turquesa seleccionado: #2CE4D4. Gris normal: #A8AFBD. Fondo transparente. Cuadrícula 24 × 24. Los 91 íconos originales tienen dos estados; 10 controles principales incluyen una silueta rellena para reforzar la selección. El resto utiliza trazo algo más grueso y turquesa. Los estados son estáticos; la app controla cuándo mostrarlos. Uso personal y comercial permitido; se pueden modificar y no requieren atribución.

## Archivos
- svg/normal: 91 SVG grises, trazo 1.8.
- svg/selected: 91 SVG turquesaes, trazo 2.1 o silueta rellena.
- android/res/drawable: 91 XML normales, 91 seleccionados y 91 selectores.
- Catalogo.html: comparativa de todos los íconos con buscador, funciona sin conexión.
- Vista_previa.png y .svg: muestra comparativa de 24 íconos.

## Android con Views
Copia el contenido de android/res/drawable dentro de app/src/main/res/drawable.
Referencia el selector sin sufijo: @drawable/ic_sm_home o @drawable/ic_sm_favorites.
Activa el estado en el mismo ImageView: iconView.isSelected = true; usa false para desmarcarlo.
Los selectores también admiten state_checked, si el componente propaga ese estado al drawable. El selector no implementa la lógica de selección ni gestiona que solo haya una pestaña activa.
Elimina tint fijos de ImageView/AppCompat que sobrescriban el turquesa o el gris. Cuando un componente controle su propio tint, configura ese tint con los colores de selección.

## Jetpack Compose
Selecciona explícitamente el recurso, ya que painterResource no carga un StateListDrawable:

```kotlin
val resource = if (selected) R.drawable.ic_sm_home_selected else R.drawable.ic_sm_home_normal
Icon(
    painter = painterResource(resource),
    contentDescription = "Inicio",
    tint = Color.Unspecified
)
```

Para NavigationBarItem, pasa selected y utiliza el recurso que corresponda dentro de icon; el contenedor gestiona la semántica de selección. No asignes semantics.selected a un icono decorativo aislado. Conserva una etiqueta accesible en su botón o elemento de navegación y áreas táctiles de al menos 48dp.

## Tema
Los SVG turquesaes y grises son iguales en modo claro y oscuro. Se eligió #2CE4D4 para destacar sobre el fondo negro del mockup. Si tu fondo claro requiere mayor contraste, ajusta el turquesa al integrar. No se incluyen fondos ni indicadores alrededor de los íconos para que se adapten a tus botones existentes.

## Validación
XML de los SVG y VectorDrawable analizado; referencias de los 91 selectores resueltas; ZIP verificado y catálogo renderizado. No se ha compilado ni ejecutado una app Android.
