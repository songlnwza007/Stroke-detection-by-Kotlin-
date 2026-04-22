# Stroke Detection App: Research & Technical Validation

## Overview
This document summarizes the research findings and technical decisions made during the development of the Stroke Detection App, specifically focusing on the transition from a Python-centric architecture to a native Kotlin implementation.

## 1. Implementation Challenges: Python vs. Kotlin
During the initial phase, a pure Python implementation was explored due to the availability of computer vision libraries. However, several critical challenges were identified:

- **Performance Overhead**: Real-time camera processing in Python on Android (via bridges like Chaquopy) introduced significant latency, which is unacceptable for diagnostic applications.
- **UI Responsiveness**: Jetpack Compose provides a much smoother user experience and better integration with Android system services compared to Python-based UI frameworks.
- **Resource Management**: Native Kotlin (CameraX and MediaPipe Tasks) offers superior control over memory and CPU usage, ensuring the app runs reliably on a wide range of devices.

## 2. Statistical Validation
The hand detection and facial symmetry models have been validated against simulated datasets to ensure accuracy:
- **Hand Detection**: Validated using MediaPipe's Hand Landmarker, achieving high precision in detecting palm vs. fist states.
- **Facial Symmetry**: Algorithms for measuring mouth symmetry and eye droop were tested for robustness against varying lighting conditions and facial angles.

## 3. The "Golden Hour" Strategy
The app is designed to be used as a rapid screening tool. By providing a clear "Stroke Probability" score based on visual symptoms, it aims to reduce the time between symptom onset and professional medical intervention.

## 4. Future Work
- **Advanced Diagnostic Models**: Integration of more complex deep learning models for stroke subtype classification.
- **Cloud Sync**: Optional secure backup of diagnostic history for medical consultation.
- **Expanded Rehabilitation Modules**: Additional exercises for fine motor skill recovery.

---
*This research was conducted as part of the Stroke Detection development initiative.*
