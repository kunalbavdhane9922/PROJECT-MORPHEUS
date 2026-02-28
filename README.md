===============================================================================
🚨 MORPHUS — Stealth Offline SOS Safety System
===============================================================================

🏆 HACKATHON PROTOTYPE — OFFLINE EMERGENCY SAFETY ARCHITECTURE

"MORPHUS works when other safety apps stop working."

-------------------------------------------------------------------------------
👀 QUICK OVERVIEW (FOR JUDGES)
-------------------------------------------------------------------------------

MORPHUS is a stealth emergency safety system disguised as a calculator that
continues protecting users even WITHOUT internet connectivity.

The system silently triggers SOS alerts, sends GPS location via SMS,
records evidence, escalates emergency calls, and preserves incident data —
all automatically and invisibly.

Built for real-world failure scenarios:
    • No internet
    • Panic situations
    • Device lock state
    • Low battery
    • Limited user interaction

Platform: Android (Native Kotlin)
Category: Safety / Emergency Tech / Offline Systems

-------------------------------------------------------------------------------
🚩 THE REAL PROBLEM
-------------------------------------------------------------------------------

Most safety apps assume ideal conditions.

Reality:
    ❌ Network disappears
    ❌ Attackers notice SOS apps
    ❌ Users cannot unlock phones calmly
    ❌ Evidence gets lost
    ❌ Apps die in background

Emergency software fails exactly when needed most.

-------------------------------------------------------------------------------
💡 OUR SOLUTION
-------------------------------------------------------------------------------

MORPHUS introduces an OFFLINE-FIRST EMERGENCY ARCHITECTURE.

Instead of depending on internet services, the system relies on:

    ✔ Cellular SMS infrastructure
    ✔ Background foreground services
    ✔ Persistent execution
    ✔ Automated decision logic
    ✔ Stealth interaction design

User triggers SOS once.
System handles everything else autonomously.

-------------------------------------------------------------------------------
⭐ WHY MORPHUS IS DIFFERENT (JUDGE HIGHLIGHTS)
-------------------------------------------------------------------------------

Unlike typical SOS apps:

| Feature                         | Normal Apps | MORPHUS |
|---------------------------------|-------------|---------|
| Works without Internet          | ❌          | ✅ |
| Hidden from attacker            | ❌          | ✅ |
| Auto evidence recording         | ❌          | ✅ |
| Persistent after reboot         | ❌          | ✅ |
| Network-loss intelligence       | ❌          | ✅ |
| Battery-critical automation     | ❌          | ✅ |
| Multi-trigger activation        | ⚠️ Limited | ✅ |

MORPHUS is designed for FAILURE CONDITIONS, not ideal demos.

-------------------------------------------------------------------------------
🧠 HOW IT WORKS (SYSTEM IDEA)
-------------------------------------------------------------------------------

The app appears as a normal calculator.

Hidden triggers activate SOS:

    • Secret PIN
    • Double tap display
    • Long press display
    • Device shake
    • Double power button press
    • Emergency calculator mode
    • Low battery auto-trigger

After activation:

    → Foreground emergency service starts
    → GPS tracking begins
    → SOS SMS sent instantly
    → Calls contacts sequentially
    → Audio evidence recorded
    → Movement tracked
    → Updates sent periodically
    → Data uploads when internet returns

No further user action required.

-------------------------------------------------------------------------------
🏗️ ARCHITECTURE (ENGINEERING VIEW)
-------------------------------------------------------------------------------

Calculator UI
        ↓
     SosManager
        ↓
EmergencyService (Foreground Persistent Service)
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

Design Focus:
    • Offline-first
    • Fail-safe execution
    • Autonomous operation
    • Evidence preservation

-------------------------------------------------------------------------------
🔥 KEY FEATURES
-------------------------------------------------------------------------------

[ STEALTH MODE ]
• Calculator disguise (icon + UI)
• Hidden settings via PIN
• No visible SOS indicators

[ OFFLINE SOS ]
• GPS location SMS without internet
• Movement-based updates
• Last-location transmission

[ EMERGENCY ESCALATION ]
• Sequential auto-calling
• Trusted contacts + volunteers

[ EVIDENCE COLLECTION ]
• Background audio recording
• Secure local storage
• Auto cloud upload

[ NETWORK INTELLIGENCE ]
• Weak signal detection
• Offline confirmation logic
• Automatic recovery handling

[ BATTERY SAFETY ]
• Auto SOS at ≤5%
• Power-saving emergency mode

-------------------------------------------------------------------------------
📱 DEMO FLOW (FOR JUDGES)
-------------------------------------------------------------------------------

1. Open app → looks like calculator
2. Perform calculation (prove disguise)
3. Trigger SOS (double tap display)
4. Observe:
       ✔ SMS received with GPS link
       ✔ Background recording active
       ✔ Persistent notification
5. Disable SOS → "I AM SAFE" message sent
6. Re-enable internet → evidence uploads

Demo Time: ~60 seconds

-------------------------------------------------------------------------------
🛠️ TECH STACK
-------------------------------------------------------------------------------

ANDROID
• Kotlin (Native)
• Foreground Services
• Accessibility Service
• Broadcast Receivers
• MediaRecorder API
• Fused Location Provider
• SmsManager API

BACKEND
• Supabase Database
• Supabase Storage
• OkHttp Networking
• Kotlin Coroutines

-------------------------------------------------------------------------------
🔐 PERMISSIONS (WHY NEEDED)
-------------------------------------------------------------------------------

Location        → GPS tracking
SMS             → Offline alerts
Call Phone      → Emergency escalation
Audio           → Evidence recording
Boot Receiver   → Restart protection
WakeLock        → Service survival

All permissions directly support emergency reliability.

-------------------------------------------------------------------------------
📂 PROJECT STRUCTURE
-------------------------------------------------------------------------------

com.morphus.app
├── ui/
├── manager/
├── service/
├── trigger/
├── location/
├── network/
├── receiver/
└── utils/

-------------------------------------------------------------------------------
⚠️ DISCLAIMER
-------------------------------------------------------------------------------

This is a hackathon prototype created for research and demonstration purposes.
It does not replace official emergency services.

-------------------------------------------------------------------------------
🚀 FUTURE ROADMAP
-------------------------------------------------------------------------------

• Live tracking dashboard
• Volunteer proximity system
• Satellite fallback communication
• Encrypted evidence vault
• Wearable SOS triggers
• Police integration (112 India)

-------------------------------------------------------------------------------
👨‍💻 TEAM MORPHUS
-------------------------------------------------------------------------------

Built during a safety innovation hackathon focused on real-world
emergency resilience systems.

-------------------------------------------------------------------------------
⭐ FINAL MESSAGE TO JUDGES
-------------------------------------------------------------------------------

Most safety apps work when conditions are perfect.

MORPHUS was designed for when everything goes wrong.
===============================================================================
