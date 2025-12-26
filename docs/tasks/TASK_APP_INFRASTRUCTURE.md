# App Infrastructure Component

## Overview
Set up the foundational infrastructure for the FolderSync Android app including dependency injection with Hilt, application class configuration, Android manifest, Gradle build configuration, ProGuard rules, and Google Cloud Console setup for Drive API access.

## Tasks

### Hilt DI Modules

#### AppModule
- [ ] Create AppModule with @Module and @InstallIn(SingletonComponent)
- [ ] Provide Application context
- [ ] Provide CoroutineDispatchers (IO, Default, Main)
- [ ] Provide SharedPreferences / DataStore
- [ ] Provide Gson/Moshi instance
- [ ] Provide Clock for time operations

#### DatabaseModule
- [ ] Create DatabaseModule
- [ ] Provide AppDatabase singleton
- [ ] Provide SyncFileDao
- [ ] Provide SyncHistoryDao
- [ ] Configure database builder with migrations

#### NetworkModule
- [ ] Create NetworkModule
- [ ] Provide OkHttpClient with interceptors:
  - [ ] AuthInterceptor
  - [ ] HttpLoggingInterceptor (debug only)
  - [ ] RetryInterceptor
- [ ] Provide Retrofit instance
- [ ] Provide DriveApiService
- [ ] Configure timeouts and connection pool

#### AuthModule
- [ ] Create AuthModule
- [ ] Provide GoogleSignInClient
- [ ] Provide GoogleSignInOptions
- [ ] Provide SecureTokenManager
- [ ] Provide AuthRepository binding

#### RepositoryModule
- [ ] Create RepositoryModule
- [ ] Bind SyncRepository implementation
- [ ] Bind AuthRepository implementation
- [ ] Provide other repository bindings

#### WorkerModule
- [ ] Configure HiltWorkerFactory
- [ ] Provide WorkManager instance
- [ ] Provide SyncScheduler

### FolderSyncApp Application Class
- [ ] Create FolderSyncApp extending Application
- [ ] Add @HiltAndroidApp annotation
- [ ] Initialize WorkManager with HiltWorkerFactory
- [ ] Configure logging (Timber for debug)
- [ ] Initialize crash reporting (optional)
- [ ] Set up strict mode for debug builds
- [ ] Initialize any required SDKs

### AndroidManifest Permissions
- [ ] Add INTERNET permission
- [ ] Add ACCESS_NETWORK_STATE permission
- [ ] Add RECEIVE_BOOT_COMPLETED permission
- [ ] Add POST_NOTIFICATIONS permission (API 33+)
- [ ] Add FOREGROUND_SERVICE permission
- [ ] Add FOREGROUND_SERVICE_DATA_SYNC permission
- [ ] Add READ_EXTERNAL_STORAGE (legacy, if needed)
- [ ] Add WRITE_EXTERNAL_STORAGE (legacy, if needed)

### AndroidManifest Components
- [ ] Declare Application class
- [ ] Declare all Activities with themes
- [ ] Declare BroadcastReceivers (BootReceiver)
- [ ] Declare Services if needed
- [ ] Configure FileProvider for file sharing
- [ ] Add intent filters for deep links (optional)
- [ ] Configure backup rules

### build.gradle.kts Dependencies
- [ ] Configure Kotlin and Android plugins
- [ ] Set compileSdk, minSdk, targetSdk
- [ ] Configure signing configs
- [ ] Add Jetpack Compose dependencies
- [ ] Add Hilt dependencies
- [ ] Add Room dependencies
- [ ] Add WorkManager dependencies
- [ ] Add Retrofit/OkHttp dependencies
- [ ] Add Google Sign-In dependencies
- [ ] Add Navigation Compose
- [ ] Add DataStore
- [ ] Add Timber for logging
- [ ] Add testing dependencies (JUnit, Mockk, Turbine)
- [ ] Configure version catalogs

### Gradle Version Catalog (libs.versions.toml)
- [ ] Define version variables
- [ ] Define library aliases
- [ ] Define bundle groups
- [ ] Define plugin aliases

### proguard-rules.pro
- [ ] Keep Retrofit models
- [ ] Keep Room entities
- [ ] Keep Hilt generated code
- [ ] Keep Google Sign-In classes
- [ ] Keep Kotlin metadata
- [ ] Keep coroutine internals
- [ ] Add rules for OkHttp
- [ ] Add rules for Moshi/Gson
- [ ] Test release build thoroughly

### Google Cloud Console Setup Instructions
- [ ] Create Google Cloud Project
- [ ] Enable Google Drive API
- [ ] Create OAuth 2.0 credentials (Android type)
- [ ] Add package name and SHA-1 fingerprint
- [ ] Configure OAuth consent screen
- [ ] Add required scopes:
  - [ ] https://www.googleapis.com/auth/drive.file
  - [ ] https://www.googleapis.com/auth/drive.readonly (optional)
- [ ] Download and configure credentials
- [ ] Document setup steps for new developers

### Project Structure
- [ ] Create package structure:
  ```
  com.foldersync
  ├── data
  │   ├── local
  │   │   ├── database
  │   │   └── datastore
  │   ├── remote
  │   │   └── drive
  │   └── repository
  ├── di
  ├── domain
  │   ├── model
  │   ├── repository
  │   └── usecase
  ├── ui
  │   ├── theme
  │   ├── navigation
  │   ├── home
  │   ├── settings
  │   ├── folder
  │   └── components
  ├── worker
  └── util
  ```
- [ ] Create base classes (BaseViewModel, etc.)
- [ ] Set up result/resource wrapper classes
- [ ] Configure Kotlin code style

### Build Variants
- [ ] Configure debug build type
- [ ] Configure release build type
- [ ] Add staging/qa build type (optional)
- [ ] Configure build config fields
- [ ] Set up different API endpoints per variant

## Acceptance Criteria
- [ ] App compiles without errors
- [ ] Hilt DI graph is complete
- [ ] All modules provide correct dependencies
- [ ] Release build works with ProGuard
- [ ] Google Sign-In works with configured credentials
- [ ] WorkManager initializes correctly
- [ ] All permissions are properly declared
- [ ] App passes lint checks

## Dependencies on Other Components
| Component | Dependency Type | Description |
|-----------|-----------------|-------------|
| All Components | Provider | Infrastructure supports all features |

## Estimated Effort
| Task Category | Hours |
|---------------|-------|
| Hilt DI Modules | 6 |
| Application Class | 2 |
| AndroidManifest | 2 |
| Gradle Dependencies | 4 |
| Version Catalog | 2 |
| ProGuard Rules | 3 |
| Google Cloud Setup | 3 |
| Project Structure | 2 |
| Build Variants | 2 |
| Testing & Verification | 4 |
| **Total** | **30** |

## Technical Notes
- Use `@Binds` for interface implementations over `@Provides`
- Consider using Anvil for Hilt code generation optimization
- Set up LeakCanary for debug memory leak detection
- Configure baseline profiles for performance
- Use R8 full mode for better optimization
- Consider using App Startup library for initialization
- Document all environment variables and secrets

## Key Dependencies (versions may vary)
```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
implementation("androidx.activity:activity-compose:1.8.2")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
kapt("com.google.dagger:hilt-compiler:2.50")
implementation("androidx.hilt:hilt-work:1.1.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Google Sign-In
implementation("com.google.android.gms:play-services-auth:20.7.0")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")
```
