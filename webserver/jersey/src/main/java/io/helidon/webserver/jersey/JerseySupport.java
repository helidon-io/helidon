/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configurable;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.SecurityContext;

import io.helidon.webserver.Handler;
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

    /**
     * The request scoped span qualifier that can be injected into a Jersey resource.
     * <pre><code>
     * {@literal @}Inject{@literal @}Named(JerseySupport.REQUEST_SPAN_QUALIFIER)
     *  private Span span;
     * </code></pre>
     *
     * @deprecated Use span context ({@link #REQUEST_SPAN_CONTEXT}) instead.
     */
    @Deprecated
    public static final String REQUEST_SPAN_QUALIFIER = "request-parent-span";

    /**
     * The request scoped span context qualifier that can be injected into a Jersey resource.
     * <pre><code>
     * {@literal @}Inject{@literal @}Named(JerseySupport.REQUEST_SPAN_CONTEXT)
     *  private SpanContext spanContext;
     * </code></pre>
     */
    public static final String REQUEST_SPAN_CONTEXT = "request-span-context";

    private static final Logger LOGGER = Logger.getLogger(JerseySupport.class.getName());

    private final Type requestType = (new GenericType<Ref<ServerRequest>>() { }).getType();
    private final Type responseType = (new GenericType<Ref<ServerResponse>>() { }).getType();
    private final Type spanType = (new GenericType<Ref<Span>>() { }).getType();
    private final Type spanContextType = (new GenericType<Ref<SpanContext>>() { }).getType();

    private final ApplicationHandler appHandler;
    private final ExecutorService service;
    private final JerseyHandler handler = new JerseyHandler();

    /**
     * Creates a Jersey Support based on the provided JAX-RS application.
     *
     * @param application the JAX-RS application to build the Jersey Support from
     * @param service     the executor service that is used for a request handling. If {@code null},
     *                    a thread pool of size
     *                    {@link Runtime#availableProcessors()} {@code * 2} is used.
     */
    private JerseySupport(Application application, ExecutorService service) {
        this.appHandler = new ApplicationHandler(application, new WebServerBinder());
        this.service = service != null ? service : Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    @Override
    public void update(Routing.Rules routingRules) {
        routingRules.any(handler);
    }

    private static URI requestUri(ServerRequest req) {
        try {
            URI partialUri = new URI(req.isSecure() ? "https" : "http", null, req.localAddress(),
                                     req.localPort(), req.path().absolute().toString(), null, null);
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
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to create a request URI from the request info.", e);
        }
    }

    private static URI baseUri(ServerRequest req) {
        try {
            return new URI(req.isSecure() ? "https" : "http", null, req.localAddress(),
                           req.localPort(), basePath(req), null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to create a base URI from the request info.", e);
        }
    }

    private static String basePath(ServerRequest req) {
        String reqPath = req.path().toString();
        String absPath = req.path().absolute().toString();
        String basePath = absPath.substring(0, absPath.length() - reqPath.length());

        if (absPath.isEmpty() || basePath.isEmpty()) {
            return "/";
        } else if (basePath.charAt(basePath.length() - 1) != '/') {
            return basePath + "/";
        } else {
            return basePath;
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

                   service.submit(() -> {
                       try {
                           LOGGER.finer("Handling in Jersey started.");

                           requestContext.setRequestScopedInitializer(injectionManager -> {
                               injectionManager.<Ref<ServerRequest>>getInstance(requestType).set(req);
                               injectionManager.<Ref<ServerResponse>>getInstance(responseType).set(res);
                               injectionManager.<Ref<Span>>getInstance(spanType).set(req.span());
                               injectionManager.<Ref<SpanContext>>getInstance(spanContextType).set(req.spanContext());
                           });

                           appHandler.handle(requestContext);
                           whenHandleFinishes.complete(null);
                       } catch (Throwable e) {
                           // this is very unlikely to happen; Jersey will try to call ResponseWriter.failure(Throwable) rather
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

    /** Just a stub implementation that should be evolved in the future. */
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
    public static class Builder implements Configurable<Builder>, io.helidon.common.Builder {

        private ResourceConfig resourceConfig;
        private ExecutorService executorService;

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
        public JerseySupport build() {
            return new JerseySupport(resourceConfig, executorService);
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
         *                        to the Jersey application
         * @return an updated instance
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }
    }
}
