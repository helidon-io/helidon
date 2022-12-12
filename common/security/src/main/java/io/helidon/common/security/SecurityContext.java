package io.helidon.common.security;

import java.security.Principal;
import java.util.Optional;

/**
 * Security context.
 * Can be obtained either from context, or from APIs of Helidon components.
 */
public interface SecurityContext<P extends Principal> {
    /**
     * Return true if the user is authenticated.
     * This only cares about USER! not about service. To check if service is authenticated, use
     * {@link #servicePrincipal()} and check the resulting optional.
     *
     * @return {@code true} for authenticated user
     */
    boolean isAuthenticated();

    /**
     * Return true if authorization was handled for current context.
     *
     * @return {@code true} for authorized requests
     */
    boolean isAuthorized();

    /**
     * User principal if user is authenticated.
     *
     * @return current context user principal, or empty if none authenticated
     */
    Optional<P> userPrincipal();

    /**
     * Service principal if service is authenticated.
     *
     * @return current context service principal, or empty if none authenticated
     */
    Optional<P> servicePrincipal();
}
