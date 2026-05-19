# Door Sound

Plays custom audio files through cabin speakers and preset tone patterns through the AVAS external speaker when door/lock events occur.

## Features

- 4 events: door open, door close, lock, unlock
- **Inside speaker**: custom audio files (OGG/MP3/WAV) with per-event volume control (0-15)
- **Outside speaker (AVAS)**: 5 preset tone patterns (ding-dong, triple beep, rapid alt, long chime, etc.)
- Auto-start on boot
- Foreground service for reliable background operation

## Build Prerequisites

| Tool | Source |
|------|--------|
| `android.jar` (API 29) | Android SDK or `sdkmanager "platforms;android-29"` |
| `aapt2` | Android SDK build-tools |
| `javac` | JDK 11+ |
| `d8` | Android SDK build-tools |
| `apksigner` | Android SDK build-tools |
| `keytool` | JDK |

## Build

```bash
./build.sh
```

Update the path to `android.jar` in the script if needed (default: `/tmp/android-10/android.jar`).

## Install

1. Connect via ADB: `adb connect <head-unit-ip>:5555`
2. Install: `adb install build/door-sound.apk`
3. Open app, select audio files, enable events
4. Whitelist in BYD auto-start manager for persistent background operation

## How It Works

- Listens for `BYDAutoBodyworkDevice` events via CAN bus (door open/close, remote lock/unlock)
- **Inside sounds**: `MediaPlayer` on `STREAM_MUSIC` plays user-selected audio files through cabin speakers
- **Outside sounds**: CAN bus commands to AVAS (factory test signals repurposed as tone patterns)
- Uses `BydPermissionContext` to handle BYD-specific permission checks

## Limitations

- Cannot replace the BCM-generated lock/unlock chirp (hardware limitation — BCM generates the sound directly)
- AVAS external speaker only supports preset tone patterns, not custom audio (MCU firmware blocks custom routing)
- AVAS volume is fixed by MCU firmware
- Requires rooted head unit for sideloading

## Architecture

The app compiles against stub interfaces (`stubs/`) that match BYD's internal HAL (`android.hardware.bydauto`). These stubs define the API surface — method signatures, constants, and listener interfaces — without any implementation. At runtime on the vehicle's head unit, the stubs are not loaded; the real system services provide the actual implementations. This allows building the app on any development machine without access to BYD's proprietary framework JARs.
