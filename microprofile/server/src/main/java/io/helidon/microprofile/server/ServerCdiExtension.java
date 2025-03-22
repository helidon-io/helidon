/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.common.Builder;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.resumable.ResumableSupport;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.mp.Prioritized;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Scopes;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.RequestScopeFeature;
import io.helidon.webserver.RequestScopeFeatureProvider;
import io.helidon.webserver.Router;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.ObserveFeatureConfig;
import io.helidon.webserver.observe.spi.Observer;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.staticcontent.StaticContentConfig;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.ParamConverterProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.glassfish.jersey.internal.inject.Bindings;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.Injections;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Extension to handle web server configuration and lifecycle.
 */
public class ServerCdiExtension implements Extension {
    private static final System.Logger LOGGER = System.getLogger(ServerCdiExtension.class.getName());
    private static final System.Logger STARTUP_LOGGER = System.getLogger("io.helidon.microprofile.startup.server");
    private static final AtomicBoolean IN_PROGRESS_OR_RUNNING = new AtomicBoolean();
    private final Map<Bean<?>, RoutingConfiguration> serviceBeans = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<HttpRouting.Builder> routingsWithKPIMetrics = new ArrayList<>();
    // build time
    private WebServerConfig.Builder serverBuilder = WebServer.builder()
            .shutdownHook(false) // we use a custom CDI shutdown hook in HelidonContainerImpl
            .featuresDiscoverServices(false) // we need to explicitly configure features, as they use different sources of config
            .port(7001);
    private ObserveFeatureConfig.Builder observeBuilder = ObserveFeature.builder();
    private HttpRouting.Builder routingBuilder = HttpRouting.builder();
    private Map<String, HttpRouting.Builder> namedRoutings = new HashMap<>();
    private Map<String, Router.Builder> namedRouters = new HashMap<>();
    private String basePath;
    private Config config;

    // runtime
    private WebServer webserver;

    // these fields may be accessed from different threads than created on
    private volatile int port;
    private volatile String listenHost = "0.0.0.0";
    private volatile boolean started;

    private Context context;

    /**
     * Default constructor required by {@link java.util.ServiceLoader}.
     */
    public ServerCdiExtension() {
    }

    /**
     * Helidon webserver routing builder that can be used to add routes to the webserver.
     *
     * @return server routing builder
     */
    public HttpRouting.Builder serverRoutingBuilder() {
        return routingBuilder;
    }

    /**
     * Helidon webserver routing builder that can be used to add routes to a named socket
     * of the webserver.
     *
     * @param name name of the named routing (should match a named socket configuration)
     * @return builder for routing of the named route
     */
    public HttpRouting.Builder serverNamedRoutingBuilder(String name) {
        if (DEFAULT_SOCKET_NAME.equals(name)) {
            return serverRoutingBuilder();
        }
        return namedRoutings.computeIfAbsent(name, routeName -> HttpRouting.builder());
    }

    /**
     * Add a routing to the server. This MUST NOT be HTTP routing. To update HTTP routing, please use
     * {@link #serverRoutingBuilder()}.
     *
     * @param routing routing to add, such as WebSocket routing
     */
    public void addRouting(Builder<?, ? extends Routing> routing) {
        this.serverBuilder.addRouting(routing);
    }

    /**
     * Add a routing to the server. This MUST NOT be HTTP routing. To update HTTP routing, please use
     * {@link #serverNamedRoutingBuilder(String)}.
     *
     * @param routing routing to add, such as WebSocket routing
     * @param socketName name of the configured socket this routing should be assigned to, if not present (and required is false),
     *                   the routing would be added to default socket
     * @param required is the socket required to be present, validated against configured sockets
     * @param appName name of the application, to provide meaningful error messages
     */
    public void addRouting(Builder<?, ? extends Routing> routing, String socketName, boolean required, String appName) {
        if (DEFAULT_SOCKET_NAME.equals(socketName)) {
            serverBuilder.addRouting(routing);
        } else {
            boolean hasRouting = serverBuilder.sockets().containsKey(socketName);
            if (required && !hasRouting) {
                throw new IllegalStateException("Application requires configured listener (socket name) "
                                                        + socketName
                                                        + " to exist, yet such a socket is not configured for web server"
                                                        + " for app: " + appName);
            }
            if (!hasRouting) {
                LOGGER.log(Level.INFO, "Routing " + socketName + " does not exist, using default routing instead for " + appName);
                serverBuilder.addRouting(routing);
            } else {
                namedRouters.computeIfAbsent(socketName, it -> Router.builder())
                        .addRouting(routing);
            }
        }
    }

    /**
     * Add an observer, probably from an observer specific CDI extension.
     * Observers are also discovered using a service loader, so if no customization in CDI is needed, the extension
     * is not needed either.
     *
     * @param observer observer to add
     */
    public void addObserver(Observer observer) {
        observeBuilder.addObserver(observer);
    }

    /**
     * Current host the server is running on.
     *
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
     * Add a server feature.
     *
     * @param feature feature to add
     */
    public void addFeature(ServerFeature feature) {
        serverBuilder.addFeature(feature);
    }

    /**
     * Provides access to routing builder.
     *
     * @param namedRouting        Named routing.
     * @param routingNameRequired Routing name required.
     * @param appName             Application's name.
     * @return The routing builder.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    HttpRouting.Builder routingBuilder(Optional<String> namedRouting,
                                       boolean routingNameRequired,
                                       String appName) {
        if (namedRouting.isPresent()) {
            String socket = namedRouting.get();
            if (!serverBuilder.sockets().containsKey(socket)) {
                if (routingNameRequired) {
                    throw new IllegalStateException("Application "
                                                            + appName
                                                            + " requires routing "
                                                            + socket
                                                            + " to exist, yet such a socket is not configured for web server");
                } else {
                    LOGGER.log(Level.INFO, "Routing " + socket + " does not exist, using default routing for application "
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

    /**
     * Helidon web server configuration builder that can be used to re-configure the web server.
     *
     * @return web server configuration builder
     */
    WebServerConfig.Builder serverBuilder() {
        return serverBuilder;
    }

    /**
     * Context (if explicitly defined).
     * @param context context to use
     */
    void context(Context context) {
        this.context = context;
    }

    /**
     * Configure the listen host of this server.
     *
     * @param listenHost host to listen on
     */
    void listenHost(String listenHost) {
        this.listenHost = listenHost;
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

    private void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.serverBuilder.config(config.get("server"));
        this.observeBuilder.config(config.get("server.features.observe"));
        this.config = config;

        if (!config.get("server.features.context").exists()) {
            // not created automatically from configuration, create it manually
            serverBuilder.addFeature(ContextFeature.create());
        }
        if (!config.get("server.features.request-scope").exists()) {
            var feature = GlobalServiceRegistry.registry()
                    .first(RequestScopeFeatureProvider.class)
                    .map(it -> {
                        Config cfg = Config.just(ConfigSources.create(Map.of("enabled", "true")));
                        return it.create(cfg, it.configKey());
                    })
                    .orElseGet(() -> RequestScopeFeature.create(GlobalServiceRegistry.registry().supply(Scopes.class), true));

            serverBuilder.addFeature(feature);
        }
    }

    // Priority must ensure that these handlers are added before the MetricsSupport KPI metrics handler.
    private void registerKpiMetricsDeferrableRequestHandlers(
            @Observes @Priority(LIBRARY_BEFORE) @Initialized(ApplicationScoped.class)
            Object event, BeanManager beanManager) {
        JaxRsCdiExtension jaxRs = beanManager.getExtension(JaxRsCdiExtension.class);

        List<JaxRsApplication> jaxRsApplications = jaxRs.applicationsToRun();
        jaxRsApplications.forEach(it -> registerKpiMetricsDeferrableRequestContextSetterHandler(jaxRs, it));
    }

    private void recordMethodProducedServices(@Observes ProcessProducerMethod<? extends HttpService, ?> ppm) {
        Method m = ppm.getAnnotatedProducerMethod().getJavaMember();
        String contextKey = m.getDeclaringClass().getName() + "." + m.getName();
        serviceBeans.put(ppm.getBean(), new RoutingConfiguration(ppm.getAnnotated(), contextKey));
    }

    private void recordFieldProducedServices(@Observes ProcessProducerField<? extends HttpService, ?> ppf) {
        Field f = ppf.getAnnotatedProducerField().getJavaMember();
        String contextKey = f.getDeclaringClass().getName() + "." + f.getName();
        serviceBeans.put(ppf.getBean(), new RoutingConfiguration(ppf.getAnnotated(), contextKey));
    }

    private void recordBeanServices(@Observes ProcessManagedBean<? extends HttpService> pmb) {
        Class<? extends HttpService> cls = pmb.getAnnotatedBeanClass().getJavaClass();
        serviceBeans.put(pmb.getBean(), new RoutingConfiguration(pmb.getAnnotated(), cls.getName()));
    }

    private void registerKpiMetricsDeferrableRequestContextSetterHandler(JaxRsCdiExtension jaxRs,
                                                                         JaxRsApplication applicationMeta) {

        Optional<String> namedRouting = jaxRs.findNamedRouting(config, applicationMeta);
        boolean routingNameRequired = jaxRs.isNamedRoutingRequired(config, applicationMeta);

        HttpRouting.Builder routing = routingBuilder(namedRouting, routingNameRequired, applicationMeta.appName());

        if (!routingsWithKPIMetrics.contains(routing)) {
            routingsWithKPIMetrics.add(routing);
            routing.any(KeyPerformanceIndicatorSupport.DeferrableRequestContext.CONTEXT_SETTING_HANDLER);
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, String.format("Adding deferrable request KPI metrics context for routing with name '%s'",
                                                      namedRouting.orElse("<unnamed>")));
            }
        }
    }

    private void startServer(@Observes @Priority(PLATFORM_AFTER + 100) @Initialized(ApplicationScoped.class) Object event,
                             BeanManager beanManager) {
        // update the status of server, as we may have been started without a builder being used
        // such as when cdi.Main or SeContainerInitializer are used
        if (!IN_PROGRESS_OR_RUNNING.compareAndSet(false, true)) {
            throw new IllegalStateException("There is another builder in progress, or another Server running. "
                                                    + "You cannot run more than one in parallel");
        }

        // redirect to the first page when root is accessed (if configured)
        registerDefaultRedirect();

        // register static content if configured
        registerStaticContent();

        // reactive services
        registerWebServerServices(beanManager);

        // JAX-RS applications (and resources)
        registerJaxRsApplications(beanManager);

        serverBuilder.addFeature(observeBuilder.build());

        // start the webserver
        serverBuilder.routing(routingBuilder);

        namedRouters.forEach((name, routerBuilder) -> {
            ListenerConfig listenerConfig = serverBuilder.sockets().get(name);
            ListenerConfig.Builder builder;
            if (listenerConfig == null) {
                builder = ListenerConfig.builder();
            } else {
                builder = ListenerConfig.builder(listenerConfig);
            }
            builder.addRoutings(routerBuilder.routings());
            serverBuilder.putSocket(name, builder.build());
        });

        namedRoutings.forEach((name, value) -> {
            ListenerConfig listenerConfig = serverBuilder.sockets().get(name);
            ListenerConfig.Builder builder;
            if (listenerConfig == null) {
                builder = ListenerConfig.builder();
            } else {
                builder = ListenerConfig.builder(listenerConfig);
            }
            builder.routing(value);
            serverBuilder.putSocket(name, builder.build());
        });

        Set<String> socketNames = serverBuilder.sockets().keySet();
        for (String socketName : socketNames) {
            if (DEFAULT_SOCKET_NAME.equals(socketName)) {
                continue;
            }
            if (namedRoutings.get(socketName) == null) {
                if (!observeBuilder.sockets().contains(socketName)) {
                    // retain original behavior
                    serverBuilder.routing(socketName, serverBuilder.routing().orElseGet(HttpRouting::builder).copy());
                }

            }
        }

        if (this.context == null) {
            this.context = Contexts.context().orElse(Context.builder()
                                                             .id("helidon-mp")
                                                             .build());
        }
        webserver = serverBuilder.build();

        try {
            webserver.start();
            started = true;
        } catch (Exception e) {
            throw new DeploymentException("Failed to start webserver", e);
        }

        this.port = webserver.port();

        ResumableSupport.get().checkpointResumeOnStartup();
        long initializationElapsedTime = ResumableSupport.get().uptime();

        String protocol = "http" + (webserver.hasTls() ? "s" : "");
        String host = "0.0.0.0".equals(listenHost) ? "localhost" : listenHost;
        String note = "0.0.0.0".equals(listenHost) ? " (and all other host addresses)" : "";

        LOGGER.log(Level.INFO, () -> "Server started on "
                + protocol + "://" + host + ":" + port
                + note + " in " + initializationElapsedTime + " milliseconds (since JVM startup).");

        // this is not needed at runtime, collect garbage
        serverBuilder = null;
        observeBuilder = null;
        routingBuilder = null;
        namedRoutings = null;
        namedRouters = null;

        STARTUP_LOGGER.log(Level.TRACE, "Server created");
    }

    private void registerJaxRsApplications(BeanManager beanManager) {
        JaxRsCdiExtension jaxRs = beanManager.getExtension(JaxRsCdiExtension.class);

        List<JaxRsApplication> jaxRsApplications = jaxRs.applicationsToRun();
        if (jaxRsApplications.isEmpty()) {
            LOGGER.log(Level.WARNING,
                       "There are no JAX-RS applications or resources. Maybe you forgot META-INF/beans.xml file?");
        } else {
            // Creates shared injection manager if multiple apps and "internal" property false
            boolean singleManager = config.get("server.single-injection-manager").asBoolean().asOptional().orElse(false);
            InjectionManager shared = jaxRsApplications.size() == 1 || singleManager ? null
                    : Injections.createInjectionManager();

            // If multiple apps, register all ParamConverterProvider's in shared manager to prevent
            // only those associated with the first application to be installed by Jersey
            if (shared != null) {
                List<? extends Application> instances = jaxRsApplications.stream()
                        .flatMap(app -> app.applicationClass().stream())
                        .flatMap(c -> CDI.current().select(c).stream())
                        .toList();
                instances.stream()
                        .flatMap(i -> i.getClasses().stream())
                        .filter(ParamConverterProvider.class::isAssignableFrom)
                        .forEach(c -> shared.register(Bindings.serviceAsContract(c).to(ParamConverterProvider.class)));
                instances.stream()
                        .flatMap(i -> i.getSingletons().stream())
                        .filter(s -> s instanceof ParamConverterProvider)
                        .forEach(s -> shared.register(Bindings.service(s)));
            }

            // Add all applications
            jaxRsApplications.forEach(it -> addApplication(jaxRs, it, shared));
        }
        STARTUP_LOGGER.log(Level.TRACE, "Registered jersey application(s)");
    }

    private void registerDefaultRedirect() {
        Optional.ofNullable(basePath)
                .or(() -> config.get("server.base-path").asString().asOptional())
                .ifPresent(basePath -> routingBuilder.any("/", (req, res) -> {
                    res.status(Status.MOVED_PERMANENTLY_301);
                    res.headers().set(HeaderNames.LOCATION, basePath);
                    res.send();
                }));
        STARTUP_LOGGER.log(Level.TRACE, "Builders ready");
    }

    private void registerStaticContent() {
        Config rootConfig = (Config) ConfigProvider.getConfig();
        Config config = rootConfig.get("server.static");

        if (config.exists()) {
            LOGGER.log(Level.WARNING, "Configuration of static content through \"server.static\" is now deprecated."
                    + " Please use \"server.features.static-content\", with sub-keys \"path\" and/or \"classpath\""
                    + " containing a list of handlers. At least \"context\" and \"location\" should be provided for each handler."
                    + " Location for classpath is the resource location with static content, for path it is the"
                    + " location on file system with the root of static content. For advanced configuration such as"
                    + " in-memory caching, temporary storage setup etc. kindly see our config reference for "
                    + "\"StaticContentFeature\" in documentation.");
        }

        config.get("classpath")
                .ifExists(this::registerClasspathStaticContent);

        config.get("path")
                .ifExists(this::registerPathStaticContent);

        Config featureConfig = rootConfig.get("server.features.static-content");
        if (featureConfig.exists()) {
            var builder = StaticContentConfig.builder()
                            .config(featureConfig);
            if (builder.welcome().isEmpty()) {
                builder.welcome("index.html");
            }
            addFeature(builder.build());
        }
    }

    @SuppressWarnings("removal")
    private void registerPathStaticContent(Config config) {
        Config context = config.get("context");
        io.helidon.webserver.staticcontent.StaticContentService.FileSystemBuilder pBuilder =
                io.helidon.webserver.staticcontent.StaticContentService.builder(config.get("location")
                                                                                               .as(Path.class)
                                                                                               .get());
        pBuilder.welcomeFileName(config.get("welcome")
                                         .asString()
                                         .orElse("index.html"));

        var staticContent = pBuilder.build();

        if (context.exists()) {
            routingBuilder.register(context.asString().get(), staticContent);
        } else {
            Supplier<io.helidon.webserver.staticcontent.StaticContentService> ms = () -> staticContent;
            routingBuilder.register(ms);
        }
        STARTUP_LOGGER.log(Level.TRACE, "Static path");
    }

    @SuppressWarnings("removal")
    private void registerClasspathStaticContent(Config config) {
        Config context = config.get("context");

        io.helidon.webserver.staticcontent.StaticContentService.ClassPathBuilder cpBuilder =
                io.helidon.webserver.staticcontent.StaticContentService.builder(config.get("location").asString().get());
        cpBuilder.welcomeFileName(config.get("welcome")
                                          .asString()
                                          .orElse("index.html"));
        config.get("tmp-dir")
                .as(Path.class)
                .ifPresent(cpBuilder::tmpDir);

        config.get("cache-in-memory")
                .asList(String.class)
                .stream()
                .flatMap(List::stream)
                .forEach(cpBuilder::addCacheInMemory);

        var staticContent = cpBuilder.build();

        if (context.exists()) {
            routingBuilder.register(context.asString().get(), staticContent);
        } else {
            routingBuilder.register(staticContent);
        }
        STARTUP_LOGGER.log(Level.TRACE, "Static classpath");
    }

    private void stopServer(@Observes @Priority(PLATFORM_BEFORE) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        try {
            if (started) {
                doStop();
            }
        } finally {
            // as there only can be a single CDI in a single JVM, once this CDI is shutting down, we
            // can start another one
            IN_PROGRESS_OR_RUNNING.set(false);
        }
    }

    private void doStop() {
        if (null == webserver || !started) {
            // nothing to do
            return;
        }
        long beforeT = System.nanoTime();

        try {
            webserver.stop();
            started = false;
        } finally {
            long t = TimeUnit.MILLISECONDS.convert(System.nanoTime() - beforeT, TimeUnit.NANOSECONDS);
            LOGGER.log(Level.INFO, () -> "Server stopped in " + t + " milliseconds.");
        }
    }

    private void addApplication(JaxRsCdiExtension jaxRs, JaxRsApplication applicationMeta,
                                InjectionManager injectionManager) {
        LOGGER.log(Level.INFO, "Registering JAX-RS Application: " + applicationMeta.appName());

        Optional<String> contextRoot = jaxRs.findContextRoot(config, applicationMeta);
        Optional<String> namedRouting = jaxRs.findNamedRouting(config, applicationMeta);
        boolean routingNameRequired = jaxRs.isNamedRoutingRequired(config, applicationMeta);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Application " + applicationMeta.appName()
                    + ", class: " + applicationMeta.appClassName()
                    + ", contextRoot: " + contextRoot
                    + ", namedRouting: " + namedRouting
                    + ", routingNameRequired: " + routingNameRequired);
        }

        HttpRouting.Builder routing = routingBuilder(namedRouting, routingNameRequired, applicationMeta.appName());

        JaxRsService jerseyHandler = jaxRs.toJerseySupport(applicationMeta, injectionManager);
        if (contextRoot.isPresent()) {
            String contextRootString = contextRoot.get();
            LOGGER.log(Level.DEBUG, () -> "JAX-RS application " + applicationMeta.appName() + " registered on '"
                    + contextRootString + "'");
            if (contextRootString.endsWith("/")) {
                routing.register(contextRootString.substring(0, contextRootString.length() - 1), jerseyHandler);
            } else {
                routing.register(contextRootString, jerseyHandler);
            }

        } else {
            LOGGER.log(Level.DEBUG, () -> "JAX-RS application " + applicationMeta.appName() + " registered on '/'");
            routing.register(jerseyHandler);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerWebServerServices(BeanManager beanManager) {
        List<Bean<?>> beans = prioritySort(beanManager.getBeans(HttpService.class));
        CreationalContext<Object> context = beanManager.createCreationalContext(null);

        for (Bean<?> bean : beans) {
            Bean<Object> objBean = (Bean<Object>) bean;
            HttpService service = (HttpService) objBean.create(context);
            registerWebServerService(serviceBeans.remove(bean), service);
        }
        STARTUP_LOGGER.log(Level.TRACE, "Registered WebServer services");
    }

    private void registerWebServerService(RoutingConfiguration routingConf, HttpService service) {

        String path = routingConf.routingPath(config);
        String routingName = routingConf.routingName(config);
        boolean routingNameRequired = routingConf.required(config);

        HttpRouting.Builder routing = findRouting(routingConf.configContext(),
                                                  routingName,
                                                  routingNameRequired);

        if ((null == path) || "/".equals(path)) {
            routing.register(service);
        } else {
            routing.register(path, service);
        }
    }

    private HttpRouting.Builder findRouting(String className,
                                            String routingName,
                                            boolean routingNameRequired) {
        if ((null == routingName) || RoutingName.DEFAULT_NAME.equals(routingName)) {
            return serverRoutingBuilder();
        }

        if (!serverBuilder.sockets().containsKey(routingName)) {
            // resolve missing socket configuration
            if (routingNameRequired) {
                throw new IllegalStateException(className
                                                        + " requires routing "
                                                        + routingName
                                                        + ", yet such a named socket is not configured for"
                                                        + " web server");
            }

            LOGGER.log(Level.DEBUG, () -> className + " is configured with named routing " + routingName + ". Such a routing"
                    + " is not configured, this service/application will run on default socket.");
            return serverRoutingBuilder();
        }

        return serverNamedRoutingBuilder(routingName);
    }
}
