package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.User;

/** Helpers for the logged-in user and office scoping. */
public final class CurrentUser {

    private CurrentUser() {}

    public static User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return user;
    }

    /** The logged-in user, or null on public endpoints. */
    public static User getOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user ? user : null;
    }

    /** The user's first office id, or null. */
    public static String officeId(User user) {
        return (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty()) ? user.getOfficeIds().get(0) : null;
    }

    /** The caller's company id (null for legacy single-company users). */
    public static String companyId() {
        return get().getCompanyId();
    }

    /** Riders in scope: one office (any role-checked caller) or all riders of the caller's company. */
    public static java.util.List<User> ridersInScope(shortly.mandmcorp.dev.shortly.repository.UserRepository repo, String scopedOfficeId) {
        String company = companyId();
        java.util.List<User> riders = scopedOfficeId == null
                ? repo.findByRoleAndCompanyId(UserRole.RIDER, company)
                : repo.findByRoleAndOfficeIdsContaining(UserRole.RIDER, scopedOfficeId);
        return riders.stream().filter(r -> java.util.Objects.equals(r.getCompanyId(), company)).toList();
    }

    public static boolean isManager(User user) {
        return user.getRole() == UserRole.MANAGER;
    }

    /**
     * Office filter to apply for the current user: managers are always limited to their own office
     * (whatever was requested); admins get the requested office (null = all offices).
     */
    public static String scopedOfficeId(String requestedOfficeId) {
        User user = get();
        if (isManager(user)) {
            String own = officeId(user);
            if (own == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager has no assigned office");
            }
            return own;
        }
        return (requestedOfficeId == null || requestedOfficeId.isBlank()) ? null : requestedOfficeId;
    }

    /** Throws 403 when a manager asks about a rider outside their office. */
    public static void requireRiderInScope(User rider) {
        User user = get();
        if (!isManager(user)) return;
        String own = officeId(user);
        if (own == null || rider.getOfficeIds() == null || !rider.getOfficeIds().contains(own)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rider is not in your office");
        }
    }
}
