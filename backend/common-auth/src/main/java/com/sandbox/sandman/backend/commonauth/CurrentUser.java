package com.sandbox.sandman.backend.commonauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the caller's internal users.id (chat_app.users) from the JWT decoded by JwtAuthFilter.
 * Inject into controllers; never accept caller identity from request body or path.
 */
@Component
public class CurrentUser {

    public static final String ATTR_USER_ID = "commonauth.userId";
    public static final String ATTR_SUPABASE_UID = "commonauth.supabaseUid";

    public Long requireUserId() {
        Long id = userIdOrNull();
        if (id == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return id;
    }

    public Long userIdOrNull() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        Object v = req.getAttribute(ATTR_USER_ID);
        return v instanceof Long ? (Long) v : null;
    }

    public String supabaseUidOrNull() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        Object v = req.getAttribute(ATTR_SUPABASE_UID);
        return v instanceof String ? (String) v : null;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
