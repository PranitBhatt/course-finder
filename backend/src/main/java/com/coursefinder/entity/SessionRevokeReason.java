package com.coursefinder.entity;

/** Why a session was ended early. Stored as text in user_session.revoke_reason. */
public enum SessionRevokeReason {
    /** The user logged out. */
    LOGOUT,
    /** A refresh token of this session was used twice, so someone has a copy of it. */
    REUSE_DETECTED
}
