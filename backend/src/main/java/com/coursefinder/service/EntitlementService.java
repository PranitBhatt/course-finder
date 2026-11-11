package com.coursefinder.service;

import com.coursefinder.entity.AppUser;
import org.springframework.stereotype.Service;

/**
 * Decides whether a logged-in user may use a feature.
 * <p>
 * This is the single place to switch on the paywall later. Today every logged-in user gets every
 * feature; callers must still ask, so that changing this one method is all a paywall needs.
 */
@Service
public class EntitlementService {

    public boolean hasAccess(AppUser user, Feature feature) {
        return true;
    }
}
