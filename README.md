# LocalChat (lchat)

A peer-to-peer chat application for Android using Wi-Fi Aware (NAN - Neighbor Awareness Networking) technology. Chat with nearby users without internet or traditional Wi-Fi access points.

## Overview

**Package Name:** `com.mattintech.lchat`  
**Min SDK:** API 26 (Android 8.0)  
**Technology:** Wi-Fi Aware (NAN)

## Features

### Core Functionality
- **Dual Mode Operation:** Single APK that can function as both host and client
- **Direct P2P Communication:** No internet or router required
- **Real-time Messaging:** Instant message delivery to nearby devices
- **Auto-discovery:** Automatically find and connect to nearby chat rooms
- **Session Management:** Maintain chat sessions while devices are in range

### User Features
- Create or join chat rooms
- Set custom nicknames
- View active users in range
- Message history (session-based)
- Material Design 3 interface
- Background service for persistent connections

## Technical Requirements

### Android Permissions
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

### Architecture

#### Single APK Design
The app operates in two modes within a single application:
1. **Host Mode** - Publishes a chat service for others to discover
2. **Client Mode** - Subscribes to discover available chat services

#### Key Components
- `WifiAwareManager` - Core Wi-Fi Aware functionality
- `PublishConfig` - Configuration for hosting chat rooms
- `SubscribeConfig` - Configuration for discovering chat rooms
- `WifiAwareSession` - Manages aware connections
- `NetworkSpecifier` - Establishes data paths between devices

### Data Flow
1. **Discovery Phase**
   - Host publishes service with room name
   - Clients subscribe to discover services
   - Service discovery triggers connection UI

2. **Connection Phase**
   - Client requests connection to host
   - Host accepts/manages connections
   - Bidirectional data path established

3. **Communication Phase**
   - Messages sent over established data path
   - All connected clients receive messages
   - Host manages client list and broadcasting

## Project Structure
```
lchat/
├── app/
│   ├── src/main/java/com/mattintech/lchat/
│   │   ├── MainActivity.kt
│   │   ├── network/
│   │   │   ├── WifiAwareManager.kt
│   │   │   ├── ChatService.kt
│   │   │   └── MessageHandler.kt
│   │   ├── ui/
│   │   │   ├── ChatFragment.kt
│   │   │   ├── LobbyFragment.kt
│   │   │   └── adapters/
│   │   ├── data/
│   │   │   ├── Message.kt
│   │   │   ├── User.kt
│   │   │   └── ChatRepository.kt
│   │   └── utils/
│   └── src/main/res/
├── gradle/
└── build.gradle.kts
```

## Development Roadmap

### Phase 1: Foundation
- [ ] Basic Android project setup
- [ ] Wi-Fi Aware permission handling
- [ ] Simple host/client mode switching

### Phase 2: Core Messaging
- [ ] Message sending/receiving
- [ ] User management
- [ ] Basic UI implementation

### Phase 3: Enhanced Features
- [ ] Message persistence
- [ ] Reconnection handling
- [ ] Advanced UI features

### Phase 4: Polish
- [ ] Error handling
- [ ] Performance optimization
- [ ] UI/UX refinements

## Building

```bash
./gradlew assembleDebug
```

## Testing

Wi-Fi Aware requires physical devices for testing (API 26+). Emulator support is limited.

## License

[To be determined]