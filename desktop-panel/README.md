# SoftMusic Desktop Panel

Panel HTML para controlar SoftMusic Android desde un navegador en desktop.

## Objetivo

- Mantener Android como fuente principal de biblioteca y reproduccion.
- Mostrar estado de reproduccion, cola, favoritos y playlists.
- Enviar comandos al telefono: reproducir, pausar, anterior, siguiente y buscar.
- Funcionar en red local, sin servidor externo.

## Estado Actual

El panel funciona contra el servidor local de Android. La primera pantalla pide host y token; cuando `/api/state` responde correctamente, se abre el reproductor remoto.

## Estructura

- `index.html`: estructura del panel.
- `styles.css`: estilos visuales.
- `app.js`: flujo de conexion, polling de estado y comandos remotos.

## Ejecucion Local

Puedes abrir `index.html` directamente en el navegador para probar la interfaz, o abrir la URL mostrada por Android.

Flujo esperado:

1. Activar "Panel web" en la app Android.
2. Escanear un QR o copiar la URL local.
3. Abrir esa URL desde el navegador del desktop.
4. Controlar la reproduccion del telefono desde el panel.
