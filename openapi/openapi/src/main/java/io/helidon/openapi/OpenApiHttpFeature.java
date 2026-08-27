/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.openapi;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.BadRequestException;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.HttpServiceLocator;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Separate feature exists so we correctly handle feature weight.
 */
class OpenApiHttpFeature implements HttpFeature {
    private static final System.Logger LOGGER = System.getLogger(OpenApiHttpFeature.class.getName());
    private static final MediaType[] PREFERRED_MEDIA_TYPES = new MediaType[] {
            MediaTypes.APPLICATION_OPENAPI_YAML,
            MediaTypes.APPLICATION_X_YAML,
            MediaTypes.APPLICATION_YAML,
            MediaTypes.APPLICATION_OPENAPI_JSON,
            MediaTypes.APPLICATION_JSON,
            MediaTypes.TEXT_X_YAML,
            MediaTypes.TEXT_YAML
    };

    private final ConcurrentMap<OpenApiFormat, String> cachedDocuments = new ConcurrentHashMap<>();
    private final OpenApiFeatureConfig config;
    private final OpenApiManager<?> manager;
    private final LazyValue<Object> model;

    OpenApiHttpFeature(OpenApiFeatureConfig config,
                       OpenApiManager<?> manager,
                       LazyValue<Object> model) {
        this.config = config;
        this.manager = manager;
        this.model = model;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        String path = config.webContext();
        HttpRules openApiServiceRules = routing;
        if (!config.permitAll()) {
            SecureHandler secureHandler = SecureHandler.authorize(config.roles().toArray(new String[0]));
            routing.any(path, secureHandler);
            openApiServiceRules = new SecuredRules(routing, secureHandler);
        }
        routing.get(path, this::handle);
        HttpRules serviceRules = openApiServiceRules;
        config.services().forEach(service -> service.setup(serviceRules, path, this::content));
    }

    @SuppressWarnings("unchecked")
    private static <T> String format(OpenApiManager<T> manager, OpenApiFormat format, Object model) {
        return manager.format((T) model, format);
    }

    private void handle(ServerRequest req, ServerResponse res) {
        String format = req.query().first("format").map(String::toLowerCase).orElse(null);
        if (format != null) {
            MediaType contentType = OpenApiFeature.SUPPORTED_FORMATS.get(format.toLowerCase());
            if (contentType == null) {
                throw new BadRequestException(String.format(
                        "Unsupported format: %s, supported formats: %s",
                        format, OpenApiFeature.SUPPORTED_FORMATS.keySet()));
            }
            res.status(Status.OK_200);
            res.header(HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF)
                    .headers().contentType(contentType);
            res.send(content(contentType));
        } else {
            // check if we should delegate to a service
            for (OpenApiService service : config.services()) {
                if (service.supports(req.headers())) {
                    res.next();
                    return;
                }
            }

            HttpMediaType contentType = req.headers()
                    .bestAccepted(PREFERRED_MEDIA_TYPES)
                    .map(HttpMediaType::create)
                    .orElse(null);

            if (contentType == null) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Accepted types not supported: {0}", req.headers().acceptedTypes());
                }
                res.next();
                return;
            }

            res.status(Status.OK_200);
            res.header(HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF)
                    .headers().contentType(contentType);
            res.send(content(contentType));
        }
    }

    private String content(MediaType mediaType) {
        OpenApiFormat format = OpenApiFormat.valueOf(mediaType);
        if (format == OpenApiFormat.UNSUPPORTED) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Requested format {0} not supported", mediaType);
            }
        }
        return cachedDocuments.computeIfAbsent(format, fmt -> format(manager, fmt, model.get()));
    }

    private static final class SecuredRules implements HttpRules {
        private final HttpRules delegate;
        private final SecureHandler secureHandler;

        private SecuredRules(HttpRules delegate, SecureHandler secureHandler) {
            this.delegate = delegate;
            this.secureHandler = secureHandler;
        }

        @Override
        public HttpRules register(HttpService... service) {
            delegate.register(securedServices(service));
            return this;
        }

        @Override
        public HttpRules register(String pathPattern, HttpService... service) {
            delegate.register(pathPattern, securedServices(service));
            return this;
        }

        @Override
        public HttpRules registerLocator(HttpServiceLocator locator) {
            delegate.registerLocator(new SecuredLocator(locator, secureHandler));
            return this;
        }

        @Override
        public HttpRules registerLocator(String pathPattern, HttpServiceLocator locator) {
            delegate.registerLocator(pathPattern, new SecuredLocator(locator, secureHandler));
            return this;
        }

        @Override
        public HttpRules route(HttpRoute route) {
            delegate.route(new SecuredRoute(route, secureHandler));
            return this;
        }

        private HttpService[] securedServices(HttpService[] services) {
            HttpService[] securedServices = new HttpService[services.length];
            for (int i = 0; i < services.length; i++) {
                securedServices[i] = new SecuredService(services[i], secureHandler);
            }
            return securedServices;
        }
    }

    private static final class SecuredLocator implements HttpServiceLocator {
        private final HttpServiceLocator delegate;
        private final SecureHandler secureHandler;
        private final ReentrantLock lock = new ReentrantLock();
        private final int maxServiceCacheSize;
        private volatile Map<HttpService, HttpService> securedServices = new IdentityHashMap<>();
        private volatile boolean stopped;

        private SecuredLocator(HttpServiceLocator delegate, SecureHandler secureHandler) {
            this.delegate = delegate;
            this.secureHandler = secureHandler;
            this.maxServiceCacheSize = delegate.maxServiceCacheSize();
            if (maxServiceCacheSize < 1) {
                throw new IllegalArgumentException("HttpServiceLocator maxServiceCacheSize must be greater than zero");
            }
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            checkRunning();

            Optional<HttpService> service = Objects.requireNonNull(delegate.locate(request),
                                                                   "HttpServiceLocator must not return null");

            checkRunning();
            return service.map(this::securedService);
        }

        @Override
        public int maxServiceCacheSize() {
            return maxServiceCacheSize;
        }

        @Override
        public void beforeStart() {
            lock.lock();
            try {
                stopped = false;
            } finally {
                lock.unlock();
            }
            delegate.beforeStart();
        }

        @Override
        public void afterStart(WebServer webServer) {
            delegate.afterStart(webServer);
        }

        @Override
        public void afterStop() {
            lock.lock();
            try {
                stopped = true;
                securedServices = new IdentityHashMap<>();
            } finally {
                lock.unlock();
            }
            delegate.afterStop();
        }

        private HttpService securedService(HttpService service) {
            Map<HttpService, HttpService> currentServices = securedServices;
            HttpService securedService = currentServices.get(service);
            if (securedService != null) {
                checkRunning();
                return securedService;
            }

            lock.lock();
            try {
                checkRunning();
                currentServices = securedServices;
                securedService = currentServices.get(service);
                if (securedService != null) {
                    return securedService;
                }
                if (currentServices.size() >= maxServiceCacheSize) {
                    throw serviceUnavailable("size of " + maxServiceCacheSize + " exceeded");
                }
                securedService = new SecuredService(service, secureHandler, this);
                Map<HttpService, HttpService> updatedServices = new IdentityHashMap<>(currentServices);
                updatedServices.put(service, securedService);
                securedServices = updatedServices;
                return securedService;
            } finally {
                lock.unlock();
            }
        }

        private void removeFailedService(HttpService service, HttpService securedService) {
            lock.lock();
            try {
                Map<HttpService, HttpService> currentServices = securedServices;
                if (currentServices.get(service) == securedService) {
                    Map<HttpService, HttpService> updatedServices = new IdentityHashMap<>(currentServices);
                    updatedServices.remove(service);
                    securedServices = updatedServices;
                }
            } finally {
                lock.unlock();
            }
        }

        private void checkRunning() {
            if (stopped) {
                throw serviceUnavailable("is stopped");
            }
        }

        private HttpException serviceUnavailable(String reason) {
            return new HttpException("HttpServiceLocator service cache " + reason,
                                     Status.SERVICE_UNAVAILABLE_503,
                                     true);
        }
    }

    private static final class SecuredService implements HttpService {
        private final HttpService delegate;
        private final SecureHandler secureHandler;
        private final SecuredLocator locator;

        private SecuredService(HttpService delegate, SecureHandler secureHandler) {
            this(delegate, secureHandler, null);
        }

        private SecuredService(HttpService delegate, SecureHandler secureHandler, SecuredLocator locator) {
            this.delegate = delegate;
            this.secureHandler = secureHandler;
            this.locator = locator;
        }

        @Override
        public void routing(HttpRules rules) {
            try {
                delegate.routing(new SecuredRules(rules, secureHandler));
            } catch (RuntimeException | Error e) {
                initializationFailed();
                throw e;
            }
        }

        @Override
        public void beforeStart() {
            try {
                delegate.beforeStart();
            } catch (RuntimeException | Error e) {
                initializationFailed();
                throw e;
            }
        }

        @Override
        public void afterStart(WebServer webServer) {
            try {
                delegate.afterStart(webServer);
            } catch (RuntimeException | Error e) {
                initializationFailed();
                throw e;
            }
        }

        @Override
        public void afterStop() {
            delegate.afterStop();
        }

        private void initializationFailed() {
            if (locator != null) {
                locator.removeFailedService(delegate, this);
            }
        }
    }

    private static final class SecuredRoute implements HttpRoute {
        private final HttpRoute delegate;
        private final Handler handler;

        private SecuredRoute(HttpRoute delegate, SecureHandler secureHandler) {
            this.delegate = delegate;
            this.handler = secureHandler.wrap(delegate.handler());
        }

        @Override
        public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
            return delegate.accepts(prologue);
        }

        @Override
        public PathMatchers.MatchResult accepts(HttpPrologue prologue, ServerRequestHeaders headers) {
            return delegate.accepts(prologue, headers);
        }

        @Override
        public Handler handler() {
            return handler;
        }

        @Override
        public Optional<PathMatcher> pathMatcher() {
            return delegate.pathMatcher();
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

}
