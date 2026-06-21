<div align="center">
  <img width="256" height="256" alt="Love Letter Icon" src="https://github.com/user-attachments/assets/01eb5e4c-cbb7-4072-99ac-fcfc3c611f1d" />

  # Love Letter 💌
  
  *A private, real-time drawing messenger designed exclusively for two people.*
</div>

---

## 📖 About The Project

**Love Letter** is a unique Android application built to bridge the distance between two specific users. Instead of text messages, users communicate by sending hand-drawn doodles directly to each other's Android Home Screen. 

When one person finishes a drawing and hits send, the app silently wakes up the partner's phone in the background, downloads the new art, and instantly updates their home screen widget — ensuring that a personal, handwritten surprise is waiting for them the next time they unlock their phone.

## ✨ Key Features

* **Home Screen Widget:** A fully responsive Android widget that displays the latest received drawing.
* **Background Synchronization:** Uses silent Firebase Cloud Messaging `data` payloads to fetch new drawings in the background without requiring the app to be open.
* **Interactive Canvas:** A built-in drawing tool supporting multiple colors, clearing canvas and smooth paths.
* **Auto-Cleaning Database:** The backend automatically purges drawings older than 3 days to keep the database lightweight and cost-effective.
* **Secure Connection:** End-to-end encrypted transit via Google Cloud Run with API Key authentication.

## 🛠️ Tech Stack

**Frontend (Android):**
* Kotlin
* Jetpack Compose (UI & Canvas)
* Kotlin Coroutines & Flow
* Ktor Client (Networking)

**Backend & Infrastructure:**
* Node.js & TypeScript
* PostgreSQL
* Firebase Cloud Messaging
* Google Cloud Run

## 🚀 How It Works (The Magic)

Because the app is strictly for two users, it avoids complex login screens. It relies on a one-to-one API Key pairing system:
1. User A draws an image and sends it via an HTTP POST request.
2. The Node.js server saves the `ByteArray` to PostgreSQL and triggers an FCM notification to User B's device token.
3. User B's Android system catches the silent notification via `PushNotificationService`.
4. The app fetches the new drawing from the server and updating the home screen dynamically.

## ⚙️ Local Setup

Since this is a private application, API keys and server URLs are not included in the repository for security reasons.
