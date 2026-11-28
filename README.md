# 🏏 Cricscore - Live Cricket Scoring & Broadcast App

**Cricscore** is a modern, real-time Android cricket scoring and live match broadcasting application built with **Kotlin** and **Jetpack Compose**. Powered by **Firebase Authentication** and **Cloud Firestore**, it allows anyone to sign up as an Admin, host their own cricket matches, and broadcast ball-by-ball live scores to spectators worldwide.

---

## ✨ Key Features

- 👤 **Multi-Admin Sign Up & Login:** Users can create an Admin account using Email/Password or Google Sign-In to host and manage their own matches.
- 🔒 **Secure Match Ownership:** Built-in Firestore Security Rules ensure that only the specific Admin (`adminId`) who created a match can update its score.
- 👀 **Live Spectator Mode:** Fans and viewers can watch real-time, ball-by-ball live scores, full interactive scorecards, and match status via a share code without needing to log in.
- 🖼️ **Teams & Players with Interactive Photo Crop:** Create teams and player rosters with built-in circle photo cropping and automatic image compression.
- 📊 **Detailed Ball-by-Ball Tracking:** Tracks runs, wickets, overs, extras (wides, no-balls, byes, leg-byes), fall of wickets, and career player stats.
- 📱 **Modern Material 3 UI:** Clean, intuitive, and responsive interface designed for effortless one-handed scoring on the field.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **Local & Cloud Database:** Room Offline DB + Firebase Cloud Firestore (Real-time sync)
- **Authentication:** Firebase Email/Password & Google Sign-In

---

## 🚀 Quick Setup & Full Documentation

For complete step-by-step instructions on **changing the Android Package ID, rebranding (app name, colors, icon, splash screen, strings), configuring Firebase, and generating a Signed Release APK/AAB**, please open the included **`Documentation.html`** file in any web browser.

### Quick Overview:
1. Extract the downloaded ZIP archive and open the project folder in **Android Studio**.
2. Create a new project in the [Firebase Console](https://google.com), add your Android app package ID, and place your `google-services.json` inside the `app/` directory.
3. Enable **Email/Password** and **Google** sign-in in **Firebase Authentication**, and publish the included `firestore.rules` in **Cloud Firestore**.
4. Customize your app name, colors, icon, and Start.io App ID as detailed in **`Documentation.html`**, then build your Signed APK/AAB.
