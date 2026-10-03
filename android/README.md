# GateTunnel VPN - Android Client

Modern, minimal, high-performance Android VPN client automatically discovering the fastest available **VPN Gate** servers in **Japan 🇯🇵** and the **United States 🇺🇸**.

## Architecture Overview

```
UI (Jetpack Compose, Strict Monochrome)
 ↓
VpnViewModel
 ↓
VPN Repository
 ├── VpnGateApiClient (HTTP/HTTPS CSV parser with gzip support)
 ├── ServerRankingEngine (Low-latency & throughput composite scoring)
 ├── VpnConnectionManager (Fallback cascade through top 5 candidates)
 └── VpnDatabase (Room local persistent cache)
```

## Features

- **Automated Server Discovery**: Fetches official live VPN Gate CSV data (`http://www.vpngate.net/api/iphone/`).
- **Selective Filtering**: Filters exclusively to Japan and the United States.
- **Composite Server Ranking Algorithm**:
  $$\text{Score} = \text{latency\_score} + \text{throughput\_score} + \text{uptime\_score} + \text{availability\_score} - \text{load\_penalty}$$
  Prioritizes low ping and high bandwidth over purely theoretical peak numbers, avoiding congested servers.
- **Failover Cascading**: If the primary candidate fails or times out, the app automatically switches to candidate #2, #3, etc., up to 5 candidates without user intervention.
- **Real Android VpnService**: Routes traffic via virtual `tun0` interface (`0.0.0.0/0` and `::/0`), using secure DNS (`1.1.1.1`, `8.8.8.8`) to prevent DNS leaks.
- **Weekly WorkManager Sync**: Automatically scheduled via `PeriodicWorkRequestBuilder<WeeklyVpnSyncWorker>(7, TimeUnit.DAYS)` with network and battery constraints.
- **Modern Minimal Monochrome UI**: Adheres strictly to Black, White, and Grey shades without distracting neon or candy accents. Default Android system font.

## Prerequisites to Build

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34 (compileSdk 34, minSdk 24)

## How to Build the APK

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Release APK
```bash
./gradlew assembleRelease
```
The resulting APK will be at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### 3. Sign the Release APK
```bash
# Generate a keystore if you don't have one
keytool -genkey -v -keystore gatetunnel.keystore -alias gatetunnel -keyalg RSA -keysize 2048 -validity 10000

# Align and sign using apksigner
zipalign -v -p 4 app/build/outputs/apk/release/app-release-unsigned.apk app-release-aligned.apk
apksigner sign --ks gatetunnel.keystore --out GateTunnel-v1.0.0-Release.apk app-release-aligned.apk
```
