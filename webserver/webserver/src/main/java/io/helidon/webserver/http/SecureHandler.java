/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webserver.http;

import java.util.Optional;

import io.helidon.common.security.SecurityContext;

/**
 * A handler that enforces authentication and/or authorization.
 * When configured, it just validates that security was processed. If not, appropriate exception is thrown.
 */
public final class SecureHandler implements Handler {
    private static final String[] NO_ROLES = new String[0];

    private final boolean authenticate;
    private final boolean authorize;
    private final String[] roleHint;

    private SecureHandler(boolean authenticate, boolean authorize, String[] roleHint) {
        this.authenticate = authenticate;
        this.authorize = authorize;
        this.roleHint = roleHint;
    }

    /**
     * Create a security handler that enforces authentication.
     *
     * @return a new handler that requires authentication
     */
    public static SecureHandler authenticate() {
        return new SecureHandler(true, false, NO_ROLES);
    }

    /**
     * Create a security handler that enforces authorization.
     *
     * @param roleHint optional hint for role names the user is expected to be in
     * @return a new handler that requires authorization
     */
    public static SecureHandler authorize(String... roleHint) {
        return new SecureHandler(false, true, roleHint);
    }

    /**
     * Add authentication requirement and create a new handler with combined setup.
     *
     * @return a new handler that combines the existing authorization requirements and adds authentication requirement
     */
    public SecureHandler andAuthenticate() {
        return new SecureHandler(true, authorize, roleHint);
    }

    /**
     * Add authorization requirement and create a new handler with combined setup.
     *
     * @param roleHint optional hint for role names the user is expected to be in
     * @return a new handler that combines the existing authentication requirements and adds authorization requirement
     */
    public SecureHandler andAuthorize(String... roleHint) {
        return new SecureHandler(authenticate, true, roleHint);
    }

    /**
     * Creates a new handler that uses the configured security requirements and wraps an existing handler to be executed
     * when security is checked.
     *
     * @param handler handler to invoke when security requirements are met
     * @return a new wrapped handler
     */
    public Handler wrap(Handler handler) {
        return new WrappedHandler(this, handler);
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) throws Exception {
        if (doHandle(req, res)) {
            res.next();
        }
    }

    private boolean doHandle(ServerRequest req, ServerResponse res) {
        Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);

        if (authenticate) {
            if (!securityContext.map(SecurityContext::isAuthenticated).orElse(false)) {
                // not authenticated in a security provider, and this is default security implementation
                if (!req.security().authenticate(req, res, true)) {
                    return false;
                }
            }
        }

        if (authorize) {
            if (!securityContext.map(SecurityContext::isAuthorized).orElse(false)) {
                // not authorized in a security provider
                if (!req.security().authorize(req, res, roleHint)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static class WrappedHandler implements Handler {
        private final SecureHandler secureHandler;
        private final Handler handler;

        private WrappedHandler(SecureHandler secureHandler, Handler handler) {
            this.secureHandler = secureHandler;

            this.handler = handler;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) throws Exception {
            if (secureHandler.doHandle(req, res)) {
                handler.handle(req, res);
            }
        }
    }
}
