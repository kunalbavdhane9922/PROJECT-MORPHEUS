===============================================================================
🚨 MORPHUS — Stealth Offline SOS Safety System
===============================================================================

🏆 Hackathon Prototype — Offline Emergency Safety Architecture

MORPHUS is a covert Android-based emergency safety system designed to operate
reliably even without internet connectivity. Disguised as a fully functional
calculator, the application enables silent SOS activation, offline emergency
communication, automated evidence collection, and fail-safe alert delivery.

Unlike traditional safety apps, MORPHUS is built using an offline-first,
survivor-centric architecture that continues functioning during network loss,
device stress, or restricted user interaction.

-------------------------------------------------------------------------------
🎯 PROBLEM
-------------------------------------------------------------------------------

Real emergencies rarely happen under ideal conditions.

Existing safety applications fail because:

  ❌ Internet dependency breaks alerts in low-signal areas
  ❌ Visible SOS apps can be detected by attackers
  ❌ Panic situations prevent manual interaction
  ❌ Evidence is not preserved reliably
  ❌ Alerts stop when connectivity drops

MORPHUS is designed specifically for failure conditions.

-------------------------------------------------------------------------------
🧠 CORE CONCEPT
-------------------------------------------------------------------------------

The application appears as a normal calculator.

Behind this interface runs a persistent emergency system capable of autonomous
operation.

Hidden SOS triggers include:

  • Secret calculator PIN
  • Double-tap calculator display
  • Long-press display
  • Device shake detection
  • Double power-button press (lock screen supported)
  • Emergency calculator mode (AC long press)
  • Critical battery auto-trigger

Once activated, the system operates automatically without further interaction.

-------------------------------------------------------------------------------
⚙️ KEY CAPABILITIES
-------------------------------------------------------------------------------

[ STEALTH ARCHITECTURE ]
  • Calculator disguise UI
  • Hidden PIN-protected settings
  • Silent background execution
  • No visible SOS indicators

[ OFFLINE EMERGENCY SYSTEM ]
  • Works without internet
  • Instant SOS SMS with GPS coordinates
  • Movement-based location updates
  • Periodic heartbeat tracking
  • Last-location transmission on signal loss
  • Offline detection intelligence

[ LIVE GPS TRACKING ]
  • High-accuracy fused location provider
  • Movement threshold tracking (default 50m)
  • Battery-aware tracking frequency
  • Google Maps link generation

[ EMERGENCY CALL ESCALATION ]
  • Sequential auto-calling of contacts
  • FIFO emergency call queue

[ EVIDENCE PRESERVATION ]
  • Background audio recording
  • Recording continues with screen locked
  • Secure local storage (M4A)
  • Auto upload when internet returns

[ CLOUD SYNC — SUPABASE ]
  Uploads incident metadata:
      - timestamp
      - latitude
      - longitude
      - battery level
      - audio evidence URL

[ NETWORK INTELLIGENCE ]
  • Weak signal detection
  • Network loss alerts
  • Signal strength monitoring
  • Offline confirmation logic

[ CRITICAL BATTERY PROTECTION ]
  • Auto SOS at ≤5% battery
  • Power-saving emergency mode
  • Battery-critical alert SMS

[ VOLUNTEER ALERT NETWORK ]
  • Optional volunteer notifications
  • Extends safety beyond trusted contacts

[ PERSISTENT EXECUTION ]
  • Foreground Service (START_STICKY)
  • WakeLock protection
  • Boot auto-restart
  • SOS state persistence

-------------------------------------------------------------------------------
🏗️ SYSTEM ARCHITECTURE
-------------------------------------------------------------------------------

Calculator UI (Hidden Triggers)
            ↓
        SosManager
            ↓
     EmergencyService (Foreground)
        ├── LocationTracker
        ├── SmsHandler
        ├── CallManager
        ├── AudioRecorder
        ├── NetworkGuardian
        ├── OfflineSosManager
        ├── CriticalBatteryManager
        └── UploadQueueManager
            ↓
        Supabase Backend

Design Principles:
  • Offline-first
  • Fail-safe execution
  • Minimal user interaction
  • Evidence preservation
  • Background reliability

-------------------------------------------------------------------------------
📱 SOS EXECUTION FLOW
-------------------------------------------------------------------------------

SOS Triggered
      ↓
Foreground Emergency Service Starts
      ↓
GPS Acquisition + Audio Recording
      ↓
Instant SOS SMS Sent
      ↓
Emergency Call Escalation
      ↓
Movement & Periodic Updates
      ↓
Offline Monitoring
      ↓
Cloud Sync When Internet Returns

-------------------------------------------------------------------------------
🛠️ TECH STACK
-------------------------------------------------------------------------------

ANDROID
  • Kotlin (Native Android)
  • Foreground Services
  • Broadcast Receivers
  • Accessibility Service
  • MediaRecorder API
  • FusedLocationProviderClient
  • SmsManager API
  • WakeLock Management

BACKEND
  • Supabase Storage
  • Supabase Database
  • OkHttp Networking
  • Kotlin Coroutines

ARCHITECTURE
  • Offline Queue System
  • Persistent SOS State Management
  • Network State Monitoring

-------------------------------------------------------------------------------
🔐 PERMISSIONS USED
-------------------------------------------------------------------------------

  ACCESS_FINE_LOCATION
  ACCESS_BACKGROUND_LOCATION
  SEND_SMS
  CALL_PHONE
  RECORD_AUDIO
  FOREGROUND_SERVICE
  RECEIVE_BOOT_COMPLETED
  WAKE_LOCK
  ACCESS_NETWORK_STATE
  POST_NOTIFICATIONS
  INTERNET

(All permissions are required for emergency reliability.)

-------------------------------------------------------------------------------
🚀 INSTALLATION
-------------------------------------------------------------------------------

Clone repository:

    git clone https://github.com/yourusername/morphus.git

Run locally:

  1. Open project in Android Studio
  2. Connect physical Android device
  3. Grant all permissions
  4. Enable Morphus Accessibility Service
  5. App launches as Calculator

-------------------------------------------------------------------------------
🧪 DEMO GUIDE
-------------------------------------------------------------------------------

1. Launch app → Calculator appears
2. Use calculator normally
3. Enter secret PIN → open settings
4. Trigger SOS (double tap / shake / power button)
5. Observe:
      • SMS alerts
      • GPS tracking
      • Background recording
6. Disable SOS → "I AM SAFE" SMS sent
7. Restore internet → recordings upload automatically

-------------------------------------------------------------------------------
📂 PROJECT STRUCTURE
-------------------------------------------------------------------------------

com.morphus.app
├── ui/            → Calculator & onboarding UI
├── manager/       → SOS orchestration logic
├── service/       → Foreground emergency services
├── trigger/       → Hidden activation mechanisms
├── location/      → GPS tracking engine
├── network/       → Connectivity & cloud sync
├── receiver/      → Boot restart handlers
└── utils/         → Helpers & constants

-------------------------------------------------------------------------------
⚠️ DISCLAIMER
-------------------------------------------------------------------------------

This project is a hackathon prototype created for educational and research
purposes and is not intended to replace official emergency services.

-------------------------------------------------------------------------------
🌟 FUTURE IMPROVEMENTS
-------------------------------------------------------------------------------

  • Live tracking dashboard
  • Volunteer proximity matching
  • Encrypted evidence vault
  • Satellite fallback integration
  • Wearable device support
  • Police system integration

-------------------------------------------------------------------------------
👨‍💻 TEAM MORPHUS
-------------------------------------------------------------------------------

Developed as part of a safety innovation hackathon focused on
real-world emergency resilience.

-------------------------------------------------------------------------------
📜 LICENSE
-------------------------------------------------------------------------------

MIT License

-------------------------------------------------------------------------------
⭐ SUPPORT
-------------------------------------------------------------------------------

If you find this project meaningful, consider starring the repository.

===============================================================================
"MORPHUS works when other safety apps stop working."
===============================================================================
