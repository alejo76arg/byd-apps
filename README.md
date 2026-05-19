# BYD Apps

Open-source Android apps for BYD electric vehicles.

Built on reverse-engineered BYD APIs — these apps interact directly with the vehicle's CAN bus through BYD's internal Android services. Not available on Google Play.

## Apps

| App | Description | Status |
|-----|-------------|--------|
| [Door Sound](apps/door-sound/) | Custom sounds on door open/close/lock/unlock events | Ready |

## Compatibility

| Property | Value |
|----------|-------|
| Vehicles | BYD Dolphin 25/26 (likely works on other DiLink 3 models) |
| Platform | DiLink 3.0 (global version) |
| Android | 10 (API 29) |
| Architecture | ARM64 (Qualcomm QCM6125) |

## Installing Apps on Your BYD

No root required. Two methods:

### USB Drive (Easiest)

1. Create a folder named `Third Party Apps 55` on a USB drive
2. Copy the APK into that folder
3. Plug USB into the car
4. Enter password: `BYD6125F`
5. Tap the APK to install

### ADB over WiFi

Requires enabling USB debugging first — see the full [Sideloading Guide](https://github.com/wheregoes/byd-dolphin-hacking/blob/master/docs/sideloading-guide.md).

```bash
adb connect 192.168.10.10:5555
adb install build/door-sound.apk
```

## Building from Source

Each app has its own build instructions in its README. General requirements:

- JDK 11+
- Android SDK build-tools (`aapt2`, `d8`, `apksigner`)
- `android.jar` for API 29

## Research

These apps are based on findings from the [byd-dolphin-hacking](https://github.com/wheregoes/byd-dolphin-hacking) research repo — CAN bus protocol documentation, BYD API reverse engineering, and audio architecture analysis.

## License

MIT
