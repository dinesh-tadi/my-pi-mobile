# Argon Mobile Hub v2.1 (Android)

Native Kotlin & Jetpack Compose Android companion client for the Raspberry Pi homelab (`argon-host`), featuring dual-homed network discovery, live foreground heartbeat monitoring, and over-the-air (OTA) updates.

---

## 🏛️ Mobile Architecture & Connection Pipeline

```mermaid
flowchart TD
    subgraph UI["📱 UI Presentation Layer (Jetpack Compose)"]
        TOPBAR["TopAppBar\n• Live Latency Badge (e.g. 22ms)\n• Live 1s Heartbeat Ticker (e.g. 3s ago)\n• Connection Inspector Modal"]
        CONTENT["Hub Content\n• Native Services Grid\n• Embedded Dashboard HUD (WebView)\n• PiDowntimeCard / Tailscale Launcher\n• OtaUpdateCard (Install Now)"]
    end

    subgraph State["🧠 Client State & Managers"]
        NM["NetworkManager\n(10s Foreground Pulse Loop)"]
        OTA["OtaManager\n(HTTP :5050 version.json)"]
        AUTH["AuthManager\n(JWT Session & RBAC)"]
    end

    subgraph Transport["🌐 Dual-Homed Network Routing"]
        LAN["Home Wi-Fi (192.168.1.10)\nFast 400ms Probe"]
        TS["Tailscale Mesh (100.123.87.122)\nEncrypted WireGuard 1200ms Probe"]
        DNS["MagicDNS (argon-host)\n600ms Probe"]
    end

    subgraph Server["🍓 Argon Pi Endpoints"]
        AUTH_EP["Argon Auth (:8089)\n• /api/health (Uptime & Host)\n• /api/auth (Login & Google OIDC)"]
        OTA_EP["Caddy OTA Server (:5050)\n• /version.json\n• /argon-hub.apk"]
        SERVICES["17 Homelab Microservices\n(Vaultwarden, Kuma, Syncthing, etc.)"]
    end

    TOPBAR --> NM
    CONTENT --> NM
    CONTENT --> OTA
    CONTENT --> AUTH

    NM -->|"Probe"| LAN
    NM -->|"Probe"| TS
    NM -->|"Probe"| DNS

    LAN --> AUTH_EP
    TS --> AUTH_EP
    LAN --> OTA_EP
    TS --> OTA_EP
    LAN --> SERVICES
    TS --> SERVICES
```

---

## 💓 Active Foreground Heartbeat State Machine

To provide instant visibility when the Pi reboots or loses power without battery drain, `NetworkManager` runs an active 10-second heartbeat while the app is in the foreground (pausing when backgrounded).

```mermaid
stateDiagram-v2
    [*] --> Checking: onResume() / App Opened

    state Checking {
        [*] --> TestLAN: Concurrent Probe (400ms)
        TestLAN --> TestTS: Timeout / Off-LAN
        TestTS --> TestDNS: 100.123.87.122 (1200ms)
    }

    Checking --> Connected: Socket Responded
    Connected --> Checking: 10-Second Timer

    Connected --> Degraded: 10s-30s No Response
    note right of Degraded
        Badge: Amber "Reconnecting (15s)"
        Retains last responding host
    end note

    Degraded --> Connected: Pulse Recovered
    Degraded --> Offline: Inactivity > 30s
    note right of Offline
        Badge: Red "Pi Down (42s)"
        Drops down PiDowntimeCard
        Shows elapsed downtime
        One-tap Tailscale launcher
    end note

    Offline --> Connected: Network / Power Restored
    Connected --> [*]: onPause() / App Backgrounded
```

---

## 🚀 Key Features

1. **Live Heartbeat Ticker & Downtime Detection**:
   - **Real-Time Pulse Counter**: Ticks up every single second (`1s ago`, `2s ago`...) right under the title: `🟢 Last ping: 3s ago (healthy)`, resetting to `0s ago` when each 10s heartbeat arrives.
   - **Compact Latency Badge**: Displays active network mode and latency (e.g. `Tailscale Mesh • 22ms` or `Home Wi-Fi • 4ms`).
   - **One-Tap Inspector Modal**: Tap the badge to inspect IP address, latency, heartbeat interval, and dead-man's switch status.
   - **Smart Downtime Alert**: If unreachable for > 30 seconds, displays a warning card with exact elapsed downtime and quick actions to launch the Tailscale app or retry.

2. **Automated Over-The-Air (OTA) Updates**:
   - App automatically probes `:5050/version.json` on launch and refresh.
   - Compares remote build code with local package version.
   - In-app **Install Now** card with live progress bar and direct Android `PackageInstaller` integration.

3. **Auto-Endpoint Routing (Zero Pi Config)**:
   - Prioritizes Home Wi-Fi (`192.168.1.10`) for lowest latency when at home.
   - Automatically fails over to Tailscale (`100.123.87.122` / `argon-host`) when off-network.
   - Auto-reprobes network upon returning to foreground (`onResume`).

4. **Native Material Design 3 Experience**:
   - Modern dark UI matching Argon homelab styling.
   - Categorized card grid for all homelab services:
     - **Homepage Dashboard** (Port 80)
     - **Identity & User Portal** (Port 8089)
     - **AdGuard Home** (Port 8080)
     - **Uptime Kuma** (Port 3001)
     - **Vaultwarden Password Vault** (Port 8443, HTTPS with internal TLS trust)
     - **Stirling-PDF** (Port 8086)
     - **Syncthing** (Port 8384)
     - **Docker Registry UI** (Port 5001)
     - **Chromium Sandbox / Neeko** (Port 3002)
     - **Qdrant Vector DB** (Port 6333)
     - **CrowdSec Bouncer** (Port 8081)
     - **Cowrie Honeypot** (Port 2222)
     - **PairDrop & IT-Tools**
   - Dual Modes:
     - **Native Hub**: Individual service cards with launch & external browser shortcuts.
     - **Dashboard (HUD)**: Embedded full Homepage dashboard directly in-app.

5. **Unified Authentication & Role-Based Access Control (RBAC)**:
   - Full support for Username/Password and Google OIDC authentication against `argon-auth` (Port 8089).
   - Role-based UI filtering:
     - **Admin**: Unrestricted access to all infrastructure stacks + 1-tap shortcut to the **Identity & User Management Portal**.
     - **Standard User**: Clean view of daily productivity tools (Stirling-PDF, Syncthing, IT-Tools, PairDrop, Disposable Browser).
   - Top-bar profile menu with role badge (`ADMIN` / `USER`) and sign-out.
   - Offline / Guest access mode fallback.

---

## 🛠️ CI/CD Pipeline & Automated Releases

```mermaid
flowchart LR
    PR["Pull Request to main"] --> GATE["PR Version Gate:\n• versionCode + 1 exactly\n• Semantic versionName bump"]
    GATE --> LINT["Fast Kotlin Lint Check\n(compileDebugKotlin)"]
    LINT -->|"Pass"| MERGE["Merge into main via CLI"]
    MERGE --> BUILD["Assemble Debug APK"]
    BUILD --> REL["Create GitHub Release Tag"]
    REL --> OTA["Deploy APK & version.json\nto Pi (:5050) via Tailscale SSH"]
    OTA --> TG["Telegram Release Notification"]
```

### Branch & PR Workflow Convention
1. Create a feature branch from `main`:
   ```bash
   git checkout -b feat/your-feature
   ```
2. Make Android changes, commit, and push:
   ```bash
   git push -u origin feat/your-feature
   ```
3. Open a Pull Request to `main`. GitHub Actions runs the **PR Version Gate** (`versionCode + 1` and semver bump) and fast compilation check.
4. On merging the PR into `main`:
   - Builds the APK
   - Creates a GitHub Release with the downloadable `.apk` file attached
   - Automatically syncs the new version to the Pi's OTA server (`argon-ota/dist/`) via Tailscale SSH!
   - Sends a Telegram release alert.

---

## Building Locally

```bash
JAVA_HOME=$HOME/.local/share/jdk-17/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug
```
