# LocalChat (lchat) - Improvement Plan

## Phase 1: Architecture Foundation

### 1.1 MVVM with ViewModels and Repository Pattern ✅
- [x] Create ViewModels for each screen (LobbyViewModel, ChatViewModel)
- [x] Extract business logic from Fragments to ViewModels
- [x] Create Repository layer for data operations
- [x] Implement proper state management with LiveData/StateFlow
- [x] Add ViewModelFactory if needed

### 1.2 Dependency Injection with Hilt ✅
- [x] Add Hilt dependencies
- [x] Set up Hilt modules for WifiAwareManager
- [x] Convert singletons to proper DI
- [x] Inject ViewModels using Hilt

### 1.3 Room Database Setup ✅
- [x] Add Room dependencies
- [x] Create Message and User entities
- [x] Implement DAOs for data access
- [x] Create database migrations
- [x] Store messages in Room database
- [x] Load message history on app restart
- [x] Implement message sync logic

### 1.4 Coroutines & Flow Optimization ✅
- [x] Convert callbacks to coroutines
- [x] Use Flow for reactive data streams
- [x] Implement proper scope management
- [x] Replace GlobalScope with proper lifecycle scopes
- [x] Add proper error handling with coroutines

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

### 2.3 Enhanced Messaging Features
- [ ] Message status indicators (sent/delivered/read)
  - [ ] Add status field to MessageEntity (pending/sent/delivered/failed)
  - [ ] Show status icons in message bubbles
  - [ ] Update status when delivery confirmed
- [ ] Store-and-forward messaging pattern
  - [ ] Save messages with "pending" status initially
  - [ ] Implement acknowledgment protocol in WifiAwareManager
  - [ ] Update to "sent" only after confirmation received
  - [ ] Queue messages when offline/disconnected
  - [ ] Auto-retry failed messages with exponential backoff
  - [ ] Mark messages as failed after max retries
- [ ] User presence indicators (online/offline/typing)
- [ ] Message timestamps with proper formatting
- [ ] Offline message queue
- [ ] Message retry mechanism
- [ ] Long press message actions (copy, delete)

### 2.4 File & Media Sharing
- [ ] Image sharing support
- [ ] File transfer capability
- [ ] Image preview in chat
- [ ] Progress indicators for transfers
- [ ] File size limits and validation

## Phase 3: UI/UX Improvements

### 3.1 Material 3 Design Update
- [ ] Migrate to Material 3 components
- [ ] Implement dynamic color theming
- [ ] Update typography and spacing
- [ ] Add proper elevation and shadows
- [ ] Implement Material You design principles

### 3.2 Dark Theme & Theming
- [ ] Implement dark theme
- [ ] Add theme toggle in settings
- [ ] System theme detection
- [ ] Custom color schemes
- [ ] Persist theme preference

### 3.3 Animations & Polish
- [ ] Message send/receive animations
- [ ] Screen transition animations
- [ ] Loading state animations
- [ ] Smooth scrolling improvements
- [ ] Haptic feedback

### 3.4 Better Error Handling UI
- [ ] User-friendly error messages
- [ ] Retry mechanisms with UI feedback
- [ ] Connection lost/restored snackbars
- [ ] Empty states for no messages/users
- [ ] Inline error states

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

## Phase 5: Security & Privacy

### 5.1 Message Encryption
- [ ] End-to-end encryption implementation
- [ ] Key exchange protocol
- [ ] Message integrity verification
- [ ] Secure key storage
- [ ] Forward secrecy

### 5.2 Privacy Features
- [ ] Optional username anonymization
- [ ] Message auto-deletion
- [ ] Block/unblock users
- [ ] Private rooms with passwords
- [ ] Data export/import

## Phase 6: Advanced Features

### 6.1 Background Service
- [ ] Create foreground service for persistent connection
- [ ] Handle Doze mode and battery optimization
- [ ] Add notification for active chat
- [ ] Implement proper service lifecycle
- [ ] Wake lock management

### 6.2 Settings & Preferences
- [ ] Create settings screen
- [ ] Notification preferences
- [ ] Sound/vibration settings
- [ ] Auto-reconnect toggle
- [ ] Message history limits

## Phase 7: Testing & Quality

### 7.1 Unit Testing
- [ ] Test ViewModels
- [ ] Test Repository logic
- [ ] Test data transformations
- [ ] Test error scenarios
- [ ] Mock dependencies with Hilt testing

### 7.2 Integration Testing
- [ ] Test database operations
- [ ] Test network layer
- [ ] Test complete user flows
- [ ] Test state persistence

### 7.3 UI Testing
- [ ] Espresso tests for main flows
- [ ] Test navigation
- [ ] Test user interactions
- [ ] Screenshot testing
- [ ] Accessibility testing

## Current Status
- ✅ Phase 1.1 - MVVM Architecture - COMPLETED
- ✅ Phase 1.2 - Dependency Injection with Hilt - COMPLETED
- ✅ Phase 1.3 - Room Database Setup - COMPLETED
- ✅ Phase 1.4 - Coroutines & Flow Optimization - COMPLETED
- ✅ Phase 2.1 - Connection Status Management - COMPLETED
- 🚀 Next Priority Options:
  - Phase 2.2 - User List Feature (Core UX)
  - Phase 2.3 - Enhanced Messaging (Better UX)
  - Phase 3.1 - Material 3 Update (Modern UI)
  - Phase 3.2 - Dark Theme & Theming

## Completed Work Summary
1. **MVVM Architecture**: ViewModels, Repository pattern, proper separation of concerns
2. **Dependency Injection**: Hilt integration with proper scoping and lifecycle management
3. **Room Database**: Message persistence with proper DAOs and entity mapping
4. **Coroutines & Flow**: Converted callbacks to coroutines, implemented Flow for reactive streams, proper scope management
5. **Connection Status**: Visual indicator with real-time updates, activity-based detection
6. **Sleep/Wake Handling**: Auto-recovery when messages resume after device sleep

## Development Notes
- Architecture foundation (Phase 1) should be completed before moving to advanced features
- UI/UX improvements (Phase 3) can be done in parallel with feature development
- Testing (Phase 7) should be implemented incrementally as features are added
- Security features (Phase 5) are important for production readiness