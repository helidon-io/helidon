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

package io.helidon.microprofile.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.InternalServerException;
import io.helidon.common.uri.UriPath;
import io.helidon.microprofile.server.HelidonHK2InjectionManagerFactory.InjectionManagerWrapper;
import io.helidon.nima.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

import static org.glassfish.jersey.CommonProperties.PROVIDER_DEFAULT_DISABLE;
import static org.glassfish.jersey.server.ServerProperties.RESOURCE_VALIDATION_IGNORE_ERRORS;
import static org.glassfish.jersey.server.ServerProperties.WADL_FEATURE_DISABLE;

class JaxRsService implements HttpService {
    /**
     * If set to {@code "true"}, Jersey will ignore responses in exceptions.
     */
    static final String IGNORE_EXCEPTION_RESPONSE = "jersey.config.client.ignoreExceptionResponse";

    private static final System.Logger LOGGER = System.getLogger(JaxRsService.class.getName());
    private static final Type REQUEST_TYPE = (new GenericType<Ref<ServerRequest>>() { }).getType();
    private static final Type RESPONSE_TYPE = (new GenericType<Ref<ServerResponse>>() { }).getType();
    private static final Set<InjectionManager> INJECTION_MANAGERS = Collections.newSetFromMap(new WeakHashMap<>());

    private final ApplicationHandler appHandler;
    private final ResourceConfig resourceConfig;
    private final Container container;
    private final Application application;

    private JaxRsService(ResourceConfig resourceConfig,
                         ApplicationHandler appHandler,
                         Container container) {
        this.resourceConfig = resourceConfig;
        this.appHandler = appHandler;
        this.container = container;
        this.application = getApplication(resourceConfig);
    }

    static JaxRsService create(ResourceConfig resourceConfig, InjectionManager injectionManager) {
        resourceConfig.property(PROVIDER_DEFAULT_DISABLE, "ALL");
        resourceConfig.property(WADL_FEATURE_DISABLE, "true");

        // At least a temporary workaround for TCK bug https://github.com/eclipse/microprofile-open-api/issues/557.
        String resourceValidationIgnoreErrors = System.getProperty(RESOURCE_VALIDATION_IGNORE_ERRORS);
        if (resourceValidationIgnoreErrors != null) {
            resourceConfig.property(RESOURCE_VALIDATION_IGNORE_ERRORS, Boolean.parseBoolean(resourceValidationIgnoreErrors));
        }

        InjectionManager ij = injectionManager == null ? null : new InjectionManagerWrapper(injectionManager, resourceConfig);
        ApplicationHandler appHandler = new ApplicationHandler(resourceConfig,
                                                               new WebServerBinder(),
                                                               ij);
        Container container = new HelidonJerseyContainer(appHandler);
        Config config = ConfigProvider.getConfig();

        // This configuration via system properties is for the Jersey Client API. Any
        // response in an exception will be mapped to an empty one to prevent data leaks
        // unless property in config is set to false.
        // See https://github.com/eclipse-ee4j/jersey/pull/4641.
        if (!System.getProperties().contains(IGNORE_EXCEPTION_RESPONSE)) {
            System.setProperty(CommonProperties.ALLOW_SYSTEM_PROPERTIES_PROVIDER, "true");
            String ignore = config.getOptionalValue(IGNORE_EXCEPTION_RESPONSE, String.class).orElse("true");
            System.setProperty(IGNORE_EXCEPTION_RESPONSE, ignore);
        }

        return new JaxRsService(resourceConfig, appHandler, container);
    }

    static String basePath(UriPath path) {
        String reqPath = path.path();
        String absPath = path.absolute().path();
        String basePath = absPath.substring(0, absPath.length() - reqPath.length() + 1);

        if (absPath.isEmpty() || basePath.isEmpty()) {
            return "/";
        } else if (basePath.charAt(basePath.length() - 1) != '/') {
            return basePath + "/";
        } else {
            return basePath;
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.any(this::handle);
    }

    @Override
    public void beforeStart() {
        appHandler.onStartup(container);
        INJECTION_MANAGERS.add(appHandler.getInjectionManager());
    }

    @Override
    public void afterStop() {
        try {
            InjectionManager ij = appHandler.getInjectionManager();
            if (INJECTION_MANAGERS.remove(ij)) {
                appHandler.onShutdown(container);
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Exception during shutdown of Jersey", e);
            }
            LOGGER.log(Level.WARNING, "Exception while shutting down Jersey's application handler " + e.getMessage());
        }
    }

    /**
     * Extracts the actual {@code Application} instance.
     *
     * @param resourceConfig the resource config
     * @return the application
     */
    private static Application getApplication(ResourceConfig resourceConfig) {
        Application application = resourceConfig;
        while (application instanceof ResourceConfig) {
            Application wrappedApplication = ((ResourceConfig) application).getApplication();
            if (wrappedApplication == application) {
                break;
            }
            application = wrappedApplication;
        }
        return application;
    }

    private static URI baseUri(ServerRequest req) {
        String uri = (req.isSecure() ? "https" : "http")
                + "://" + req.authority()
                + basePath(req.path());

        return URI.create(uri);
    }

    private void handle(ServerRequest req, ServerResponse res) {
        Contexts.runInContext(req.context(), () -> doHandle(req.context(), req, res));
    }

    private void doHandle(Context ctx, ServerRequest req, ServerResponse res) {
        URI baseUri = baseUri(req);
        URI requestUri;

        String rawPath = req.path().rawPath();
        rawPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (req.query().isEmpty()) {
            requestUri = baseUri.resolve(rawPath);
        } else {
            requestUri = baseUri.resolve(rawPath + "?" + req.query().rawValue());
        }

        ContainerRequest requestContext = new ContainerRequest(baseUri,
                                                               requestUri,
                                                               req.prologue().method().text(),
                                                               new HelidonMpSecurityContext(), new MapPropertiesDelegate(),
                                                               resourceConfig);

        for (HeaderValue header : req.headers()) {
            requestContext.headers(header.name(),
                                   header.allValues());
        }

        JaxRsResponseWriter writer = new JaxRsResponseWriter(res);
        requestContext.setWriter(writer);
        requestContext.setEntityStream(req.content().inputStream());
        requestContext.setProperty("io.helidon.jaxrs.remote-host", req.remotePeer().host());
        requestContext.setProperty("io.helidon.jaxrs.remote-port", req.remotePeer().port());
        requestContext.setRequestScopedInitializer(ij -> {
            ij.<Ref<ServerRequest>>getInstance(REQUEST_TYPE).set(req);
            ij.<Ref<ServerResponse>>getInstance(RESPONSE_TYPE).set(res);
        });

        Optional<KeyPerformanceIndicatorSupport.DeferrableRequestContext> kpiMetricsContext =
                req.context().get(KeyPerformanceIndicatorSupport.DeferrableRequestContext.class);
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "[" + req.serverSocketId() + " " + req.socketId() + "] Handling in Jersey started");
        }

        // Register Application instance in context in case there is more
        // than one application. Class SecurityFilter requires this.
        ctx.register(application);

        try {
            kpiMetricsContext.ifPresent(KeyPerformanceIndicatorSupport.DeferrableRequestContext::requestProcessingStarted);
            appHandler.handle(requestContext);
            writer.await();
        } catch (UncheckedIOException e) {
            throw e;
        } catch (io.helidon.common.http.NotFoundException | NotFoundException e) {
            // continue execution, maybe there is a non-JAX-RS route (such as static content)
            res.next();
        } catch (Exception e) {
            throw new InternalServerException("Internal exception in JAX-RS processing", e);
        }
    }

    private static class HelidonJerseyContainer implements Container {
        private final ApplicationHandler applicationHandler;

        private HelidonJerseyContainer(ApplicationHandler appHandler) {
            this.applicationHandler = appHandler;
        }

        @Override
        public ResourceConfig getConfiguration() {
            return applicationHandler.getConfiguration();
        }

        @Override
        public ApplicationHandler getApplicationHandler() {
            return applicationHandler;
        }

        @Override
        public void reload() {
            // no op
            throw new UnsupportedOperationException("Reloading is not supported in Helidon");
        }

        @Override
        public void reload(ResourceConfig configuration) {
            // no op
            throw new UnsupportedOperationException("Reloading is not supported in Helidon");
        }
    }

    private static class HelidonMpSecurityContext implements SecurityContext {
        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return null;
        }
    }

    private static class JaxRsResponseWriter implements ContainerResponseWriter {
        private final CountDownLatch cdl = new CountDownLatch(1);
        private final ServerResponse res;
        private OutputStream outputStream;

        private JaxRsResponseWriter(ServerResponse res) {
            this.res = res;
        }

        @Override
        public OutputStream writeResponseStatusAndHeaders(long contentLengthParam,
                                                          ContainerResponse containerResponse) throws ContainerException {
            long contentLength = contentLengthParam;
            if (contentLength <= 0) {
                String headerString = containerResponse.getHeaderString("Content-Length");
                if (headerString != null) {
                    contentLength = Long.parseLong(headerString);
                }
            }
            for (Map.Entry<String, List<String>> entry : containerResponse.getStringHeaders().entrySet()) {
                String name = entry.getKey();
                List<String> values = entry.getValue();
                if (values.size() == 1) {
                    res.header(Header.create(Header.create(name), values.get(0)));
                } else {
                    res.header(Header.create(Header.create(entry.getKey()), entry.getValue()));
                }
            }
            Response.StatusType statusInfo = containerResponse.getStatusInfo();
            res.status(Http.Status.create(statusInfo.getStatusCode(), statusInfo.getReasonPhrase()));

            if (contentLength > 0) {
                res.header(Header.create(Header.CONTENT_LENGTH, String.valueOf(contentLength)));
            }
            this.outputStream = new NoFlushOutputStream(res.outputStream());
            return outputStream;
        }

        @Override
        public boolean suspend(long timeOut, TimeUnit timeUnit, TimeoutHandler timeoutHandler) {
            if (timeOut != 0) {
                throw new UnsupportedOperationException("Currently, time limited suspension is not supported!");
            }
            return true;
        }

        @Override
        public void setSuspendTimeout(long l, TimeUnit timeUnit) throws IllegalStateException {
            throw new UnsupportedOperationException("Currently, extending the suspension time is not supported!");
        }

        @Override
        public void commit() {
            try {
                if (outputStream == null) {
                    res.outputStream().close();
                } else {
                    outputStream.close();
                }
                cdl.countDown();
            } catch (IOException e) {
                cdl.countDown();
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void failure(Throwable throwable) {
            cdl.countDown();

            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            throw new InternalServerException("Failed to process JAX-RS request", throwable);
        }

        @Override
        public boolean enableResponseBuffering() {
            // Jersey should not try to do the buffering
            return false;
        }

        public void await() {
            try {
                cdl.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to wait for Jersey to write response");
            }
        }
    }

    private static class NoFlushOutputStream extends OutputStream {
        private final OutputStream delegate;

        private NoFlushOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() {
            // intentional no-op, flush did not work nicely with Jersey
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }
    }
}
