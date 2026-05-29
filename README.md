# Fulbito14 ARG

**App Nativa Android TV** para streaming de deportes en vivo.

## Especificaciones

- **Package:** `com.fulbito14.arg`
- **Min SDK:** 21 (Android 5.0)
- **Target SDK:** 36
- **Lenguaje:** Java
- **Streaming:** ExoPlayer 2.19.1 (HLS/M3U8 nativo)
- **UI:** Android TV Leanback + Custom Views
- **Networking:** OkHttp 4.12.0

## Login

- **Usuario:** `limonsin14`
- **Contraseña:** `1276`

## Canales (20)

ESPN, ESPN 2-7, ESPN Premium, Fox Sports, Fox Sports 2-3, Fox Sports Premium, DSports, DSports+, TNT Sports, TyC Sports, Win Sports, Win Sports+, Win Sports+ Premium, TUDN

## Cómo funciona

1. La app carga páginas embed de `la12hd.com` / `la14hd.com`
2. Extrae el URL M3U8 dinámico (con token IP-bound) usando OkHttp
3. Pasa el Referer header correcto a ExoPlayer
4. ExoPlayer reproduce el stream HLS nativamente

## Firma APK

- **V1 (JAR signing):** ✅
- **V2 (APK Signature Scheme v2):** ✅
- **V3 (APK Signature Scheme v3):** ✅

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleRelease
```

## Estructura del Proyecto

```
app/src/main/java/com/fulbito14/arg/
├── LoginActivity.java        # Pantalla de login
├── ChannelListActivity.java  # Lista de canales
├── PlayerActivity.java       # Reproductor ExoPlayer
├── Channel.java              # Modelo de canal
├── ChannelData.java          # Datos de canales
└── M3U8Extractor.java        # Extractor de M3U8 con Referer tracking
```
