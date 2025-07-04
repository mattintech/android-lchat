# LocalChat (lchat) - Improvement Plan

## Phase 1: Architecture Foundation

### 1.1 MVVM with ViewModels and Repository Pattern ✅
- [x] Create ViewModels for each screen (LobbyViewModel, ChatViewModel)
- [x] Extract business logic from Fragments to ViewModels
- [x] Create Repository layer for data operations
- [x] Implement proper state management with LiveData/StateFlow
- [x] Add ViewModelFactory if needed

### 1.2 Dependency Injection with Hilt
- [ ] Add Hilt dependencies
- [ ] Set up Hilt modules for WifiAwareManager
- [ ] Convert singletons to proper DI
- [ ] Inject ViewModels using Hilt

## Phase 2: Core UX Improvements

### 2.1 Connection Status Management ✅
- [x] Add connection state to ViewModels
- [x] Create UI indicator for connection status
- [x] Show real-time connection state changes
- [x] Add connection error messages

### 2.2 User List Feature
- [ ] Track connected users in Repository
- [ ] Add UI to display active users
- [ ] Handle user join/leave events
- [ ] Show user count in chat header

## Phase 3: Data Persistence

### 3.1 Room Database Setup
- [ ] Add Room dependencies
- [ ] Create Message and User entities
- [ ] Implement DAOs for data access
- [ ] Create database migrations

### 3.2 Message Persistence
- [ ] Store messages in Room database
- [ ] Load message history on app restart
- [ ] Implement message sync logic
- [ ] Add message timestamps

## Phase 4: Reliability Improvements

### 4.1 Reconnection Handling
- [ ] Detect connection drops
- [ ] Implement exponential backoff retry
- [ ] Preserve message queue during disconnection
- [ ] Auto-reconnect when network available

### 4.2 Network State Monitoring
- [ ] Monitor WiFi state changes
- [ ] Handle app lifecycle properly
- [ ] Save and restore connection state

## Phase 5: Advanced Features

### 5.1 Background Service
- [ ] Create foreground service for persistent connection
- [ ] Handle Doze mode and battery optimization
- [ ] Add notification for active chat
- [ ] Implement proper service lifecycle

### 5.2 Additional Features
- [ ] Message delivery status
- [ ] Typing indicators
- [ ] File/image sharing support
- [ ] Message encryption improvements

## Current Status
- ✅ Phase 1.1 - MVVM Architecture - COMPLETED
- ✅ Phase 2.1 - Connection Status Management - COMPLETED
- 🚀 Next: Phase 1.2 (Dependency Injection) or Phase 3 (Data Persistence)

## Completed Work Summary
1. **MVVM Architecture**: ViewModels, Repository pattern, proper separation of concerns
2. **Connection Status**: Visual indicator with real-time updates, activity-based detection
3. **Sleep/Wake Handling**: Auto-recovery when messages resume after device sleep