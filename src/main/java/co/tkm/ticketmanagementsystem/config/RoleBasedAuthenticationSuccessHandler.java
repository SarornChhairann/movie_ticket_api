package co.tkm.ticketmanagementsystem.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Sends admins to the back-office dashboard after login; other users use default behavior
 * (saved request if present, otherwise movies listing).
 */
public class RoleBasedAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public RoleBasedAuthenticationSuccessHandler() {
        setDefaultTargetUrl("/movies");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()))) {
            getRedirectStrategy().sendRedirect(request, response,
                    request.getContextPath() + "/admin/dashboard");
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
