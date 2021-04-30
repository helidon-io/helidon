/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;

import io.helidon.common.Prioritized;
import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.microprofile.cdi.BuildTimeStart;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;
import io.helidon.webserver.staticcontent.StaticContentSupport;

import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.Injections;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Extension to handle web server configuration and lifecycle.
 */
public class ServerCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(ServerCdiExtension.class.getName());
    private static final AtomicBoolean IN_PROGRESS_OR_RUNNING = new AtomicBoolean();

    // build time
    private WebServer.Builder serverBuilder = WebServer.builder()
            .port(7001);

    private Routing.Builder routingBuilder = Routing.builder();
    private Map<String, Routing.Builder> namedRoutings = new HashMap<>();

    // configuration option that can be provided, only available in `startServer`
    private Supplier<? extends ExecutorService> jaxRsExecutorService;
    private String basePath;
    private Config config;

    // runtime
    private WebServer webserver;

    // these fields may be accessed from different threads than created on
    private volatile int port;
    private volatile String listenHost = "0.0.0.0";
    private volatile boolean started;
    private final List<JerseySupport> jerseySupports = new LinkedList<>();

    private void buildTime(@Observes @BuildTimeStart Object event) {
        // update the status of server, as we may have been started without a builder being used
        // such as when cdi.Main or SeContainerInitializer are used
        if (!IN_PROGRESS_OR_RUNNING.compareAndSet(false, true)) {
            throw new IllegalStateException("There is another builder in progress, or another Server running. "
                                                    + "You cannot run more than one in parallel");
        }
    }

    private void prepareRuntime(@Observes @RuntimeStart Config config) {
        serverBuilder.config(config.get("server"));
        this.config = config;
    }

    private void startServer(@Observes @Priority(PLATFORM_AFTER + 100) @Initialized(ApplicationScoped.class) Object event,
                             BeanManager beanManager) {

        // make sure all configuration is in place
        if (null == jaxRsExecutorService) {
            Config serverConfig = config.get("server");
            final java.lang.reflect.Method m;
            if (DeprecatedConfig.get(serverConfig, "executor-service.virtual-threads", "virtual-threads")
                    .asBoolean().orElse(false)) {
                java.lang.reflect.Method temp = null;
                try {
                    temp = Executors.class.getDeclaredMethod("newVirtualThreadExecutor");
                } catch (final ReflectiveOperationException notLoomEarlyAccess) {
                    temp = null;
                } finally {
                    m = temp;
                }
            } else {
                m = null;
            }
            if (m != null) {
                jaxRsExecutorService = () -> {
                    try {
                        return (ExecutorService) m.invoke(null);
                    } catch (final ReflectiveOperationException reflectiveOperationException) {
                        throw new IllegalStateException(reflectiveOperationException.getMessage(), reflectiveOperationException);
                    }
                };
            } else {
                jaxRsExecutorService = ServerThreadPoolSupplier.builder()
                    .name("server")
                    .config(serverConfig.get("executor-service"))
                    .build();
            }
        }

        // redirect to the first page when root is accessed (if configured)
        registerDefaultRedirect();

        // register static content if configured
        registerStaticContent();

        // reactive services
        registerWebServerServices(beanManager);

        // JAX-RS applications (and resources)
        registerJaxRsApplications(beanManager);

        // start the webserver
        serverBuilder.routing(routingBuilder.build());

        namedRoutings.forEach(serverBuilder::addNamedRouting);
        webserver = serverBuilder.build();

        try {
            webserver.start().toCompletableFuture().get();
            started = true;
        } catch (Exception e) {
            throw new DeploymentException("Failed to start webserver", e);
        }

        this.port = webserver.port();

        long initializationElapsedTime = ManagementFactory.getRuntimeMXBean().getUptime();

        if ("0.0.0.0".equals(listenHost)) {
            // listening on all addresses
            LOGGER.info(() -> "Server started on http://localhost:" + port + " (and all other host addresses)"
                    + " in " + initializationElapsedTime + " milliseconds (since JVM startup).");
        } else {
            LOGGER.info(() -> "Server started on http://" + listenHost + ":" + port
                    + " in " + initializationElapsedTime + " milliseconds (since JVM startup).");
        }

        // this is not needed at runtime, collect garbage
        serverBuilder = null;
        routingBuilder = null;
        namedRoutings = null;
    }

    private void registerJaxRsApplications(BeanManager beanManager) {
        JaxRsCdiExtension jaxRs = beanManager.getExtension(JaxRsCdiExtension.class);

        List<JaxRsApplication> jaxRsApplications = jaxRs.applicationsToRun();
        if (jaxRsApplications.isEmpty()) {
            LOGGER.warning("There are no JAX-RS applications or resources. Maybe you forgot META-INF/beans.xml file?");
        } else {
            InjectionManager injectionManager = Injections.createInjectionManager();
            jaxRsApplications.forEach(it -> addApplication(jaxRs, it, injectionManager));
        }
    }

    private void registerDefaultRedirect() {
        Optional.ofNullable(basePath)
                .or(() -> config.get("server.base-path").asString().asOptional())
                .ifPresent(basePath -> routingBuilder.any("/", (req, res) -> {
                    res.status(Http.Status.MOVED_PERMANENTLY_301);
                    res.headers().put(Http.Header.LOCATION, basePath);
                    res.send();
                }));
    }

    private void registerStaticContent() {
        Config config = (Config) ConfigProvider.getConfig();
        config = config.get("server.static");

        config.get("classpath")
                .ifExists(this::registerClasspathStaticContent);

        config.get("path")
                .ifExists(this::registerPathStaticContent);
    }

    private void registerPathStaticContent(Config config) {
        Config context = config.get("context");
        StaticContentSupport.FileSystemBuilder pBuilder = StaticContentSupport.builder(config.get("location")
                                                                                               .as(Path.class)
                                                                                               .get());
        config.get("welcome")
                .asString()
                .ifPresent(pBuilder::welcomeFileName);
        StaticContentSupport staticContent = pBuilder.build();

        if (context.exists()) {
            routingBuilder.register(context.asString().get(), staticContent);
        } else {
            routingBuilder.register(staticContent);
        }
    }

    private void registerClasspathStaticContent(Config config) {
        Config context = config.get("context");

        StaticContentSupport.ClassPathBuilder cpBuilder = StaticContentSupport.builder(config.get("location").asString().get());
        cpBuilder.welcomeFileName(config.get("welcome")
                                          .asString()
                                          .orElse("index.html"));
        config.get("tmp-dir")
                .as(Path.class)
                .ifPresent(cpBuilder::tmpDir);
        StaticContentSupport staticContent = cpBuilder.build();

        if (context.exists()) {
            routingBuilder.register(context.asString().get(), staticContent);
        } else {
            routingBuilder.register(staticContent);
        }
    }

    private void stopServer(@Observes @Priority(PLATFORM_BEFORE) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        try {
            if (started) {
                doStop(event);
            }
        } finally {
            // as there only can be a single CDI in a single JVM, once this CDI is shutting down, we
            // can start another one
            IN_PROGRESS_OR_RUNNING.set(false);
        }
    }

    private void doStop(Object event) {
        if (null == webserver || !started) {
            // nothing to do
            return;
        }
        long beforeT = System.nanoTime();

        try {
            webserver.shutdown()
                    .toCompletableFuture()
                    .get();

            started = false;
            jerseySupports.forEach(JerseySupport::close);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Failed to stop web server", e);
        } finally {
            long t = TimeUnit.MILLISECONDS.convert(System.nanoTime() - beforeT, TimeUnit.NANOSECONDS);
            LOGGER.info(() -> "Server stopped in " + t + " milliseconds.");
        }
    }

    private void addApplication(JaxRsCdiExtension jaxRs, JaxRsApplication applicationMeta,
                                InjectionManager injectionManager) {
        LOGGER.info("Registering JAX-RS Application: " + applicationMeta.appName());

        Optional<String> contextRoot = jaxRs.findContextRoot(config, applicationMeta);
        Optional<String> namedRouting = jaxRs.findNamedRouting(config, applicationMeta);
        boolean routingNameRequired = jaxRs.isNamedRoutingRequired(config, applicationMeta);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Application " + applicationMeta.appName()
                                  + ", class: " + applicationMeta.appClassName()
                                  + ", contextRoot: " + contextRoot
                                  + ", namedRouting: " + namedRouting
                                  + ", routingNameRequired: " + routingNameRequired);
        }

        Routing.Builder routing = routingBuilder(namedRouting, routingNameRequired, applicationMeta.appName());

        JerseySupport jerseySupport = jaxRs.toJerseySupport(jaxRsExecutorService, applicationMeta, injectionManager);
        if (contextRoot.isPresent()) {
            String contextRootString = contextRoot.get();
            LOGGER.fine(() -> "JAX-RS application " + applicationMeta.appName() + " registered on '" + contextRootString + "'");
            routing.register(contextRootString, jerseySupport);
        } else {
            LOGGER.fine(() -> "JAX-RS application " + applicationMeta.appName() + " registered on '/'");
            routing.register(jerseySupport);
        }
        jerseySupports.add(jerseySupport);
    }

    /**
     * Provides access to routing builder.
     *
     * @param namedRouting Named routing.
     * @param routingNameRequired Routing name required.
     * @param appName Application's name.
     * @return The routing builder.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Routing.Builder routingBuilder(Optional<String> namedRouting,
                                          boolean routingNameRequired,
                                          String appName) {
        if (namedRouting.isPresent()) {
            String socket = namedRouting.get();
            if (!serverBuilder.hasSocket(socket)) {
                if (routingNameRequired) {
                    throw new IllegalStateException("Application "
                                                            + appName
                                                            + " requires routing "
                                                            + socket
                                                            + " to exist, yet such a socket is not configured for web server");
                } else {
                    LOGGER.info("Routing " + socket + " does not exist, using default routing for application "
                                        + appName);

                    return serverRoutingBuilder();
                }
            } else {
                return serverNamedRoutingBuilder(socket);
            }
        } else {
            return serverRoutingBuilder();
        }
    }

    @SuppressWarnings("unchecked")
    private void registerWebServerServices(BeanManager beanManager) {
        List<Bean<?>> beans = prioritySort(beanManager.getBeans(Service.class));
        CreationalContext<Object> context = beanManager.createCreationalContext(null);

        for (Bean<?> bean : beans) {
            Bean<Object> objBean = (Bean<Object>) bean;
            Class<?> aClass = objBean.getBeanClass();
            Service service = (Service) objBean.create(context);
            registerWebServerService(aClass, service);
        }
    }

    private static List<Bean<?>> prioritySort(Set<Bean<?>> beans) {
        List<Bean<?>> prioritized = new ArrayList<>(beans);
        prioritized.sort((o1, o2) -> {
            int firstPriority = priority(o1.getBeanClass());
            int secondPriority = priority(o2.getBeanClass());
            return Integer.compare(firstPriority, secondPriority);
        });
        return prioritized;
    }

    private static int priority(Class<?> aClass) {
        Priority prio = aClass.getAnnotation(Priority.class);
        return (null == prio) ? Prioritized.DEFAULT_PRIORITY : prio.value();
    }

    private void registerWebServerService(Class<?> serviceClass,
                                          Service service) {

        RoutingPath rp = serviceClass.getAnnotation(RoutingPath.class);
        RoutingName rn = serviceClass.getAnnotation(RoutingName.class);

        String path = (null == rp) ? null : rp.value();
        String routingName = (null == rn) ? null : rn.value();
        boolean routingNameRequired = (null != rn) && rn.required();

        // can override routing path from configuration
        path = config.get(serviceClass.getName()
                                  + "."
                                  + RoutingPath.CONFIG_KEY_PATH)
                .asString()
                .orElse(path);

        // can override routing name from configuration
        routingName = config.get(serviceClass.getName()
                                         + "."
                                         + RoutingName.CONFIG_KEY_NAME)
                .asString()
                .orElse(routingName);

        // also whether the routing name is required can be overridden
        routingNameRequired = config.get(serviceClass.getName()
                                                 + "."
                                                 + RoutingName.CONFIG_KEY_REQUIRED)
                .asBoolean()
                .orElse(routingNameRequired);

        Routing.Rules routing = findRouting(serviceClass.getName(),
                                            routingName,
                                            routingNameRequired);

        if ((null == path) || "/".equals(path)) {
            routing.register(service);
        } else {
            routing.register(path, service);
        }
    }

    private Routing.Rules findRouting(String className,
                                      String routingName,
                                      boolean routingNameRequired) {
        if ((null == routingName) || RoutingName.DEFAULT_NAME.equals(routingName)) {
            return serverRoutingBuilder();
        }

        if (!serverBuilder.hasSocket(routingName)) {
            // resolve missing socket configuration
            if (routingNameRequired) {
                throw new IllegalStateException(className
                                                        + " requires routing "
                                                        + routingName
                                                        + ", yet such a named socket is not configured for"
                                                        + " web server");
            }

            LOGGER.fine(() -> className + " is configured with named routing " + routingName + ". Such a routing"
                    + " is not configured, this service/application will run on default socket.");
            return serverRoutingBuilder();
        }

        return serverNamedRoutingBuilder(routingName);
    }

    /**
     * Helidon web server configuration builder that can be used to re-configure the web server.
     *
     * @return web server configuration builder
     */
    public WebServer.Builder serverBuilder() {
        return serverBuilder;
    }

    /**
     * Helidon webserver routing builder that can be used to add routes to the webserver.
     *
     * @return server routing builder
     */
    public Routing.Builder serverRoutingBuilder() {
        return routingBuilder;
    }

    /**
     * Helidon webserver routing builder that can be used to add routes to a named socket
     *  of the webserver.
     *
     * @param name name of the named routing (should match a named socket configuration)
     * @return builder for routing of the named route
     */
    public Routing.Builder serverNamedRoutingBuilder(String name) {
        return namedRoutings.computeIfAbsent(name, routeName -> Routing.builder());
    }

    /**
     * Configure the default executor service to be used by this server.
     *
     * @param defaultExecutorService executor service supplier
     */
    public void defaultExecutorService(Supplier<? extends ExecutorService> defaultExecutorService) {
        this.jaxRsExecutorService = defaultExecutorService;
    }

    /**
     * Current host the server is running on.
     * @return host of this server
     */
    public String host() {
        return listenHost;
    }

    /**
     * Current port the server is running on. This information is only available after the
     * server is actually started.
     *
     * @return port the server is running on
     */
    public int port() {
        return port;
    }

    /**
     * Named port the server is running on. This information is only available after the
     * server is actually started.
     *
     * @param name Socket name
     * @return Named port the server is running on
     */
    public int port(String name) {
        return webserver.port(name);
    }

    /**
     * State of the server.
     *
     * @return {@code true} if the server is already started, {@code false} otherwise
     */
    public boolean started() {
        return started;
    }

    /**
     * Base path of this server. This is used to redirect when a request is made for root ("/").
     *
     * @param basePath path to redirect to when user requests the root path
     */
    public void basePath(String basePath) {
        this.basePath = basePath;
    }

    /**
     * Configure the listen host of this server.
     *
     * @param listenHost host to listen on
     */
    void listenHost(String listenHost) {
        this.listenHost = listenHost;
    }
}
