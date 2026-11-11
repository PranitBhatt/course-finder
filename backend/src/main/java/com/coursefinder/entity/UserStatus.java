package com.coursefinder.entity;

/** Stored as text in app_user.status. */
public enum UserStatus {
    ACTIVE,
    /** Can't log in or refresh. Set by hand in the database for now. */
    DISABLED
}
