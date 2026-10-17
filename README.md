# RouteWise SA - Android Kotlin Native Application

A high-fidelity, production-grade Android native application designed for the South African road landscape. Developed with **Kotlin**, **Android Jetpack**, **Room Local Database**, **Retrofit 2**, **Coroutines & Flow**, and a custom **Material 3** dark design system.

---

## 📁 Project Architecture & Structure

This project follows **Clean Architecture** principles and the **Repository Pattern** to ensure scalability, testability, and offline-first reliability.

```text
RouteWiseSA/
├── gradle/                             # Gradle version catalog & wrapper
└── app/
    └── src/main/
        ├── AndroidManifest.xml         # Permissions & Activity declarations
        ├── java/com/routewise/sa/
        │   ├── RouteWiseApplication.kt  # Repository & Database initialization
        │   ├── MainActivity.kt          # Host for BottomNav & Side Drawer
        │   ├── data/
        │   │   ├── local/               # Room DB (RouteWiseDatabase), DAOs
        │   │   ├── model/               # Room Entities (User, Incident, etc.)
        │   │   ├── remote/              # Retrofit API Service & DTOs
        │   │   └── repository/          # Centralized data access logic
        │   ├── ui/
        │   │   ├── auth/                # Login & Advanced Sign-Up (SA Location Data)
        │   │   ├── map/                 # Live Navigation Map
        │   │   ├── alerts/              # Real-time Traffic Alert Feed (Room-backed)
        │   │   ├── report/              # Multi-step Hazard Reporting logic
        │   │   ├── plan/                # Trip Corridor Planner with Multi-stops
        │   │   ├── chat/                # AI Road Scout Copilot
        │   │   ├── profile/             # User session & Impact Points tracking
        │   │   ├── settings/            # App preferences (Units, Theme)
        │   │   └── ViewModelFactory.kt  # Dependency Injection for ViewModels
        │   └── util/                    # LocationData, VoiceGuidance, Helpers
        └── res/
            ├── drawable/                # Custom emerald glow & themed assets
            ├── layout/                  # Modern XML Layouts with ViewBinding
            ├── navigation/              # Jetpack Navigation Graph
            └── values/                  # Strings (externalized), Colors, Themes
```

---

## 🇿🇦 Key Features

### 🔐 Advanced Authentication
- **Dual-Mode Auth**: High-fidelity "Sign In" and "Create Account" interface.
- **SA Location Engine**: Integrated selection for all 9 South African provinces with dynamic city filtering.
- **Session Persistence**: Automatic login bypass using `SharedPreferences` and user profile caching in `Room`.

### 🚨 Real-Time Traffic & Hazards
- **Crowdsourced Alerts**: Report and track potholes, load-shedding signal outages, collisions, and police traps.
- **Local-First Reporting**: Hazards are saved to the **Room Database** immediately, ensuring data integrity during poor connectivity on highways like the N1 or N3.
- **Interactive Feed**: Live list of active alerts with upvote/dismiss functionality.

### 🗺️ Intelligent Route Planning
- **Corridor Optimization**: Pre-configured popular SA corridors (N1 North, N2 Settlers Way, M4 Coastal).
- **Multi-Stop Support**: Quick-add waypoints for Gas, Food (Nando's, Steers), Coffee, or ATMs along your route.
- **Strategy Selection**: Choose between Fastest, Shortest, or Eco-Save routes with toll-road avoidance.

### 🤖 AI Road Scout Copilot
- **Conversational Assistant**: Ask about road rules, traffic intelligence, or optimal travel times.
- **South African Context**: Trained on local road terminology and load-shedding impacts.

---

## 🛠️ Technical Stack

- **Language**: Kotlin 1.9+ (Coroutines, Flow, Sealed Classes)
- **Database**: Room (Local persistence with offline-first sync logic)
- **Networking**: Retrofit 2 & OkHttp (REST API integration)
- **Architecture**: MVVM with Repository Pattern
- **UI**: 
    - Material Components (Exposed Dropdowns, Chips, MaterialSwitch)
    - Custom Design System (Slate & Emerald dark theme)
    - ViewBinding for type-safe layout interaction
- **Navigation**: Jetpack Navigation Component (BottomNav + Drawer integration)

---

## 🚀 How to Open in Android Studio

1. Clone this repository or extract the project files.
2. Open **Android Studio** (Ladybug 2024.2.1 or newer recommended).
3. Go to **File -> Open...** and select the root directory.
4. Wait for the Gradle sync to complete (uses Version Catalogs for dependency management).
5. **Clean & Build**: Run `Build -> Make Project` to generate ViewBinding and Room classes.
6. **Run**: Deploy to an emulator or physical device (Minimum SDK: 24).
