# Engine Sound

Simula sonido de motor a combustión modulando pitch y volumen de un loop de
audio según la velocidad GPS del vehículo (no según el pedal real — ver
limitaciones).

## Cómo funciona

- Un `Foreground Service` se suscribe a actualizaciones de `LocationManager`
  cada 200ms.
- La velocidad instantánea (`Location.getSpeed()`) se suaviza con un filtro
  exponencial para que el motor no "tartamudee" con el ruido normal del GPS.
- Se mapea velocidad → pitch y velocidad → volumen de forma lineal entre los
  valores calibrables en la pantalla principal.
- Si detecta una aceleración brusca (>8 km/h por segundo), agrega un "kick"
  temporal de pitch por 350ms para simular el efecto de pisar fondo o bajar
  un cambio.
- Todo esto se reproduce en loop con `MediaPlayer` + `PlaybackParams` (cambio
  de pitch en caliente, requiere Android 6+, el DiLink target tiene Android
  10 así que sobra margen).

## Limitaciones (importante leer)

- **No lee el pedal real.** No existe (todavía) ninguna API reverseada de
  BYD que expongas la posición del acelerador — el
  `research/byd-auto-api-reference.md` del repo solo documenta AC, cierre de
  puertas, panorama, carrocería y nube. Esto es un proxy por velocidad GPS,
  no telemetría real del auto.
- **Solo suena por los parlantes internos.** El parlante exterior (AVAS)
  está bloqueado por firmware del MCU para audio custom — mismo límite
  documentado en `apps/door-sound`. No hay forma de rutear esto hacia afuera
  sin reversear el protocolo CAN del AVAS a fondo.
- El GPS tarda en tener señal sólida; en cocheras/túneles puede no actualizar
  velocidad por unos segundos (el motor se queda sonando al último valor
  conocido).
- `Location.getSpeed()` no siempre está disponible en todos los choques de
  GPS — si tu head unit no lo reporta, este prototipo no calcula velocidad
  por diferencia de posición (se podría agregar si hace falta).

## Audio placeholder

El repo incluye `src/main/res/raw/engine_loop.ogg`, un drone sintético de
dos tonos (90Hz + 180Hz) generado para que el build no falle por falta de
recurso. **Reemplazalo** por un sample real de motor en loop seamless antes
de usarlo en serio — buscá "engine idle loop seamless" en bancos de sonido
libres, o grabá un motor a combustión en ralentí y recortalo a un loop sin
clicks en el punto de unión.

Formatos soportados: OGG, MP3, WAV. Mantené el mismo nombre de archivo
(`engine_loop`) o actualizá la referencia `R.raw.engine_loop` en
`EngineSoundService.java`.

## Calibración

Desde la pantalla principal:

- **Velocidad máxima de referencia**: a qué velocidad se alcanza el pitch
  máximo (default 120 km/h).
- **Pitch máximo**: qué tan agudo suena el motor a esa velocidad (default
  1.6x, o sea 60% más agudo que el loop original).
- **Volumen base**: volumen en ralentí/parado (default 30%). El volumen
  máximo está fijo en 100%.

## Troubleshooting: "no detecta la velocidad"

Esto pasaba por tres motivos posibles, ya corregidos en el código:

1. **`Location.hasSpeed()` devolvía `false`** en tu head unit. No todos los
   chips GPS automotrices llenan ese campo de forma confiable (depende del
   firmware del GNSS). El service ahora calcula velocidad como fallback por
   `distancia/tiempo` entre dos fixes consecutivos cuando el campo speed no
   viene poblado — en la práctica este fallback es el que termina haciendo
   el trabajo real en la mayoría de los head units.
2. **No había feedback visual.** El campo de velocidad en la pantalla
   principal ahora se actualiza en vivo (con la fuente del dato: `doppler` o
   `distancia/tiempo`), así podés confirmar si está llegando algo o no sin
   mirar logcat.
3. **Permiso o GPS del sistema apagado, fallando en silencio.** Ahora la
   app te avisa con un Toast y te lleva directo a Ajustes > Ubicación si
   está desactivada, y el service loguea explícitamente si falta el permiso.

Si después de esto sigue sin andar, conectá por ADB y mirá el log en vivo
mientras manejás:

```bash
adb logcat -s EngineSoundService
```

Vas a ver líneas como:
```
D EngineSoundService: speed=23.4km/h source=distancia/tiempo acc=12m hasSpeed=false
```

Si no aparece nada ni con el auto en movimiento, lo más probable es que:
- El permiso de ubicación no está concedido (revisalo en Ajustes > Apps).
- El head unit tiene la ubicación del sistema completamente apagada a nivel
  Android (no solo en la app de mapas).
- El GPS interno del DiLink no expone un provider estándar de Android — en
  ese caso habría que investigar si BYD tiene su propio HAL de ubicación
  (similar al caso de AVAS), lo cual sería tema de otro reversing.

## Build

```bash
./build.sh
```

Requiere las mismas herramientas que `door-sound`: `android.jar` (API 29),
`aapt2`, `javac` (JDK 11+), `d8`, `apksigner`, `keytool`. Ver el README raíz
del repo para el setup completo (WSL/Linux/GitHub Actions).

## Install

1. Conectar por ADB: `adb connect <head-unit-ip>:5555`
2. Instalar: `adb install build/engine-sound.apk`
3. Abrir la app, dar permiso de ubicación cuando lo pida
4. Tocar "Iniciar"
5. Si querés que arranque solo con el auto, tildá "Iniciar con el auto" y
   whitelistealo en el administrador de auto-inicio de BYD (Settings > Apps
   > Auto-start management > Engine Sound)

## Licencia

MIT, mismo criterio que el resto del repo.
