# Stroke Detection App (Kotlin)

![Android](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-orange?style=for-the-badge&logo=kotlin)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue?style=for-the-badge&logo=jetpack-compose)
![MediaPipe](https://img.shields.io/badge/AI-MediaPipe-brightgreen?style=for-the-badge)

A premium Android application designed for early stroke symptom detection and rehabilitation monitoring. This app utilizes state-of-the-art Computer Vision to analyze facial symmetry and hand mobility, providing users and healthcare providers with critical data during the "Golden Hour" of stroke response and subsequent recovery.

## ✨ Features

- **🛡️ Real-time Facial Analysis**: Uses advanced computer vision to monitor eye droop and mouth asymmetry, common early signs of a stroke.
- **✋ Hand Mobility Exercises**: Powered by Google's MediaPipe, the app tracks hand landmark points to validate exercise performance and fist-clenching strength.
- **📊 Rehabilitation History**: Local data persistence using Room DB to track progress over time.
- **🚀 Premium UX/UI**: Built with Jetpack Compose, featuring a sleek dark mode, glassmorphism elements, and smooth transitions.
- **🔗 Hybrid Architecture**: Designed with a Python-ready bridge for advanced diagnostic models (Chaquopy integration ready).

## 🛠️ Technology Stack

- **UI Framework**: Jetpack Compose
- **Camera**: CameraX API
- **AI/ML**: Google MediaPipe (Vision Tasks)
- **Database**: Room Persistence Library
- **Navigation**: Compose Navigation
- **Architecture**: MVVM with Coroutines and StateFlow

## 📁 Repository Structure

```
├── app/
│   ├── src/main/java/com/example/strokedetectionapp/
│   │   ├── data/           # Room DB and Repository
│   │   ├── ui/             # Compose Screens and Navigation
│   │   ├── utils/          # Camera and AI logic (HandDetector, PythonBridge)
│   │   └── viewmodel/      # Business logic
│   └── src/main/assets/    # MediaPipe models
├── docs/                   # Research and validation documents
└── build.gradle.kts        # Project configuration
```

## 🚀 Getting Started

### Prerequisites

- Android Studio Koala+
- JDK 17
- Android Device/Emulator (API 26+)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/songlnwza007/Stroke-detection-by-Kotlin-.git
   ```
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Build and Run on your device.

## 📝 Research & Validation

This project is backed by comprehensive research on the efficiency of native Kotlin implementations for real-time mobile diagnostics compared to pure Python approaches. Detailed findings can be found in the [docs/research_summary.md](docs/research_summary.md).

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---
*Created with passion to improve stroke recovery outcomes.*
