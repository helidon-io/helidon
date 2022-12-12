package io.helidon.nima.webserver.http;

import java.util.Arrays;
import java.util.Optional;

import io.helidon.common.http.ForbiddenException;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpException;
import io.helidon.common.security.SecurityContext;

/**
 * A handler that enforces authentication and/or authorization.
 * When configured, it just validates that security was processed. If not, appropriate exception is thrown.
 */
public final class SecureHandler {
    private static final Handler NO_OP = new NoOpHandler();
    private static final Handler ATN = new AtnHandler();
    /**
     * This endpoint requires security.
     *
     * @param requireAuthentication if this endpoint requires authentication
     * @param requireAuthorization if this endpoint requires authorization
     * @return a new handler that enforces the configured options
     */
    public static Handler create(boolean requireAuthentication, boolean requireAuthorization, String... roleHint) {
        if (requireAuthentication && requireAuthorization) {
            return new AllHandler(roleHint);
        }

        if (requireAuthentication) {
            return ATN;
        }

        if (requireAuthorization) {
            return new AtzHandler(roleHint);
        }

        return NO_OP;
    }

    private static class AtzHandler implements Handler {
        private final String[] roleHint;

        private AtzHandler(String[] roleHint) {
            this.roleHint = roleHint;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);

            if (!securityContext.map(SecurityContext::isAuthorized).orElse(false)) {
                // not authorized in a security provider, and this is default security implementation
                if (roleHint.length > 0) {
                    throw new ForbiddenException("Forbidden. User should be in one of these roles: " + Arrays.toString(roleHint));
                }
                throw new ForbiddenException("Forbidden, security must be enabled ant this endpoint must be authorized.");
            }
            res.next();
        }
    }

    private static final class AtnHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);

            if (!securityContext.map(SecurityContext::isAuthenticated).orElse(false)) {
                // not authenticated in a security provider, and this is default security implementation
                throw new HttpException("Not Authenticated", Http.Status.UNAUTHORIZED_401);
            }

            res.next();
        }
    }

    private static final class AllHandler implements Handler {
        private final String[] roleHint;

        private AllHandler(String[] roleHint) {
            this.roleHint = roleHint;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);

            if (!securityContext.map(SecurityContext::isAuthenticated).orElse(false)) {
                // not authenticated in a security provider, and this is default security implementation
                throw new HttpException("Not Authenticated", Http.Status.UNAUTHORIZED_401);
            }
            if (!securityContext.map(SecurityContext::isAuthorized).orElse(false)) {
                // not authorized in a security provider, and this is default security implementation
                if (roleHint.length > 0) {
                    throw new ForbiddenException("Forbidden. User should have access to " + Arrays.toString(roleHint));
                }
                throw new ForbiddenException("Forbidden, security must be enabled and this endpoint must be authorized.");
            }

            res.next();
        }
    }

    private static final class NoOpHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.next();
        }
    }
}
