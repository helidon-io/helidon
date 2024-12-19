/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.InternalServerException;
import io.helidon.http.Status;
import io.helidon.microprofile.server.HelidonHK2InjectionManagerFactory.InjectionManagerWrapper;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

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
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

class JaxRsService implements HttpService {
    /**
     * If set to {@code "true"}, Jersey will ignore responses in exceptions.
     */
    static final String IGNORE_EXCEPTION_RESPONSE = "jersey.config.client.ignoreExceptionResponse";
    static final String SUPPRESS_DATASOURCE_PROVIDER = "jersey.config.server.suppressDataSourceProvider";

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

        Config config = ConfigProvider.getConfig();

        // Silence warnings from Jersey. See 9019. Allow overriding to pass tck
        Boolean suppressDatasourceProvider = config.getOptionalValue(SUPPRESS_DATASOURCE_PROVIDER, Boolean.class).orElse(true);
        if (!resourceConfig.hasProperty(CommonProperties.PROVIDER_DEFAULT_DISABLE) && suppressDatasourceProvider) {
            resourceConfig.addProperties(Map.of(CommonProperties.PROVIDER_DEFAULT_DISABLE, "DATASOURCE"));
        }
        if (!resourceConfig.hasProperty(ServerProperties.WADL_FEATURE_DISABLE)) {
            resourceConfig.addProperties(Map.of(ServerProperties.WADL_FEATURE_DISABLE, "true"));
        }

        InjectionManager ij = injectionManager == null ? null : new InjectionManagerWrapper(injectionManager, resourceConfig);
        ApplicationHandler appHandler = new ApplicationHandler(resourceConfig,
                                                               new WebServerBinder(),
                                                               ij);
        Container container = new HelidonJerseyContainer(appHandler);

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

    private static String basePath(UriPath path) {
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

    private void handle(ServerRequest req, ServerResponse res) {
        Context context = req.context();

        // make these available in context for ServerCdiExtension
        context.supply(ServerRequest.class, () -> req);
        context.supply(ServerResponse.class, () -> res);

        // call doHandle in active context
        Contexts.runInContext(context, () -> doHandle(context, req, res));
    }

    private void doHandle(Context ctx, ServerRequest req, ServerResponse res) {
        BaseUriRequestUri uris = BaseUriRequestUri.resolve(req);
        ContainerRequest requestContext = new ContainerRequest(uris.baseUri,
                                                               uris.requestUri,
                                                               req.prologue().method().text(),
                                                               new HelidonMpSecurityContext(),
                                                               new MapPropertiesDelegate(),
                                                               resourceConfig);
        /*
         MP CORS supports needs a way to obtain the UriInfo from the request context.
         */
        requestContext.setProperty(UriInfo.class.getName(), ((Supplier<UriInfo>) req::requestedUri));

        for (Header header : req.headers()) {
            requestContext.headers(header.name(),
                                   header.allValues());
        }

        JaxRsResponseWriter writer = new JaxRsResponseWriter(res);
        requestContext.setWriter(writer);
        requestContext.setEntityStream(new LazyInputStream(req));
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
            if (res.status() == Status.NOT_FOUND_404 && requestContext.getUriInfo().getMatchedResourceMethod() == null) {
                // Jersey will not throw an exception, it will complete the request - but we must
                // continue looking for the next route
                // this is a tricky piece of code - the next can only be called if reset was successful
                // reset may be impossible if data has already been written over the network
                if (res instanceof RoutingResponse routing) {
                    if (routing.reset()) {
                        res.status(Status.OK_200);
                        routing.next();
                    }
                }
            }
        } catch (UncheckedIOException e) {
            throw e;
        } catch (io.helidon.http.NotFoundException | NotFoundException e) {
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
                    res.header(HeaderValues.create(HeaderNames.create(name), values.get(0)));
                } else {
                    res.header(HeaderValues.create(entry.getKey(), entry.getValue()));
                }
            }
            Response.StatusType statusInfo = containerResponse.getStatusInfo();
            res.status(Status.create(statusInfo.getStatusCode(), statusInfo.getReasonPhrase()));

            if (contentLength > 0) {
                res.header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, String.valueOf(contentLength)));
            }
            // in case there is an exception during close operation, we would lose the information and wait indefinitely
            this.outputStream = new ReleaseLatchStream(cdl, res.outputStream());
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
            } catch (Throwable e) {
                // always release on commit, regardless of what happened
                cdl.countDown();
                throw e;
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
            return true;        // enable buffering in Jersey
        }

        void await() {
            try {
                cdl.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to wait for Jersey to write response");
            }
        }
    }

    private static class ReleaseLatchStream extends OutputStream {
        private final CountDownLatch cdl;
        private final OutputStream delegate;

        private ReleaseLatchStream(CountDownLatch cdl, OutputStream delegate) {
            this.cdl = cdl;
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
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
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                cdl.countDown();
            }
        }
    }

    private static class BaseUriRequestUri {
        private final URI baseUri;
        private final URI requestUri;

        private BaseUriRequestUri(URI baseUri, URI requestUri) {
            this.baseUri = baseUri;
            this.requestUri = requestUri;
        }

        private static BaseUriRequestUri resolve(ServerRequest req) {
            String processedBasePath = basePath(req.path());
            String rawPath = req.path().absolute().rawPath();
            String prefix = (req.isSecure() ? "https" : "http") + "://" + req.authority();
            String serverBasePath = prefix + processedBasePath;
            String requestPath = prefix + rawPath;
            if (!req.query().isEmpty()) {
                requestPath = requestPath + "?" + req.query().rawValue();
            }
            return new BaseUriRequestUri(URI.create(serverBasePath), URI.create(requestPath));
        }
    }
}
