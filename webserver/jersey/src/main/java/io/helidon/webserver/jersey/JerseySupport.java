/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configurable;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.SecurityContext;

import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPool;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.spi.Container;

import static java.util.Objects.requireNonNull;

/**
 * The Jersey Support integrates Jersey (JAX-RS RI) into the Web Server.
 * <p>
 * To enable Jersey for a given path, do
 * <pre><code>
 * WebServer.create(Routing.builder()
 *                         .register("/jersey",
 *                                   JerseySupport.builder()
 *                                                .register(JerseyExampleResource.class)
 *                                                .build())
 *                         .build());
 * </code></pre>
 * In such case the registered {@link JerseySupport} instance gets associated with the Web Server
 * and handles all requests made to {@code /jersey} context root.
 * <p>
 * Note that due to a blocking IO approach, each request handling is forwarded to a dedicated
 * thread pool which can be configured by one of the JerseySupport constructor.
 */
public class JerseySupport implements Service {

    private static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";

    /**
     * The request scoped span context qualifier that can be injected into a Jersey resource.
     * <pre><code>
     * {@literal @}Inject{@literal @}Named(JerseySupport.REQUEST_SPAN_CONTEXT)
     *  private SpanContext spanContext;
     * </code></pre>
     */
    public static final String REQUEST_SPAN_CONTEXT = "request-span-context";

    private static final Logger LOGGER = Logger.getLogger(JerseySupport.class.getName());

    private static final Type REQUEST_TYPE = (new GenericType<Ref<ServerRequest>>() { }).getType();
    private static final Type RESPONSE_TYPE = (new GenericType<Ref<ServerResponse>>() { }).getType();
    private static final Type SPAN_TYPE = (new GenericType<Ref<Span>>() { }).getType();
    private static final Type SPAN_CONTEXT_TYPE = (new GenericType<Ref<SpanContext>>() { }).getType();
    private static final AtomicReference<ExecutorService> DEFAULT_THREAD_POOL = new AtomicReference<>();

    private final ApplicationHandler appHandler;
    private final ExecutorService service;
    private final JerseyHandler handler = new JerseyHandler();
    private final HelidonJerseyContainer container;

    /**
     * Creates a Jersey Support based on the provided JAX-RS application.
     *
     * @param builder builder with application (the JAX-RS application to build the Jersey Support from),
     *                executor service (the executor service that is used for a request handling. If {@code null},
     *                a thread pool of size {@link Runtime#availableProcessors()} {@code * 8} is used),
     *                and Config
     */
    private JerseySupport(Builder builder) {
        ExecutorService executorService = (builder.executorService != null)
                ? builder.executorService
                : getDefaultThreadPool(builder.config);
        this.service = Contexts.wrap(executorService);

        // make sure we have a wrapped async executor as well
        if (builder.asyncExecutorService == null) {
            // create a new one from configuration
            builder.resourceConfig.register(AsyncExecutorProvider.create(builder.config));
        } else {
            // use the one provided
            builder.resourceConfig.register(AsyncExecutorProvider.create(builder.asyncExecutorService));
        }

        this.appHandler = new ApplicationHandler(builder.resourceConfig, new ServerBinder(executorService));
        this.container = new HelidonJerseyContainer(appHandler, builder.resourceConfig);
    }

    @Override
    public void update(Routing.Rules routingRules) {
        routingRules.any(handler);
        appHandler.onStartup(container);
    }

    private static synchronized ExecutorService getDefaultThreadPool(Config config) {
        if (DEFAULT_THREAD_POOL.get() == null) {
            Config executorConfig = config.get("executor-service");
            DEFAULT_THREAD_POOL.set(ServerThreadPoolSupplier.builder()
                                            .name("server")
                                            .config(executorConfig)
                                            .build()
                                            .get());
        }
        return DEFAULT_THREAD_POOL.get();
    }

    private static URI requestUri(ServerRequest req) {
        try {
            // Use raw string representation and URL to avoid re-encoding chars like '%'
            URI partialUri = new URL(req.isSecure() ? "https" : "http", req.localAddress(),
                                     req.localPort(), req.path().absolute().toRawString()).toURI();
            StringBuilder sb = new StringBuilder(partialUri.toString());
            if (req.uri().toString().endsWith("/") && sb.charAt(sb.length() - 1) != '/') {
                sb.append('/');
            }

            // unfortunately, the URI constructor encodes the 'query' and 'fragment' which is totally silly
            if (req.query() != null && !req.query().isEmpty()) {
                sb.append("?")
                        .append(req.query());
            }
            if (req.fragment() != null && !req.fragment().isEmpty()) {
                sb.append("#")
                        .append(req.fragment());
            }
            return new URI(sb.toString());
        } catch (URISyntaxException | MalformedURLException e) {
            throw new HttpException("Unable to parse request URL", Http.Status.BAD_REQUEST_400, e);
        }
    }

    private static URI baseUri(ServerRequest req) {
        try {
            return new URI(req.isSecure() ? "https" : "http", null, req.localAddress(),
                           req.localPort(), basePath(req.path()), null, null);
        } catch (URISyntaxException e) {
            throw new HttpException("Unable to parse request URL", Http.Status.BAD_REQUEST_400, e);
        }
    }

    static String basePath(HttpRequest.Path path) {
        String reqPath = path.toString();
        String absPath = path.absolute().toString();
        String basePath = absPath.substring(0, absPath.length() - reqPath.length() + 1);

        if (absPath.isEmpty() || basePath.isEmpty()) {
            return "/";
        } else if (basePath.charAt(basePath.length() - 1) != '/') {
            return basePath + "/";
        } else {
            return basePath;
        }
    }

    /**
     * A WebServerBinder that also supports injection of ThreadPool by name if the executor is one.
     * This class is explicitly static to avoid field assignment order issues in the outer class.
     */
    private static class ServerBinder extends WebServerBinder {
        private final ExecutorService executorService;

        ServerBinder(ExecutorService executorService) {
            this.executorService = requireNonNull(executorService);
        }

        @Override
        protected void configure() {
            super.configure();

            // If the executor is a ThreadPool, make it available to inject with its name.
            Optional<ThreadPool> maybePool = ThreadPool.asThreadPool(executorService);
            if (maybePool.isPresent()) {
                ThreadPool pool = maybePool.get();
                bind(pool).named(pool.getName()).to(ThreadPool.class);
            }
        }
    }

    /**
     * A {@link Handler} implementation that has a 1:1 association with the {@link JerseySupport}
     * instance. The {@link JerseySupport} does not implement the {@link Handler} so that users
     * do not accidentally register the instance by calling {@link Routing.Builder#any(Handler...)}
     * for example.
     */
    private class JerseyHandler implements Handler {

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            // Skip this handler if a WebSocket upgrade request
            Optional<String> secWebSocketKey = req.headers().value(SEC_WEBSOCKET_KEY);
            if (secWebSocketKey.isPresent()) {
                req.next();
                return;
            }

            // create a new context for jersey, so we do not modify webserver's internals
            Context parent = Contexts.context()
                    .orElseThrow(() -> new IllegalStateException("Context must be propagated from server"));
            Context jerseyContext = Context.create(parent);

            Contexts.runInContext(jerseyContext, () -> doAccept(req, res));
        }

        private void doAccept(ServerRequest req, ServerResponse res) {
            CompletableFuture<Void> whenHandleFinishes = new CompletableFuture<>();
            ResponseWriter responseWriter = new ResponseWriter(res, req, whenHandleFinishes);
            ContainerRequest requestContext = new ContainerRequest(baseUri(req),
                                                                   requestUri(req),
                                                                   req.method().name(),
                                                                   new WebServerSecurityContext(),
                                                                   new WebServerPropertiesDelegate(req));
            // set headers
            req.headers().toMap().forEach(requestContext::headers);

            requestContext.setWriter(responseWriter);

            req.content()
                    .as(InputStream.class)
                    .thenAccept(is -> {
                        requestContext.setEntityStream(is);

                        service.execute(() -> { // No need to use submit() since the future is not used.
                            try {
                                LOGGER.finer("Handling in Jersey started.");

                                requestContext.setRequestScopedInitializer(injectionManager -> {
                                    injectionManager.<Ref<ServerRequest>>getInstance(REQUEST_TYPE).set(req);
                                    injectionManager.<Ref<ServerResponse>>getInstance(RESPONSE_TYPE).set(res);
                                    injectionManager.<Ref<Span>>getInstance(SPAN_TYPE).set(req.span());
                                    injectionManager.<Ref<SpanContext>>getInstance(SPAN_CONTEXT_TYPE).set(req.spanContext());
                                });

                                appHandler.handle(requestContext);
                                whenHandleFinishes.complete(null);
                            } catch (Throwable e) {
                                // this is very unlikely to happen; Jersey will try to call ResponseWriter.failure(Throwable)
                                // rather
                                // than to propagate the exception
                                req.next(e);
                            }
                        });

                    })
                    .exceptionally(throwable -> {
                        // this should not happen; but for the sake of completeness ..
                        req.next(throwable);
                        return null;
                    });
        }
    }

    /**
     * Close this integration with Jersey.
     * Once closed, this instance is no longer usable.
     */
    public void close() {
        appHandler.onShutdown(container);
    }

    /**
     * A limited implementation of {@link PropertiesDelegate} that doesn't support
     * a significant set of operations due to the Web Server design.
     * <p>
     * It is expected that the limitations would be overcome (somehow) in the future if needed.
     */
    private static class WebServerPropertiesDelegate implements PropertiesDelegate {

        private final ServerRequest req;

        WebServerPropertiesDelegate(ServerRequest req) {
            this.req = req;
        }

        @Override
        public Object getProperty(String name) {
            return req.context().get(name, Object.class).orElse(null);
        }

        @Override
        public Collection<String> getPropertyNames() {
            // TODO we don't provide an ability to iterate over the registered properties
            throw new UnsupportedOperationException("iteration over property names not allowed");
        }

        @Override
        public void setProperty(String name, Object object) {
            req.context().register(name, object);
        }

        @Override
        public void removeProperty(String name) {
            // TODO do we want to remove properties?
            throw new UnsupportedOperationException("property removal from the context is not allowed");
        }
    }

    /**
     * Just a stub implementation that should be evolved in the future.
     */
    private static class WebServerSecurityContext implements SecurityContext {

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

    /**
     * Creates {@code JerseySupport} based on the provided {@link Application JAX-RS Application}.
     * <pre>
     * WebServer.create(Routing.builder()
     *                         .register("/jersey",
     *                                   JerseySupport.create(new ResourceConfig(JerseyExampleResource.class)))
     *                         .build());
     * </pre>
     *
     * @param application the JAX-RS application to create this instance based on
     * @return the Jersey Support instance
     * @see #builder(Application)
     */
    public static JerseySupport create(Application application) {
        return builder(application).build();
    }

    /**
     * Creates {@code JerseySupport} builder based on default empty {@link ResourceConfig}.
     * <p>
     * Every component must be registered on this builder by calling any of {@code register} methods.
     * Properties can be set by the builder method {@link Builder#property(String, Object)}.
     * <p>
     * {@code Build WebServer}:
     * <pre>
     * WebServer.create(Routing.builder()
     *                         .register("/jersey",
     *                                   JerseySupport.builder()
     *                                                .register(JerseyExampleResource.class)
     *                                                .build())
     *                         .build());
     * </pre>
     *
     * @return this
     * @see #builder(Application)
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates {@code JerseySupport} builder based on a passed application.
     * <p>
     * The application might be extended by calling any of {@code register} methods.
     * Properties can be set by the application, the builder method {@link Builder#property(String, Object)}.
     * <p>
     * {@code Build WebServer}:
     * <pre>
     * WebServer.create(Routing.builder()
     *                         .register("/jersey",
     *                                   JerseySupport.builder()
     *                                                .register(JerseyExampleResource.class)
     *                                                .build())
     *                         .build());
     * </pre>
     *
     * @param application a base application
     * @return this
     */
    public static Builder builder(Application application) {
        return new Builder(application);
    }

    /**
     * Builder for convenient way to create {@link JerseySupport}.
     */
    public static final class Builder implements Configurable<Builder>, io.helidon.common.Builder<JerseySupport> {

        private ResourceConfig resourceConfig;
        private ExecutorService executorService;
        private Config config = Config.empty();
        private ExecutorService asyncExecutorService;

        private Builder() {
            this(null);
        }

        private Builder(Application application) {
            if (application == null) {
                application = new Application();
            }
            this.resourceConfig = ResourceConfig.forApplication(application);
        }

        /**
         * Jersey Module builder class for convenient creating {@link JerseySupport}.
         *
         * @return built module
         */
        @Override
        public JerseySupport build() {
            return new JerseySupport(this);
        }

        @Override
        public Configuration getConfiguration() {
            return resourceConfig.getConfiguration();
        }

        @Override
        public Builder property(String key, Object value) {
            resourceConfig.property(key, value);
            return this;
        }

        @Override
        public Builder register(Class<?> componentClass) {
            resourceConfig.register(componentClass);
            return this;
        }

        @Override
        public Builder register(Class<?> componentClass, int priority) {
            resourceConfig.register(componentClass, priority);
            return this;
        }

        @Override
        public Builder register(Class<?> componentClass, Class<?>... contracts) {
            resourceConfig.register(componentClass, contracts);
            return this;
        }

        @Override
        public Builder register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
            resourceConfig.register(componentClass, contracts);
            return this;
        }

        @Override
        public Builder register(Object component) {
            resourceConfig.register(component);
            return this;
        }

        @Override
        public Builder register(Object component, int priority) {
            resourceConfig.register(component, priority);
            return this;
        }

        @Override
        public Builder register(Object component, Class<?>... contracts) {
            resourceConfig.register(component, contracts);
            return this;
        }

        @Override
        public Builder register(Object component, Map<Class<?>, Integer> contracts) {
            resourceConfig.register(component, contracts);
            return this;
        }

        /**
         * Exposes {@link ResourceConfig#registerResources(Resource...)}.
         *
         * @param resources resources to register
         * @return an updated instance
         */
        public Builder registerResources(Resource... resources) {
            resourceConfig.registerResources(resources);
            return this;
        }

        /**
         * Sets the executor service to use for a handling of request that matches a path
         * where the {@link JerseySupport} is registered.
         *
         * @param executorService the executor service to use for a handling of requests that go
         * to the Jersey application
         * @return an updated instance
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Sets the executor service to use for a handling of asynchronous requests
         * with {@link javax.ws.rs.container.AsyncResponse}.
         *
         * @param executorService the executor service to use for a handling of asynchronous requests
         * @return an updated instance
         */
        public Builder asyncExecutorService(ExecutorService executorService) {
            this.asyncExecutorService = executorService;
            return this;
        }

        /**
         * Update configuration from Config.
         * Currently used to set up async executor service only.
         *
         * @param config configuration at the Jersey configuration node
         * @return updated builder instance
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }
    }

    private static class HelidonJerseyContainer implements Container {
        private final ApplicationHandler applicationHandler;

        private HelidonJerseyContainer(ApplicationHandler appHandler, ResourceConfig resourceConfig) {
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
}
