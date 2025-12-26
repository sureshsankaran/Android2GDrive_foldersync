# Google Drive API Component

## Overview
Implement Google Drive REST API v3 integration for file operations. This component handles all communication with Google Drive including listing, uploading, downloading, updating, and deleting files with proper error handling, rate limiting, and exponential backoff.

## Tasks

### DriveApiService Setup
- [ ] Add Retrofit and OkHttp dependencies
- [ ] Create DriveApiService interface with Retrofit annotations
- [ ] Configure base URL (https://www.googleapis.com/drive/v3/)
- [ ] Add Moshi/Gson converter for JSON parsing
- [ ] Create OkHttpClient with auth interceptor
- [ ] Implement request/response logging (debug builds)
- [ ] Configure timeouts (connect, read, write)

### Authentication Interceptor
- [ ] Create AuthInterceptor class
- [ ] Inject access token into Authorization header
- [ ] Handle 401 responses with token refresh
- [ ] Retry request after token refresh
- [ ] Propagate auth failures to UI

### DriveFileManager Implementation
- [ ] Create DriveFileManager class
- [ ] Inject DriveApiService and AuthRepository
- [ ] Implement suspend functions for all operations
- [ ] Map API responses to domain models
- [ ] Handle API errors with custom exceptions
- [ ] Implement pagination for large result sets

### List Files in Folder
- [ ] Implement listFiles(folderId: String) function
- [ ] Build query: `'folderId' in parents and trashed = false`
- [ ] Request fields: id, name, mimeType, modifiedTime, size, md5Checksum
- [ ] Handle pagination with nextPageToken
- [ ] Support orderBy parameter (name, modifiedTime)
- [ ] Filter by mimeType if needed
- [ ] Return Flow<List<DriveFile>>

### Upload File (Multipart)
- [ ] Implement uploadFile(localUri: Uri, folderId: String)
- [ ] Create multipart request with metadata and content
- [ ] Use resumable upload for files > 5MB
- [ ] Set appropriate mimeType from file extension
- [ ] Handle upload progress callbacks
- [ ] Implement chunked upload for large files
- [ ] Return uploaded file metadata

### Download File
- [ ] Implement downloadFile(fileId: String, destinationUri: Uri)
- [ ] Use media download endpoint
- [ ] Stream content directly to file (memory efficient)
- [ ] Handle download progress callbacks
- [ ] Support partial downloads (range requests)
- [ ] Verify checksum after download
- [ ] Handle download interruption/resume

### Update File
- [ ] Implement updateFile(fileId: String, localUri: Uri)
- [ ] Update file content with PATCH request
- [ ] Update metadata if changed (name, etc.)
- [ ] Handle version conflicts (use etag)
- [ ] Return updated file metadata

### Delete File
- [ ] Implement deleteFile(fileId: String)
- [ ] Use DELETE endpoint (permanent delete)
- [ ] Alternatively, move to trash (safer)
- [ ] Handle "file not found" gracefully
- [ ] Confirm deletion success

### Create Folder
- [ ] Implement createFolder(name: String, parentId: String)
- [ ] Set mimeType to application/vnd.google-apps.folder
- [ ] Return created folder metadata
- [ ] Handle duplicate folder names

### Rate Limiting and Exponential Backoff
- [ ] Implement RateLimiter class
- [ ] Track API quota usage
- [ ] Implement exponential backoff for 429/503 errors
- [ ] Configure max retries (default: 5)
- [ ] Add jitter to backoff delays
- [ ] Log rate limit events
- [ ] Implement circuit breaker pattern (optional)

## Acceptance Criteria
- [ ] All Drive API operations work correctly
- [ ] Large files (>100MB) upload/download reliably
- [ ] Progress callbacks update UI smoothly
- [ ] Rate limiting prevents quota exhaustion
- [ ] Exponential backoff handles transient errors
- [ ] Network errors are handled gracefully
- [ ] API responses are properly parsed
- [ ] No memory leaks during file transfers

## Dependencies on Other Components
| Component | Dependency Type | Description |
|-----------|-----------------|-------------|
| Authentication | Required | Provides OAuth tokens for API calls |
| App Infrastructure | Required | Hilt DI and network configuration |
| Sync Engine | Dependent | Uses Drive API for sync operations |
| Local Storage | Related | Maps Drive files to local entities |

## Estimated Effort
| Task Category | Hours |
|---------------|-------|
| DriveApiService Setup | 3 |
| Authentication Interceptor | 2 |
| DriveFileManager Implementation | 3 |
| List Files | 3 |
| Upload File | 6 |
| Download File | 5 |
| Update File | 3 |
| Delete File | 1 |
| Create Folder | 1 |
| Rate Limiting & Backoff | 4 |
| Testing & Debugging | 6 |
| **Total** | **37** |

## Technical Notes
- Use `@Streaming` annotation for large file downloads
- Implement `ProgressRequestBody` for upload progress
- Consider using Google's official Drive SDK as alternative
- Handle `UserRecoverableAuthException` for consent required
- Cache folder structure to reduce API calls
- Implement proper cancellation with coroutine cancellation

## API Endpoints Reference
```
GET  /drive/v3/files                    - List files
GET  /drive/v3/files/{fileId}           - Get file metadata
GET  /drive/v3/files/{fileId}?alt=media - Download file
POST /upload/drive/v3/files             - Upload file
PATCH /upload/drive/v3/files/{fileId}   - Update file
DELETE /drive/v3/files/{fileId}         - Delete file
```
