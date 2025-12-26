package com.foldersync.domain.model

/**
 * Strategy for resolving sync conflicts
 */
enum class ConflictResolutionStrategy {
    /** Always use the local version */
    KEEP_LOCAL,
    
    /** Always use the remote/cloud version */
    KEEP_REMOTE,
    
    /** Keep whichever was modified more recently */
    KEEP_NEWEST,
    
    /** Keep both versions (rename the conflicting one) */
    KEEP_BOTH,
    
    /** Ask the user to resolve manually */
    ASK_USER
}
