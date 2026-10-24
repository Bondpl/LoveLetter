<div align="center">
  <img width="180" height="180" alt="Love Letter Icon" src="https://github.com/user-attachments/assets/01eb5e4c-cbb7-4072-99ac-fcfc3c611f1d" />

  # Love Letter

  *A private, end-to-end encrypted drawing messenger designed for pairs.*
</div>

<p align="center">
  <img width="260" alt="Love Letter Canvas Screen" src="https://github.com/user-attachments/assets/802dd83e-fa5c-4bbb-809c-efa895e00683" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img width="260" alt="Love Letter Widget" src="https://github.com/user-attachments/assets/0c0cb71e-2243-4ff1-89f1-bea748aad6b8" />
</p>

---

## Overview

**Love Letter** is a private Android application designed exclusively for two connected partners. Instead of standard text messages, partners exchange handwritten notes and drawings that appear directly on each other's Android Home Screen widget.

When a user finishes a drawing and sends it, the backend dispatches a silent push notification that wakes the partner's device in the background. The device downloads the encrypted drawing, decrypts it locally, and refreshes the home screen widget in real time without requiring the recipient to open the app.

---

## Key Features

* **Home Screen Widget:** Built with Jetpack Glance, rendering the latest received partner drawing on the Android launcher with automatic resizing and session state awareness.
* **End-to-End Encryption (E2EE):** Drawings are encrypted on the sender's device using AES-256-GCM. Neither the backend server nor cloud storage can access the plaintext drawings.
* **Background Push Synchronization:** Silent Firebase Cloud Messaging (FCM) data messages wake the recipient's phone under system WakeLock guarantees to download, decrypt, and update the widget immediately.
* **Drawing Canvas:** Full-screen vector drawing canvas featuring:
  * Multi-color palette with active selection highlighting and canvas-matching pure white.
  * Dedicated canvas background fill tool.
  * Multiple brush types: Standard Marker, Dashed, Continuous, and Highlighter.
  * 4 brush thickness levels.
  * Undo history stack and single-tap dot support.
  * Automatic state caching across application pauses and process recreation.
  * Hardware-accelerated path caching for smooth 60-120 FPS rendering.
* **Secure Pairing System:**
  * 6-digit invitation codes valid for 10 minutes.
  * Public key exchange during pairing.
  * Real-time disconnect alerts (`PAIR_DISCONNECTED`) that clear the session, history, and home screen widget on both devices instantly.
  * Automatic cleanup of expired drawings on storage.

---

## Security and Cryptography

The application implements zero-knowledge end-to-end encryption for all drawings:

1. **Key Generation:** Each device generates an asymmetric EC key pair (`secp256r1` / NIST P-256) upon pairing initiation.
2. **Key Exchange:** The base64-encoded public keys are exchanged during the initial pairing handshake (`/api/auth/anonymous` and `/api/pairs/join`). Private keys remain in Android private app storage.
3. **Shared Secret Derivation:** Both devices independently compute the shared secret using ECDH and derive an AES-256 encryption key via SHA-256.
4. **Drawing Encryption:** The drawing PNG byte stream is encrypted with AES/GCM/NoPadding using a unique 12-byte initialization vector (IV) generated via `SecureRandom`.
5. **Decryption:** The recipient downloads the encrypted payload from cloud storage and decrypts it using their local private key and the sender's public key.

---

## Tech Stack

### Android Client
* **Language:** Kotlin
* **UI:** Jetpack Compose (Material 3)
* **Widget:** Jetpack Glance (`GlanceAppWidget`, `PreferencesGlanceStateDefinition`)
* **Networking:** Retrofit 2, OkHttp 3, Kotlinx Serialization
* **Asynchronous:** Kotlin Coroutines, StateFlow, SharedFlow
* **Cryptography:** Java Cryptography Architecture (JCA: ECDH `secp256r1`, AES-256-GCM, SHA-256)
* **Push Notifications:** Firebase Cloud Messaging (FCM)

### Backend & Cloud Architecture
* **Runtime:** Node.js, TypeScript (Google Cloud Run)
* **Database:** PostgreSQL (Pairing state, anonymous user tokens, ephemeral codes)
* **Object Storage:** S3-compatible cloud storage (Encrypted drawing payloads via presigned URLs)
* **Messaging:** Firebase Admin SDK (Silent Data Messages)

---

## Setup and Configuration

### Requirements
* Android Studio Ladybug or newer
* JDK 21
* Android SDK 36 (minSdk 28)

### Configuration
1. Clone the repository:
   ```bash
   git clone git@github.com:Bondpl/LoveLetter.git
   ```
2. Create or update `local.properties` in the project root:
   ```properties
   MY_EU_SERVER_URL=https://your-backend-url.run.app
   ```
3. Place your `google-services.json` file inside the `app/` directory for Firebase Cloud Messaging support.
4. Build and install:
   ```bash
   ./gradlew assembleDebug
   ```
