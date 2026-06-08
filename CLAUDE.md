# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SmartCharger Dual** is an Android application for dual-channel EV charging stations. It implements OCPP 1.6 WebSocket communication, payment terminal integration, serial hardware control, and RF card reader support.

- Package: `com.dongah.smartcharger`
- Min SDK: 24 (Android 7.0), Target/Compile SDK: 36
- Language: Java (primary) + Kotlin (Compose UI)
- NDK: Serial port native library via `app/src/main/jni/Android.mk`

## Build Commands

Build from Android Studio or via Gradle wrapper in the project root:

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

The release signing config points to a local keystore at `D:\AndroidDongah\PlatformKeyClear\keystore\platform.jks` (platform key, alias `platform`, password `android`). Commented-out configs for `senke` and `hola` hardware variants are in `app/build.gradle`.

## Architecture

### Entry Point

`MainActivity.java` is the single Activity. It initializes all subsystems on startup:
- WebSocket OCPP connection
- Control board serial communication
- RF card reader
- Payment terminal (TECH3800)

Fragment navigation is handled by `basefunction/FragmentChange.java`. UI state transitions drive which fragment is shown.

### Key Packages

**`basefunction/`** — Central business logic
- `GlobalVariables.java` — App-wide state; `maxChannel = 2`, `maxPlugCount = 3`
- `ChargerConfiguration.java` — Persistent config: server URL, auth mode, operation mode (reads/writes to SQLite via `sqlite/SQLiteHelper.java`)
- `ChargingCurrentData.java` — Per-connector real-time charging state
- `ClassUiProcess.java` — UI state machine; drives fragment transitions and charging flow
- `NotifyFaultCheck.java` — Fault detection logic

**`websocket/socket/`** — WebSocket transport
- `Socket.java` — OkHttp3 WebSocket client; handles TLS with BKS keystores (`charging_station_keystore.bks` / `charging_station_truststore.bks`)
- `SocketReceiveMessage.java` — OCPP message dispatcher; maps action names to handler instances
- `SocketState.java` — Connection lifecycle enum: `NONE → OPEN → RECONNECTING → CLOSED`

**`websocket/socket/handler/`** — OCPP handler split:
- `handlersend/` — Background threads that send periodic/triggered OCPP requests (HeartbeatThread, BootNotificationThread, StatusNotificationThread, etc.)
- `handlerreceive/` — Handlers invoked by `SocketReceiveMessage` for inbound OCPP commands (AuthorizeHandler, ResetHandler, ChangeConfigurationHandler, etc.)

**`websocket/ocpp/`** — OCPP 1.6 feature groups:
- `core/` — Core profile: Authorize, Start/StopTransaction, MeterValues, Reset, RemoteStart/Stop
- `firmware/` — Firmware update flow
- `security/` — Certificate operations
- `smartcharging/` — Charging profiles
- `localauthlist/` — Local auth list
- `datatransfer/lselink/` — LSE-Link vendor DataTransfer extensions (payment, battery info, vehicle info, unit price, etc.)
- `datatransfer/dongah/` — DongAh-specific DataTransfer extensions
- `datatransfer/vas/` — VAS (Value-Added Service) extensions

**`TECH3800/`** — Payment terminal protocol over serial
- `TLS3800.java` — Serial packet protocol (RF card read, payment, cancel, IC check)
- `packet/` — Packet structures: `PacketHeader`, `PacketPay`, `PacketPayG`, `PacketPayCancel`

**`controlboard/`** — Hardware control board via serial (CRC16)
- `ControlBoard.java` — Sends commands and receives voltage/current/temperature/status
- `RxData.java` / `TxData.java` — Board data frames

**`pages/`** — UI Fragments (Jetpack Compose + View-based mix)
- Charging flow: `InitFragment` → `MemberCardFragment` → `ChargingFragment` → `ChargingFinishFragment`
- Status: `FaultFragment`, `ScreenSaverFragment`, `ConnectionFailedFragment`
- Admin/debug: `ConfigSettingFragment`, `WebSocketDebugFragment`, `ProductTestFragment`, `EnvironmentFragment`

**`rfcard/`** — RF/NFC card reader with callback listener pattern

**`sqlite/`** — SQLite persistence: `CpSettings` (charger config), `CpNonTransmit` (offline transaction buffer)

**`utils/`** — `CRC16.java`, `FileManagement.java`, `LogDataSave.java`, `MonitorHttpServer.java`

### AIDL Interfaces

Located in `app/src/main/aidl/`:
- `service/vact/smartro/com/vcat/SmartroVCatInterface.aidl` — V-CAT payment service (executeService, postExtraData, cancelService)
- `service/vact/smartro/com/vcat/SmartroVCatCallback.aidl` — V-CAT callbacks (onServiceEvent, onServiceResult)
- `com/dongah/smartcharger/VCatConstructor.java` — AIDL helper

### OCPP Message Flow

1. `Socket.java` receives raw WebSocket frame
2. `SocketReceiveMessage.java` parses OCPP array `[messageType, messageId, action, payload]`
3. For `CALL (2)`: dispatches to the matching `OcppHandler` in `handlerreceive/`
4. For `CALLRESULT (3)`: matched to pending request by `messageId`
5. Outgoing messages: handler threads in `handlersend/` call `SendHashMapObject` → `Socket.java`

### SSL/TLS

BKS keystore files in `app/src/main/res/raw/`:
- `charging_station_keystore.bks` (password: `ecospass`)
- `charging_station_truststore.bks` (password: `trustpass`)

### Reactive/Async Patterns

- **RxJava 3** for FTP downloads (`FtpRxJava.java`) and HTTP operations
- **Android Handler** for posting results back to the main thread
- **Listener/callback interfaces** for ControlBoard, RfCard, and Socket events
- Background threads (not coroutines) for OCPP heartbeat and periodic send loops
