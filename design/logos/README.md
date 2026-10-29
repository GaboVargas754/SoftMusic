# Logotipos de SoftMusic

Los diez recursos `seasonal_logo_*.webp` proceden de los archivos proporcionados por el usuario. Se extrajo técnicamente el fondo oscuro y se normalizó la presentación; no se usó IA, no se redibujaron letras y no se cambiaron los colores de la marca.

El logotipo estándar procede de `originals/Logotipo oficial de SoftMusic.png`. Los nueve temáticos proceden de `originals/SoftMusic_9_Logotipos_Tematicos.zip`. Ambos archivos son copias exactas de los originales; el ZIP conserva los nueve PNG originales completos. `manifest.json` registra SHA-256 de las fuentes, del archivo ZIP y de cada resultado, además de todos los parámetros y las transformaciones aplicadas.

Los originales permanecen fuera de `app/src/main/res`, por lo que no se empaquetan en el APK. Los diez PNG de ejecución ocupaban **12.158.212 bytes**. Los diez WebP ocupan **762.744 bytes**: una reducción del **93,73 %**.

## Recursos de ejecución

- Lienzo común de **840 × 280 px**, relación 3:1, suficiente para el encabezado de hasta 280 dp a densidad 3×.
- RGBA con transparencia real, incluido el interior de la letra `o`; la `S` negra y el símbolo de reproducción del icono mantienen el aspecto original.
- WebP **sin pérdida**: se comprueba que el RGBA decodificado coincide exactamente con el resultado de la extracción y el remuestreo.
- Los nombres `seasonal_logo_*` se conservan, por lo que las referencias `R.drawable` siguen funcionando.
- Se alinean el borde izquierdo del icono y la base del texto. La escala es uniforme en ambos ejes para conservar las proporciones de cada fuente. El encuadre es común y deja espacio a los adornos de todas las temporadas.

## Extracción y alineación

1. Se identifica el contenido usando un máximo de canal RGB superior a 32; el fondo de estas fuentes está muy por debajo de ese valor.
2. Solo en la región del icono (`x < 640`) se cierran pequeñas discontinuidades del contorno y se rellenan sus huecos. Esto conserva los trazos oscuros de la `S`, sombreros, telaraña, audífonos y demás detalles. Los huecos de las letras quedan fuera de esa región y son transparentes.
3. El interior conserva el RGB de la fuente. En el contorno se estima cobertura parcial con una vecindad de siete píxeles y se descuenta el fondo oscuro original, para evitar un borde negro al componer sobre fondos claros. Los píxeles completamente transparentes guardan RGB cero.
4. Se mide la zona blanca `oft`, libre de adornos en las diez fuentes, y se aplica únicamente escala uniforme y traslación. Se normalizan su altura a 227 píxeles de fuente, su base a `y = 464` y el borde izquierdo del icono a `x = 237`. El encuadre común incluye la unión de los diez contornos con margen lateral de 26 píxeles de fuente.
5. Se reduce una sola vez mediante Lanczos a 840 × 280 y se codifica WebP sin pérdida. El PNG de ejecución solo se retira después de comprobar que corresponde a la fuente archivada y que su reemplazo se decodifica correctamente.

La textura propia de las letras y del icono se conserva. La palabra blanca `Soft` conserva el color del original, por lo que su contraste depende del fondo sobre el que se presente. El procesamiento no crea versiones alternativas para fondos claros.

## Reproducción

Desde la raíz del repositorio:

```sh
python3 -m venv /tmp/softmusic-logos
/tmp/softmusic-logos/bin/pip install Pillow==13.0.0 numpy==2.5.4
/tmp/softmusic-logos/bin/python tools/prepare_seasonal_logos.py
```

El script admite `--sources`, `--output`, `--manifest` y `--previews` para generar en otra carpeta sin tocar los recursos del proyecto. El manifiesto incluye las versiones de Pillow y numpy utilizadas.

## Revisión

`previews/all-light-dark.jpg` muestra los diez resultados compuestos sobre fondos claro y oscuro. También se incluyen vistas individuales del original, Halloween, Día de Muertos y Aniversario. Se revisaron especialmente los trazos negros del icono, la telaraña, los sombreros, las flores, la calavera y el pastel.

Se verificaron los diez archivos decodificados: dimensiones, canales RGBA, alfa entre 0 y 255, márgenes transparentes, coincidencia de hashes del manifiesto y ausencia de PNG duplicados en los recursos de ejecución.
