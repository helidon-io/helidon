/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.security.SecurityContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.spi.ProtocolUpgradeHandler;

/**
 * A handler that enforces authentication and/or authorization.
 * When configured, it just validates that security was processed. If not, appropriate exception is thrown.
 */
public final class SecureHandler implements Handler, ProtocolUpgradeHandler {
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
     * @param roleHint optional role names; when specified, the built-in security feature requires the user to be in
     *                 at least one of these roles
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
     * @param roleHint optional role names; when specified, the built-in security feature requires the user to be in
     *                 at least one of these roles
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

    /**
     * Creates a new service locator that applies the configured security requirements to each located service.
     * <p>
     * WebServer caches the located service's original identity and applies the security wrapper only after admitting that
     * identity to the locator's service cache.
     *
     * @param locator service locator to wrap
     * @return a new wrapped service locator
     */
    public HttpServiceLocator wrap(HttpServiceLocator locator) {
        return new WrappedLocator(this, Objects.requireNonNull(locator));
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) throws Exception {
        if (doHandle(req, res)) {
            res.next();
        }
    }

    @Api.Internal
    @Override
    public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) throws Exception {
        handle(req, res);
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
            if (roleHint.length != 0 || !securityContext.map(SecurityContext::isAuthorized).orElse(false)) {
                // not authorized in a security provider, or route roles still need to be validated
                if (!req.security().authorize(req, res, roleHint)) {
                    return false;
                }
            }
        }

        return true;
    }

    private HttpService wrap(HttpService service) {
        return new WrappedService(service, this);
    }

    private HttpRoute wrap(HttpRoute route) {
        return new WrappedRoute(route, this);
    }

    private static final class WrappedLocator implements HttpServiceLocator {
        private final SecureHandler secureHandler;
        private final HttpServiceLocator delegate;

        private WrappedLocator(SecureHandler secureHandler, HttpServiceLocator delegate) {
            this.secureHandler = secureHandler;
            this.delegate = delegate;
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            return delegate.locate(request).map(secureHandler::wrap);
        }

        @Override
        public int maxServiceCacheSize() {
            return delegate.maxServiceCacheSize();
        }

        @Override
        public void beforeStart() {
            delegate.beforeStart();
        }

        @Override
        public void afterStart(WebServer webServer) {
            delegate.afterStart(webServer);
        }

        @Override
        public void afterStop() {
            delegate.afterStop();
        }

    }

    private static final class WrappedRules implements HttpRules {
        private final HttpRules delegate;
        private final SecureHandler secureHandler;

        private WrappedRules(HttpRules delegate, SecureHandler secureHandler) {
            this.delegate = delegate;
            this.secureHandler = secureHandler;
        }

        @Override
        public HttpRules register(HttpService... services) {
            delegate.register(wrappedServices(services));
            return this;
        }

        @Override
        public HttpRules register(String pathPattern, HttpService... services) {
            delegate.register(pathPattern, wrappedServices(services));
            return this;
        }

        @Override
        public HttpRules registerLocator(HttpServiceLocator locator) {
            delegate.registerLocator(secureHandler.wrap(locator));
            return this;
        }

        @Override
        public HttpRules registerLocator(String pathPattern, HttpServiceLocator locator) {
            delegate.registerLocator(pathPattern, secureHandler.wrap(locator));
            return this;
        }

        @Override
        public HttpRules route(HttpRoute route) {
            delegate.route(secureHandler.wrap(route));
            return this;
        }

        private HttpService[] wrappedServices(HttpService[] services) {
            HttpService[] wrappedServices = new HttpService[services.length];
            for (int i = 0; i < services.length; i++) {
                wrappedServices[i] = secureHandler.wrap(services[i]);
            }
            return wrappedServices;
        }
    }

    private static final class WrappedService implements HttpService, LocatedServiceCacheKey {
        private final HttpService delegate;
        private final SecureHandler secureHandler;

        private WrappedService(HttpService delegate, SecureHandler secureHandler) {
            this.delegate = delegate;
            this.secureHandler = secureHandler;
        }

        @Override
        public void routing(HttpRules rules) {
            delegate.routing(new WrappedRules(rules, secureHandler));
        }

        @Override
        public void beforeStart() {
            delegate.beforeStart();
        }

        @Override
        public void afterStart(WebServer webServer) {
            delegate.afterStart(webServer);
        }

        @Override
        public void afterStop() {
            delegate.afterStop();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof WrappedService that) || secureHandler != that.secureHandler) {
                return false;
            }
            if (delegate instanceof WrappedService wrappedDelegate) {
                return wrappedDelegate.equals(that.delegate);
            }
            return delegate == that.delegate;
        }

        @Override
        public int hashCode() {
            int delegateHash = delegate instanceof WrappedService wrappedDelegate
                    ? wrappedDelegate.hashCode()
                    : System.identityHashCode(delegate);
            return 31 * delegateHash + System.identityHashCode(secureHandler);
        }
    }

    private static final class WrappedRoute extends HttpRouteWrap {
        private final Handler handler;

        private WrappedRoute(HttpRoute route, SecureHandler secureHandler) {
            super(route);
            this.handler = secureHandler.wrap(route.handler());
        }

        @Override
        public Handler handler() {
            return handler;
        }
    }

    private static final class WrappedHandler implements Handler, ProtocolUpgradeHandler {
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

        @Api.Internal
        @Override
        public void handleProtocolUpgrade(ServerRequest req, ServerResponse res) throws Exception {
            if (!secureHandler.doHandle(req, res)) {
                return;
            }
            if (handler instanceof ProtocolUpgradeHandler upgradeHandler) {
                upgradeHandler.handleProtocolUpgrade(req, res);
            } else {
                res.next();
            }
        }
    }
}
