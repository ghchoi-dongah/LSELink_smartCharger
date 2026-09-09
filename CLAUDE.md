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

## Recent Changes (feature/error_260903)

### DT(MeterValues) accWh / accTickWh 보정 — `MeterValuesReq` + `MeterValuesStopReq`

**문제**: `meterStop - meterStart` 값(Wh)과 DT(MeterValues)의 `accTickWh` 합산이 불일치. 원인은 첫 번째 DT 전송 시 `prevPowerMeter = -1`이어서 `accTickWh = 0.0`으로 전송되고, `meterStart`와 첫 보드 계측값 사이의 구간 사용량이 누락되었기 때문.

**수정 내용**:

#### `MeterValuesReq.java` (`handlersend/MeterValuesReq.java`)
- `totalSentPowerMeterDiff` 필드 추가: 매 전송 시 `diffPowerMeter` 누적
- `getTotalSentPowerMeterDiff()` / `getPrevPowerMeter()` getter 추가
- `firstSend` 플래그 도입 (`prevPowerMeter < 0` 여부로 판단):
  - 첫 DT: `accWh = powerMeterStart * 0.001` (meterStart 기준), `accTickWh = 0.0`
  - 첫 DT의 `prevPowerMeter`는 `powerMeterStart`로 저장 (보드값이 아님) → 두 번째 DT의 `accTickWh`가 meterStart 기준 차이로 계산됨
  - 이후 DT: `accWh = boardMeter * 0.001`, `accTickWh = 실제 보드값 차이`

#### `MeterValuesStopReq.java` (`handlersend/MeterValuesStopReq.java`) — 신규 파일
- 충전 종료 시 한 번만 호출되는 최종 DT(MeterValues) 전송 클래스
- `sendMeterValuesStop(MeterValuesReq meterValuesReq)`:
  - `remainingWh = (currentMeter - powerMeterStart) - totalSentPowerMeterDiff`
  - `accTickWh = remainingWh * 0.001`
  - `accWh = (prevPowerMeter + remainingWh) * 0.001` → 이전 DT의 accWh + accTickWh와 일치
  - 소켓 단절 시 `LogDataSave`로 dump 저장

#### `ClassUiProcess.java` (`basefunction/ClassUiProcess.java`)
- 충전 종료 경로 2곳에서 `meterValuesReq.sendMeterValues()` → `new MeterValuesStopReq(...).sendMeterValuesStop(meterValuesReq)`로 교체
  - `handleFinishWait()` 내부 (정상 종료)
  - fault/emergency 정지 경로

#### `StopTransactionReq.java` (`handlersend/StopTransactionReq.java`)
- `energy.setValue`: `String.valueOf(...)` → `String.format("%.3f", ...)` 로 변경하여 부동소수점 표현 오류(`1.1500000000000001`) 방지

**설계 원칙**:
- 첫 DT `accTickWh = 0.0` 유지 (서버 스펙)
- `accWh`와 `accTickWh` 내부 일치: `prev accWh + accTickWh = current accWh`
- `sum(accTickWh) = (meterStop - meterStart) / 1000` 보장 (MeterValuesStopReq가 나머지 보정)
