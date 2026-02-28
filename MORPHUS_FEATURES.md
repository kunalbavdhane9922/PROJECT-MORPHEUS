# ═══════════════════════════════════════════════════════
# MORPHUS — COMPLETE FEATURE LIST
# Women Safety SOS Application
# ═══════════════════════════════════════════════════════


## 1. CALCULATOR DISGUISE UI

The app disguises itself as a fully functional calculator.
No visual indication of SOS capabilities.

- Standard arithmetic operations (+, -, ×, ÷)
- Decimal support, backspace, AC (clear)
- Modern dark-themed calculator interface
- Normal calculator behaviour for anyone who opens the app


## 2. HIDDEN SOS TRIGGERS

### 2.1 Double-Tap Display
- Double-tap the calculator display area
- Triggers SOS immediately with vibration confirmation
- No visual change on screen

### 2.2 Long-Press Display
- Long-press the calculator display area
- Triggers SOS immediately with vibration confirmation
- No visual change on screen

### 2.3 Shake Detection
- Violent shake triggers SOS automatically
- Uses accelerometer with g-force threshold (2.7g)
- 1500ms cooldown prevents false triggers
- Runs inside EmergencyService (SENSOR_DELAY_GAME)

### 2.4 Double Power Button Press
- Two rapid power button presses within 700ms
- Detected via SCREEN_ON / SCREEN_OFF broadcasts
- Works even when screen is locked
- Triggers full SOS activation

### 2.5 Calculator Emergency Mode (Long-Press AC)
- Long-press the AC button to enter hidden emergency mode
- Buttons 1-5 remap to predefined situations:
  1 → In Cab
  2 → Being Followed
  3 → Unsafe Location
  4 → Medical Emergency
  5 → Misbehavior
- Sends situation-specific SMS (no phone call)
- Press AC again to exit emergency mode silently


## 3. SOS ACTIVATION SYSTEM

- SosManager handles full emergency activation flow
- Shows high-priority notification on activation
- Starts EmergencyService as foreground service
- Persists SOS state across app restarts
- Vibration feedback (two-pulse pattern) confirms activation
- Deactivation from Settings with "I AM SAFE" SMS


## 4. FOREGROUND EMERGENCY SERVICE

- Runs as Android foreground service (START_STICKY)
- Survives app removal, system restarts
- Partial WakeLock keeps CPU alive (1-hour timeout)
- Silent notification (no sound, no vibration)
- Coordinates all emergency subsystems


## 5. LIVE GPS TRACKING

- FusedLocationProviderClient (high accuracy)
- 10-second update interval (5s fastest)
- Dedicated background HandlerThread
- First-fix callback for reliable initial SOS
- 15-second failsafe timer if GPS is slow
- Falls back to last-known cached location
- GPS disabled detection with callback
- Power saving mode: 30s interval when battery critical


## 6. EMERGENCY SMS SYSTEM

### 6.1 Initial SOS SMS
- Sent INSTANTLY after first GPS fix (no cooldown)
- Includes: lat, lon, battery %, time, Google Maps link
- Retries up to max on failure

### 6.2 Movement-Based Updates
- Sends update SMS when user moves beyond threshold
- Configurable movement threshold (metres)

### 6.3 Heartbeat Updates
- Periodic location SMS at configurable interval
- Doubles interval when battery is critical

### 6.4 Battery Critical SMS
- Automatic alert when battery drops to ≤5%
- "BATTERY CRITICAL. SOS potentially going dead."

### 6.5 Situation-Specific SMS
- Custom messages for 7 predefined situations
- Triggered from calculator emergency mode
- Sends SMS without initiating phone calls

### 6.6 Safe Message
- "I AM SAFE" SMS sent to all contacts on deactivation


## 7. EMERGENCY CALL ESCALATION

- Sequential auto-calling through all emergency contacts
- Managed by CallManager with EmergencyCallQueue
- Configurable via Settings (Auto Call toggle)
- Requires CALL_PHONE permission


## 8. AUDIO RECORDING

- Background audio recording during emergencies
- Saves evidence as M4A files
- Runs inside EmergencyService
- Configurable via Settings (Audio Record toggle)
- Requires RECORD_AUDIO permission


## 9. NETWORK INTELLIGENCE

### 9.1 Weak Network Alert
- Detects when network loses NET_CAPABILITY_VALIDATED
- SMS: "⚠ Network signal becoming weak" + last location
- 10-minute cooldown between alerts

### 9.2 Network Lost Alert
- Immediate SMS when network is completely lost
- SMS: "📵 Network lost" + last location + Maps link

### 9.3 Offline Mode Detection
- 30-second debounce timer prevents false triggers
- Cross-checks cellular signal strength (< -110 dBm)
- Verifies network type (UNKNOWN = confirmed offline)
- Automatic online mode restoration when connectivity returns

### 9.4 Signal Strength Monitoring
- TelephonyManager integration (legacy + modern API 31+)
- dBm-level signal tracking
- Supports LTE, 5G, and all cell info groups


## 10. OFFLINE SOS MODE

- Activates when sustained network loss confirmed
- Fetches accurate GPS location (≤50m, 10s timeout)
- Sends SMS directly (bypasses cooldown)
- Periodic update SMS every 5 minutes
- Movement monitoring every 30 seconds
- Battery critical SMS in offline mode
- Falls back to "no location" SMS if GPS unavailable


## 11. CRITICAL BATTERY MANAGEMENT

- Auto-activates SOS when battery is critical
- Enables power saving mode:
  - Reduces GPS frequency (30s intervals)
  - Doubles heartbeat interval
- Sends critical battery SMS to contacts
- Shows persistent battery-critical notification
- Reset when device is plugged back in


## 12. SUPABASE CLOUD INTEGRATION

- Incident records uploaded to Supabase database
- Audio evidence uploaded to Supabase Storage
- Offline upload queue (UploadQueueManager)
- Processes queued uploads when connectivity returns
- Fields: timestamp, latitude, longitude, battery, audio URL


## 13. SETTINGS & CONFIGURATION

### 13.1 PIN Security
- PIN-protected settings access from calculator
- Custom PIN configuration
- Default PIN provided

### 13.2 Emergency Contacts
- Add/remove trusted contacts
- Stored in AppRepository (SharedPreferences)
- Used for SMS, calls, and alerts

### 13.3 Feature Toggles
- Shake detection ON/OFF
- Power button trigger ON/OFF
- Auto call ON/OFF
- Audio recording ON/OFF
- Offline guard ON/OFF

### 13.4 Tracking Settings
- Movement threshold (metres)
- Update interval (minutes)
- Custom message template

### 13.5 Manual SOS Off
- Deactivate button in Settings
- Stops EmergencyService
- Sends "I AM SAFE" message
- Shows deactivation confirmation notification


## 14. ACCESSIBILITY SERVICE

- MorphusAccessibilityService for system-level events
- Power button detection via KeyEvent monitoring
- Safety check before triggering any action


## 15. BOOT RECEIVER

- BootReceiver listens for BOOT_COMPLETED
- Can restart protection after device reboot


## 16. PERMISSIONS

| Permission                          | Purpose                            |
|-------------------------------------|------------------------------------|
| ACCESS_FINE_LOCATION                | GPS tracking                       |
| ACCESS_COARSE_LOCATION              | Backup location                    |
| ACCESS_BACKGROUND_LOCATION          | Background GPS                     |
| SEND_SMS                            | Emergency SMS                      |
| RECEIVE_SMS / READ_SMS              | SMS handling                       |
| CALL_PHONE                          | Auto emergency calls               |
| RECORD_AUDIO                        | Evidence recording                 |
| FOREGROUND_SERVICE                  | Service survival                   |
| FOREGROUND_SERVICE_LOCATION         | Location in foreground             |
| FOREGROUND_SERVICE_MICROPHONE       | Audio in foreground                |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS| Prevent system kill                |
| RECEIVE_BOOT_COMPLETED              | Restart on boot                    |
| VIBRATE                             | Haptic confirmation                |
| WAKE_LOCK                           | Keep CPU alive                     |
| POST_NOTIFICATIONS                  | Android 13+ notifications          |
| INTERNET                            | Supabase uploads                   |
| ACCESS_NETWORK_STATE                | Network monitoring                 |
| READ_PHONE_STATE                    | Signal strength                    |


## 17. ARCHITECTURE OVERVIEW

```
┌─────────────────────────────────────────────────┐
│                  Calculator UI                   │
│      (CalculatorFragment + Hidden Triggers)      │
└──────────┬──────────────────────┬────────────────┘
           │                      │
     ┌─────▼─────┐        ┌──────▼──────┐
     │ SosManager │        │  Settings   │
     │ (activate) │        │  Activity   │
     └─────┬─────┘        └─────────────┘
           │
     ┌─────▼──────────────────────────────┐
     │        EmergencyService            │
     │       (Foreground Service)         │
     ├────────────────────────────────────┤
     │  LocationTracker  │  SmsHandler    │
     │  NetworkGuardian  │  AudioRecorder │
     │  CallManager      │  ShakeDetector │
     │  OfflineSosManager│  BatteryMgmt   │
     └────────────┬───────────────────────┘
                  │
     ┌────────────▼───────────────────────┐
     │     Supabase Cloud Backend         │
     │  (Incidents + Audio Storage)       │
     └────────────────────────────────────┘
```


## 18. FILE STRUCTURE

```
com.morphus.app/
├── data/
│   ├── AppRepository.kt          — Emergency contacts & data
│   └── SettingsManager.kt        — SharedPreferences management
├── manager/
│   ├── AudioRecorder.kt          — M4A evidence recording
│   ├── CallManager.kt            — Call escalation
│   ├── CriticalBatteryManager.kt — Low battery auto-SOS
│   ├── EmergencyCallQueue.kt     — Sequential call queue
│   ├── LocationBroadcaster.kt    — Location broadcasting
│   ├── LocationTracker.kt        — GPS tracking engine
│   ├── NetworkGuardian.kt        — Network monitoring + alerts
│   ├── OfflineSosManager.kt      — Offline SMS emergency
│   ├── SituationMessageBuilder.kt— Situation SMS templates
│   ├── SituationType.kt          — Emergency situation enum
│   ├── SmsHandler.kt             — SMS dispatch + retry
│   ├── SosManager.kt             — SOS activation controller
│   └── TriggerManager.kt         — Trigger coordination
├── network/
│   ├── IncidentData.kt           — Incident data model
│   ├── NetworkManager.kt         — Connectivity utilities
│   ├── SupabaseClient.kt         — Cloud API client
│   └── UploadQueueManager.kt     — Offline upload queue
├── receiver/
│   └── BootReceiver.kt           — Boot restart
├── service/
│   ├── EmergencyService.kt       — Main foreground service
│   ├── MorphusAccessibilityService.kt — System events
│   └── MorphusService.kt         — Background service
├── trigger/
│   ├── PowerButtonGuardian.kt    — Double power press
│   └── ShakeDetector.kt          — Shake detection
├── ui/
│   ├── CalculatorFragment.kt     — Calculator + hidden SOS
│   ├── CalculatorViewModel.kt    — Calculator logic
│   ├── MainActivity.kt           — App entry point
│   ├── MainFragment.kt           — Main navigation
│   ├── MainViewModel.kt          — Main state
│   ├── OnboardingFragment.kt     — First-run setup
│   ├── OnboardingViewModel.kt    — Onboarding state
│   └── SOSSettingsActivity.kt    — Settings screen
└── utils/
    ├── Constants.kt              — App-wide constants
    ├── MorphusLog.kt             — Custom logging
    ├── PermissionHelper.kt       — Permission utilities
    ├── PermissionUtils.kt        — Permission checks
    └── SystemHelper.kt           — System utilities
```
