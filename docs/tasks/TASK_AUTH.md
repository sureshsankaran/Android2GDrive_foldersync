# Authentication Component

## Overview
Implement secure Google Sign-In authentication flow for the FolderSync Android app. This component handles OAuth 2.0 authentication with Google, secure token storage using Android Keystore, automatic token refresh, and user session management.

## Tasks

### Google Sign-In SDK Integration
- [ ] Add Google Sign-In dependencies to build.gradle.kts
- [ ] Configure Google Cloud Console OAuth 2.0 credentials
- [ ] Add SHA-1 fingerprint to Firebase/Google Console
- [ ] Create GoogleSignInClient configuration
- [ ] Implement sign-in intent launcher

### OAuth 2.0 Flow Implementation
- [ ] Request Drive API scopes (drive.file, drive.readonly)
- [ ] Handle GoogleSignInAccount result
- [ ] Extract OAuth tokens from sign-in result
- [ ] Implement error handling for auth failures
- [ ] Handle user cancellation flow

### Token Storage (Android Keystore)
- [ ] Create SecureTokenManager class
- [ ] Implement EncryptedSharedPreferences setup
- [ ] Store access token securely
- [ ] Store refresh token securely
- [ ] Implement token retrieval methods
- [ ] Clear tokens on sign out

### Token Refresh Handling
- [ ] Implement TokenRefreshManager
- [ ] Detect token expiration (401 responses)
- [ ] Implement silent sign-in for token refresh
- [ ] Handle refresh token expiration (re-auth required)
- [ ] Add token refresh interceptor for OkHttp

### AuthRepository Implementation
- [ ] Create AuthRepository interface
- [ ] Implement AuthRepositoryImpl with Hilt
- [ ] Expose auth state as Flow<AuthState>
- [ ] Implement signIn() suspend function
- [ ] Implement signOut() suspend function
- [ ] Implement isAuthenticated() check
- [ ] Implement getCurrentUser() method

### Sign Out Functionality
- [ ] Clear local tokens
- [ ] Revoke Google OAuth tokens
- [ ] Clear cached user data
- [ ] Reset app state to signed-out
- [ ] Navigate to sign-in screen

## Acceptance Criteria
- [ ] User can sign in with Google account
- [ ] App requests only necessary Drive API scopes
- [ ] Tokens are stored securely in Android Keystore
- [ ] Tokens refresh automatically before expiration
- [ ] User can sign out completely
- [ ] Auth state persists across app restarts
- [ ] Error messages are user-friendly
- [ ] No token leakage in logs

## Dependencies on Other Components
| Component          | Dependency Type | Description                            |
| ------------------ | --------------- | -------------------------------------- |
| App Infrastructure | Required        | Hilt DI setup for dependency injection |
| UI Layer           | Required        | Sign-in button and auth state display  |
| Drive API          | Dependent       | Uses auth tokens for API calls         |
| Background Worker  | Dependent       | Needs valid auth for background sync   |

## Estimated Effort
| Task Category                  | Hours  |
| ------------------------------ | ------ |
| Google Sign-In SDK Integration | 3      |
| OAuth 2.0 Flow Implementation  | 4      |
| Token Storage                  | 3      |
| Token Refresh Handling         | 4      |
| AuthRepository Implementation  | 3      |
| Sign Out Functionality         | 2      |
| Testing & Debugging            | 4      |
| **Total**                      | **23** |

## Technical Notes
- Use `androidx.credentials` for modern Credential Manager API
- Consider migrating to One Tap sign-in for better UX
- Implement proper error mapping for Google API exceptions
- Use `GoogleSignInOptions.Builder` with `requestScopes()` for Drive access
