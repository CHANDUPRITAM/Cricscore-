# 🏏 Cricket Scorer Pro

**Cricket Scorer Pro** is a modern, real-time Android cricket scoring and live match broadcasting application built with **Kotlin** and **Jetpack Compose**. Powered by **Firebase Authentication** and **Cloud Firestore**, it allows anyone to sign up as an Admin, host their own cricket matches, and broadcast ball-by-ball live scores to spectators worldwide.

---

## ✨ Key Features

- 👤 **Multi-Admin Sign Up & Login:** Any user can create an Admin account using Email/Password authentication to host and manage their own matches.
- 🔒 **Secure Match Ownership:** Built-in Firestore Security Rules ensure that only the specific Admin (`adminId`) who created a match can update its score.
- 👀 **Live Spectator Mode:** Fans and viewers can watch real-time, ball-by-ball live scores, current run rates, and match status without needing to log in.
- ⚡ **Smart Openers & Saved Players Selector:** Quickly save player rosters and select opening batsmen and bowlers with zero hassle.
- 📊 **Detailed Ball-by-Ball Tracking:** Tracks runs, wickets, overs, extras (wides, no-balls, byes), partnerships, and ball-by-ball commentary chips.
- 📱 **Modern Material Design UI:** Clean, intuitive, and responsive interface designed for effortless one-handed scoring on the field.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **Backend & Database:** Firebase Cloud Firestore (Real-time sync)
- **Authentication:** Firebase Email/Password Auth

---

## 🚀 Getting Started (Setup Instructions)

To run this project locally in Android Studio:

1. **Clone the Repository:**
   ```bash
   git clone https://github.com
   ```
2. **Open in Android Studio:**
   - Open Android Studio and select **Open an existing project**, then choose the cloned folder.
3. **Configure Firebase:**
   - Go to the [Firebase Console](https://google.com) and create a new project.
   - Register an Android app with your package name and download the `google-services.json` file.
   - Place `google-services.json` inside the `app/` directory of this project.
   - Enable **Email/Password** in **Firebase Authentication**.
   - Create a **Cloud Firestore** database and apply the security rules (`firestore.rules`).
4. **Build & Run:**
   - Sync Gradle files and run the app on an Android emulator or physical device.

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to fork this repository and submit a pull request.
