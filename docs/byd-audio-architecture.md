# BYD Audio Architecture

## Audio Hardware Topology

```
                                    +--------------------------------------+
                                    |              MCU DSP                 |
  SoC (SM6125)                      |                                      |
  +----------+    I2S    +-----+    |    +---------------------+           |
  | Qualcomm |--------->| MCU |----|--->|     A2B Bus         |           |
  |  ADSP    |           |     |    |    |  (Analog Devices     |           |
  +----------+           |     |    |    |   Automotive Audio)  |           |
       ^                 +--+--+    |    +--+----------+-------+           |
       |                    |       |       |          |                    |
  SPI (/dev/spidev_ivi)     |       |       v          v                   |
       |                    |       |   Main Amp    AVAS Amp               |
  +----+-----+              |       |       |          |                   |
  |BYDAuto   |   CAN bus    |       |       v          v                   |
  |Manager   |<-------------+       |   Cabin      External               |
  |(libbyd   |  commands            |   Speakers   Speaker                 |
  | auto.so) |                      |              (pedestrian)            |
  +----------+                      +--------------------------------------+
```

The SoC sends audio data to the MCU via I2S. The MCU's DSP is the master of the
A2B (Analog Devices Automotive Audio Bus) and decides all routing to speakers.
There is NO direct audio path from the SoC to the AVAS speaker -- it must go
through the MCU's DSP.

## CAN Bus Signal Flow

```
Android App -> DiCarServer -> BYDAutoManager (JNI -> libbydauto.so) -> SPI -> MCU -> A2B -> Amplifiers
```

DiCarServer uses two ID systems:
- **Framework IDs** (`BYDAutoFeatureIds.Audio`): computed at runtime from `isCanFD`/`isToyota` flags. Used by `BYDAutoManager.getInt/setInt()`.
- **Hex string IDs** (`com.byd.feature.audio.Audio`): hardcoded hex strings. `AudioMapper.transformFeatureId()` tries framework lookup first, falls back to `Integer.valueOf(hex, 16)`.

### Signal Direction Convention

| Prefix | Direction | Purpose |
|--------|-----------|---------|
| 0x99 | MCU -> SOC | Read / event notification |
| 0xAA | SOC -> MCU | Write / command |
| 0x1B, 0x1C, 0x32 | SOC -> MCU | Audio control writes |
| 0x4C, 0x4F, 0x35 | MCU -> SOC | Audio status reads |
| 0x48 | MCU -> SOC | Engine device reads |
| 0x3E | SOC -> MCU | Engine device writes |

## AVAS Signals

| Signal | Hex ID | Device | R/W | MCU Result | Notes |
|--------|--------|--------|-----|------------|-------|
| AVAS_SOUND_SOURCE_STATE | 0x4C60002D | 1002 | R | OK | Current preset readback |
| AVAS_SOUND_SOURCE_SET_SET | 0x1B10003D | 1002 | W | SUCCESS | Accepts 0-5+ (UI shows 2) |
| AVAS_SOURCE_TYPE | 0x99000162 | 1002 | R | OK | Source type readback |
| AVAS_FAULT_STATUS | 0x35201042 | 1002 | R | -10011 | |
| AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_SET | 0x32B1C042 | 1002 | W | FAILED | MCU rejects routing |
| AVAS_AUDIO_SOURCE_TO_EXTERNAL_SPEAKER_STATUS | 0x35203032 | 1002 | R | -10011 | |

### Sound Source Channels

The DSP manages parallel channels, each with a SET/STATE signal pair. All channels select MCU-stored presets -- none routes SoC audio:

| Channel | SET Signal | STATE Signal |
|---------|-----------|-------------|
| Media | 0x1B10001C | 0x4C60000C |
| Radar | 0x1B100025 | 0x4C600015 |
| Navigation | 0x1B10002D | 0x4C60001D |
| ANC | 0x1B100035 | 0x4C600025 |
| AVAS | 0x1B10003D | 0x4C60002D |
| INS | 0x1B100045 | 0x4C600035 |
| BD | 0x1C100026 | 0x4FD0001E |

## AVAH Test Tones

Factory diagnostic signals that produce audible tones on the AVAS external speaker:

| Signal | Hex ID | Device | R/W | MCU Result | Notes |
|--------|--------|--------|-----|------------|-------|
| TEST_CMD_TEST_AUDIO_AVAH | 0x6EA70010 | 1002 | R | OK | Returns 65535 (0xFFFF) |
| TEST_CMD_TEST_AUDIO_AVAH_SET | 0x6E970010 | 1002 | W | SUCCESS | 0=stop, 1=1kHz, 2=2kHz, 3=3kHz |

Prerequisite: AVAS must be enabled in Vehicle Settings > Notification. Tone is continuous until stopped with value 0.

### AVAH Enabler Commands

After MCU configuration has been altered by diagnostic probing, AVAH may require
factory test commands to be set before producing audible output:

| Command | Feature ID | Enable Value | Purpose |
|---------|-----------|-------------|---------|
| TEST_PA_CONTROL_SET | 0xAA000148 | 1 | Power amplifier for diagnostic path |
| TEST_MCU_SPEAK_SET | 0xAA000142 | 1 | MCU speaker test mode |
| TEST_FM_SPEAK_SET | 0xAA00011A | 1 | FM speaker path |
| TEST_AUDIO_AVAS_SET | 0xAA000104 | 1 | AVAS test audio |
| TEST_MCU_AVAS_CONFIGURATION_SET | 0xAA000171 | 1 | AVAS test mode config |
| AUDIO_CHANNLE_WITH_MUTE_STATE_SET | 0xAA00011E | 0 | Unmute channel |

**Start sequence** (plays tone on AVAS external speaker):
```java
setInt(1002, 0xAA000148, 1);  // PA on
setInt(1002, 0xAA000142, 1);  // MCU speak on
setInt(1002, 0xAA00011A, 1);  // FM speak on
setInt(1002, 0xAA000104, 1);  // Test AVAS on
setInt(1002, 0xAA000171, 1);  // AVAS config on
setInt(1002, 0xAA00011E, 0);  // Unmute
Thread.sleep(100);
setInt(1002, 0x6E970010, 1);  // AVAH tone on
```

**Stop sequence** (disable enablers FIRST, then AVAH):
```java
setInt(1002, 0xAA000148, 0);  // PA off
setInt(1002, 0xAA000142, 0);  // MCU speak off
setInt(1002, 0xAA00011A, 0);  // FM speak off
setInt(1002, 0xAA000104, 0);  // Test AVAS off
setInt(1002, 0xAA000171, 0);  // AVAS config off
setInt(1002, 0x6E970010, 0);  // AVAH tone off
```

**Warning**: Setting AVAH=0 WITHOUT disabling enablers first causes the tone to
get stuck -- can only be stopped by toggling AVAS off/on in Vehicle Settings.

## Dual I2S Architecture

The SoC has two I2S output buses to the MCU:

```
SoC (SM6125)                    MCU DSP
+-- TERT_MI2S_RX (MultiMedia1) --> Main Audio Mix --> A2B --> Cabin Speakers
+-- QUAT_MI2S_RX (MultiMedia2) --> Navigation Mix --> A2B --> Cabin Speakers (ONLY)
+-- CAN/SPI ---------------------> Routing Control + AVAH Tone Generator
```

The MCU DSP has a hard separation between I2S audio input (routed only to cabin
speakers) and the AVAH internal tone generator (routed to AVAS speaker). No
combination of CAN commands changes this routing. Custom audio on the AVAS speaker
requires MCU firmware modification.

## AVAS Volume

The AVAS speaker volume is hardcoded in MCU firmware and cannot be changed from
the SoC. CAN bus signals control on/off and tone selection only, not amplitude.

## MCU Return Codes

| Value | Meaning |
|-------|---------|
| 0 | SUCCESS |
| -10011 | Feature not registered (write-only signals return this on read) |
| -10013 | Feature not available |
| -2147482648 | BYDAUTO_COMMAND_RESULT_FAILED (MCU rejects) |
| -2147482647 | BYDAUTO_COMMAND_RESULT_BUSY |
| -2147482646 | BYDAUTO_COMMAND_RESULT_TIMEOUT |
| -2147482645 | BYDAUTO_COMMAND_RESULT_INVALID_VALUE |
