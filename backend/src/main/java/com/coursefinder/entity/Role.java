package com.coursefinder.entity;

/** What a user is allowed to do. Stored as text in app_user.role and sent in the "roles" token claim. */
public enum Role {
    USER,
    ADMIN
}
