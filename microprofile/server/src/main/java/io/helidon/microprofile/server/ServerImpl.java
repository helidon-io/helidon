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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.OptionalHelper;
import io.helidon.common.Prioritized;
import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.Service;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.StaticContentSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * Server to handle lifecycle of microprofile implementation.
 */
public class ServerImpl implements Server {
    private static final Logger LOGGER = Logger.getLogger(ServerImpl.class.getName());
    private static final Logger JERSEY_LOGGER = Logger.getLogger(ServerImpl.class.getName() + ".jersey");
    private static final Logger STARTUP_LOGGER = Logger.getLogger("io.helidon.microprofile.startup.server");
    private static final String EXIT_ON_STARTED_KEY = "exit.on.started";
    private static final boolean EXIT_ON_STARTED = "!".equals(System.getProperty(EXIT_ON_STARTED_KEY));
    private static final StartedServers STARTED_SERVERS = new StartedServers();

    private static long initStartupTime = System.nanoTime();
    private static long initFinishTime = -1;

    private final SeContainer container;
    private final boolean containerCreated;
    private final String host;
    private final WebServer server;
    private final Context context;
    private final boolean supportParallelRun;
    private int port = -1;
    private boolean isInitTimingLogged = false;

    static void recordInitStart(long time) {
        if (time < initStartupTime) {
            initStartupTime = time;
        }
    }

    private static boolean recordInitFinish(long time) {
        boolean result = initFinishTime == -1;
        if (result) {
            initFinishTime = time;
        }
        return result;
    }

    ServerImpl(Builder builder) {
        MpConfig mpConfig = (MpConfig) builder.config();
        Config config = mpConfig.helidonConfig();
        this.container = builder.cdiContainer();
        this.containerCreated = builder.containerCreated();
        this.context = builder.context();
        this.supportParallelRun = builder.supportParallelRun();

        InetAddress listenHost;
        if (null == builder.host()) {
            listenHost = InetAddress.getLoopbackAddress();
        } else {
            try {
                listenHost = InetAddress.getByName(builder.host());
            } catch (UnknownHostException e) {
                throw new MpException("Failed to create address for host: " + builder.host(), e);
            }
        }
        this.host = listenHost.getHostName();

        BeanManager beanManager = container.getBeanManager();
        Routing.Builder routingBuilder = Routing.builder();

        Config serverConfig = config.get("server");

        ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder(serverConfig)
                .context(this.context)
                .port(builder.port())
                .bindAddress(listenHost);

        OptionalHelper.from(Optional.ofNullable(builder.basePath()))
                .or(() -> config.get("server.base-path").asString().asOptional())
                .asOptional()
                .ifPresent(basePath -> {
                    routingBuilder.any("/", (req, res) -> {
                        res.status(Http.Status.MOVED_PERMANENTLY_301);
                        res.headers().put(Http.Header.LOCATION, basePath);
                        res.send();
                    });
                });

        STARTUP_LOGGER.finest("Builders ready");

        List<JaxRsApplication> applications = builder.applications();
        Map<String, Routing.Builder> namedRoutings = new HashMap<>();
        loadExtensions(builder, mpConfig, config, applications, routingBuilder, namedRoutings, serverConfigBuilder);

        STARTUP_LOGGER.finest("Extensions loaded");

        applications.stream().map(JaxRsApplication::resourceConfig).forEach(resourceConfig -> {
            // do not remove the "new ExceptionMapper<Exception>", as otherwise Jersey loses the generics info and does not
            // trigger the handler
            resourceConfig.register(new ExceptionMapper<Exception>() {
                @Override
                public Response toResponse(Exception exception) {
                    if (exception instanceof WebApplicationException) {
                        return ((WebApplicationException) exception).getResponse();
                    } else {
                        JERSEY_LOGGER.log(Level.WARNING, exception, () -> "Internal server error");
                        return Response.serverError().build();
                    }
                }
            });
        });

        serverConfig.get("static.classpath").ifExists(cpConfig -> {
            Config context = cpConfig.get("context");

            StaticContentSupport.Builder cpBuilder = StaticContentSupport.builder(cpConfig.get("location").asString().get());
            cpBuilder.welcomeFileName(cpConfig.get("welcome")
                                              .asString()
                                              .orElse("index.html"));
            StaticContentSupport staticContent = cpBuilder.build();

            if (context.exists()) {
                routingBuilder.register(context.asString().get(), staticContent);
            } else {
                routingBuilder.register(staticContent);
            }
        });

        STARTUP_LOGGER.finest("Static classpath");

        serverConfig.get("static.path").ifExists(pathConfig -> {
            Config context = pathConfig.get("context");
            StaticContentSupport.Builder pBuilder = StaticContentSupport.builder(pathConfig.get("location").as(Path.class).get());
            pathConfig.get("welcome")
                    .asString()
                    .ifPresent(pBuilder::welcomeFileName);
            StaticContentSupport staticContent = pBuilder.build();

            if (context.exists()) {
                routingBuilder.register(context.asString().get(), staticContent);
            } else {
                routingBuilder.register(staticContent);
            }
        });

        STARTUP_LOGGER.finest("Static path");
        ServerConfiguration serverConfiguration = serverConfigBuilder.build();

        registerJerseyApplications(config,
                                   routingBuilder,
                                   namedRoutings,
                                   serverConfiguration,
                                   applications,
                                   builder::defaultExecutorService);

        STARTUP_LOGGER.finest("Registered jersey application(s)");

        registerWebServerServices(config, beanManager, routingBuilder, namedRoutings, serverConfiguration);

        STARTUP_LOGGER.finest("Registered WebServer services");

        WebServer.Builder serverBuilder = WebServer.builder(routingBuilder.build());
        namedRoutings.forEach(serverBuilder::addNamedRouting);

        server = serverBuilder.config(serverConfiguration)
                .build();

        STARTUP_LOGGER.finest("Server created");
    }

    private static void registerJerseyApplications(Config config,
                                                   Routing.Builder routingBuilder,
                                                   Map<String, Routing.Builder> namedRoutings,
                                                   ServerConfiguration serverConfiguration,
                                                   List<JaxRsApplication> applications,
                                                   Supplier<ExecutorService> defaultExecService) {
        applications
                .forEach(app -> {
                    JerseySupport js = JerseySupport.builder(app.resourceConfig())
                            .config(config.get("server.jersey"))
                            .executorService(app.executorService()
                                                     .orElseGet(defaultExecService))
                            .build();

                    Routing.Rules routing = findRouting(config,
                                                        serverConfiguration,
                                                        routingBuilder,
                                                        namedRoutings,
                                                        app.routingName(),
                                                        app.routingNameRequired(),
                                                        app.appClassName());

                    registerService(config,
                                    routing,
                                    app.contextRoot(),
                                    app.appClassName(),
                                    js);
                });
    }

    @SuppressWarnings("unchecked")
    private static void registerWebServerServices(Config config,
                                                  BeanManager beanManager,
                                                  Routing.Builder routingBuilder,
                                                  Map<String, Routing.Builder> namedRoutings,
                                                  ServerConfiguration serverConfiguration) {

        CreationalContext<Object> context = beanManager.createCreationalContext(null);
        List<Bean<?>> wsServicesSorted = prioritySort(beanManager.getBeans(Service.class));

        for (Bean<?> serviceBean : wsServicesSorted) {
            Bean<Object> theBean = (Bean<Object>) serviceBean;
            Class<?> serviceClass = theBean.getBeanClass();
            String className = serviceClass.getName();
            Service service = (Service) theBean.create(context);
            RoutingPath rp = serviceClass.getAnnotation(RoutingPath.class);
            RoutingName rn = serviceClass.getAnnotation(RoutingName.class);
            String path = (null == rp) ? null : rp.value();
            String routingName = (null == rn) ? null : rn.value();
            boolean routingNameRequired = (null != rn) && rn.required();

            path = config.get(className + "." + RoutingPath.CONFIG_KEY_PATH).asString().orElse(path);

            Routing.Rules routing = findRouting(config,
                                                serverConfiguration,
                                                routingBuilder,
                                                namedRoutings,
                                                routingName,
                                                routingNameRequired,
                                                className);

            registerService(config,
                            routing,
                            path,
                            className,
                            service);
        }
    }

    private static Routing.Rules findRouting(Config config,
                                             ServerConfiguration serverConfiguration,
                                             Routing.Builder routingBuilder,
                                             Map<String, Routing.Builder> namedRoutings,
                                             String routingNameParam,
                                             boolean routingNameRequiredParam,
                                             String className) {
        String routingName = config.get(className + "." + RoutingName.CONFIG_KEY_NAME).asString().orElse(routingNameParam);
        boolean routingNameRequired = config.get(className + "." + RoutingName.CONFIG_KEY_REQUIRED).asBoolean()
                .orElse(routingNameRequiredParam);

        if ((null == routingName) || RoutingName.DEFAULT_NAME.equals(routingName)) {
            return routingBuilder;
        } else {

            SocketConfiguration socket = serverConfiguration.socket(routingName);
            if (null == socket) {
                if (routingNameRequired) {
                    throw new IllegalStateException(className + " requires routing " + routingName
                                                            + ", yet such a named socket is not configured for web server");
                }
                LOGGER.fine(() -> className + " is configured with named routing " + routingName + ". Such a routing"
                        + " is not configured, this service/application will run on default socket.");
                return routingBuilder;
            } else {
                return namedRoutings.computeIfAbsent(routingName, it -> Routing.builder());
            }
        }

    }

    private static void registerService(Config config,
                                        Routing.Rules rules,
                                        String pathParam,
                                        String className,
                                        Service service) {

        String path = config.get(className + "." + RoutingPath.CONFIG_KEY_PATH).asString().orElse(pathParam);

        if ((null == path) || "/".equals(path)) {
            rules.register(service);
        } else {
            rules.register(path, service);
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

    private void loadExtensions(Builder builder,
                                MpConfig mpConfig,
                                Config config,
                                List<JaxRsApplication> apps,
                                Routing.Builder routingBuilder,
                                Map<String, Routing.Builder> namedRouting,
                                ServerConfiguration.Builder serverConfigBuilder) {

        List<JaxRsApplication> newApps = new LinkedList<>();

        MpServiceContext context = createExtensionContext(mpConfig,
                                                          config,
                                                          apps,
                                                          routingBuilder,
                                                          namedRouting,
                                                          serverConfigBuilder,
                                                          newApps);

        // extensions
        builder.extensions()
                .forEach(extension -> {
                    extension.configure(context);
                    apps.addAll(newApps);
                    newApps.clear();
                });
    }

    private MpServiceContext createExtensionContext(MpConfig mpConfig,
                                                    Config config,
                                                    List<JaxRsApplication> apps,
                                                    Routing.Builder routingBuilder,
                                                    Map<String, Routing.Builder> namedRouting,
                                                    ServerConfiguration.Builder serverConfigBuilder,
                                                    List<JaxRsApplication> newApps) {
        return new MpServiceContext() {
            @Override
            public org.eclipse.microprofile.config.Config config() {
                return mpConfig;
            }

            @Override
            public List<ResourceConfig> applications() {
                return apps.stream()
                        .map(JaxRsApplication::resourceConfig)
                        .collect(Collectors.toList());
            }

            @Override
            public void addApplication(Application application) {
                newApps.add(JaxRsApplication.create(application));
            }

            @Override
            public void addApplication(String contextRoot, Application application) {
                newApps.add(JaxRsApplication.builder().contextRoot(contextRoot)
                                    .application(application).build());
            }

            @Override
            public Config helidonConfig() {
                return config;
            }

            @Override
            public SeContainer cdiContainer() {
                return container;
            }

            @Override
            public ServerConfiguration.Builder serverConfigBuilder() {
                return serverConfigBuilder;
            }

            @Override
            public Routing.Builder serverRoutingBuilder() {
                return routingBuilder;
            }

            @Override
            public Routing.Builder serverNamedRoutingBuilder(String name) {
                return namedRouting.computeIfAbsent(name, routeName -> Routing.builder());
            }

            @Override
            public <U> void register(Class<? extends U> key, U instance) {
                context.register(instance);
            }

            @Override
            public void register(Object instance) {
                context.register(instance);
            }

            @Override
            public void register(Object classifier, Object instance) {
                context.register(classifier, instance);
            }
        };
    }

    @Override
    public SeContainer cdiContainer() {
        return container;
    }

    @Override
    public Server start() {
        STARTUP_LOGGER.entering(ServerImpl.class.getName(), "start");

        STARTED_SERVERS.start(this);

        CountDownLatch cdl = new CountDownLatch(1);
        AtomicReference<Throwable> throwRef = new AtomicReference<>();

        server.start()
                .whenComplete((webServer, throwable) -> {
                    if (null != throwable) {
                        STARTUP_LOGGER.log(Level.FINEST, "Startup failed", throwable);
                        throwRef.set(throwable);
                    } else {
                        boolean reportInitTime = recordInitFinish(System.nanoTime());
                        port = webServer.port();
                        STARTUP_LOGGER.finest("Started up");
                        if (reportInitTime) {
                            /*
                             * Report initialization time only during the first server start; init
                             * includes most notably CDI initialization and server start-up.
                             */
                            long initializationElapsedTime =
                                TimeUnit.MILLISECONDS.convert(initFinishTime - initStartupTime,
                                        TimeUnit.NANOSECONDS);

                            if ("0.0.0.0".equals(host)) {
                                // listening on all addresses
                                LOGGER.info(() -> "Server initialized on http://localhost:" + port + " (and all other host addresses)"
                                        + " in " + initializationElapsedTime + " milliseconds.");
                            } else {
                                LOGGER.info(() -> "Server initialized on http://" + host + ":" + port
                                        + " in " + initializationElapsedTime + " milliseconds.");
                            }
                        }
                    }
                    cdl.countDown();
                });

        try {
            cdl.await();
            STARTUP_LOGGER.finest("Count down latch released");
        } catch (InterruptedException e) {
            throw new MpException("Interrupted while starting server", e);
        }

        if (throwRef.get() == null) {
            if (EXIT_ON_STARTED) {
                exitOnStarted();
            }
            return this;
        } else {
            throw new MpException("Failed to start server", throwRef.get());
        }
    }

    private void exitOnStarted() {
        LOGGER.info(String.format("Exiting, -D%s set.",  EXIT_ON_STARTED_KEY));
        System.exit(0);
    }

    @Override
    public Server stop() {
        try {
            stopWebServer();
        } finally {
            if (containerCreated) {
                try {
                    container.close();
                } catch (IllegalStateException e) {
                    LOGGER.log(Level.SEVERE, "Container already closed", e);
                }
            }
            STARTED_SERVERS.stop(this);
        }

        return this;
    }

    private void stopWebServer() {
        CountDownLatch cdl = new CountDownLatch(1);
        AtomicReference<Throwable> throwRef = new AtomicReference<>();

        long beforeT = System.nanoTime();
        server.shutdown()
                .whenComplete((webServer, throwable) -> {
                    if (null != throwable) {
                        throwRef.set(throwable);
                    } else {
                        long t = TimeUnit.MILLISECONDS.convert(System.nanoTime() - beforeT, TimeUnit.NANOSECONDS);
                        LOGGER.info(() -> "Server stopped in " + t + " milliseconds.");
                    }
                    cdl.countDown();
                });

        try {
            cdl.await();
        } catch (InterruptedException e) {
            throw new MpException("Interrupted while shutting down server", e);
        }

        if (throwRef.get() != null) {
            throw new MpException("Failed to shut down server", throwRef.get());
        }
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return server.port();
    }

    @Override
    public int port(String socketName) {
        return server.port(socketName);
    }

    private static final class StartedServers {
        private final Map<ServerImpl, Boolean> runningServers = new IdentityHashMap<>();
        private boolean parallelSupported = false;

        private StartedServers() {
        }

        synchronized void start(ServerImpl server) {
            if (runningServers.isEmpty()) {
                // this is the first server
                runningServers.put(server, true);
                parallelSupported = server.supportParallelRun;
                return;
            }

            // check if parallel supported
            if (parallelSupported && server.supportParallelRun) {
                // parallel runtime is supported - explicitly enabled
                LOGGER.info("You are using an unsupported configuration of running more than one MP Server in the same JVM");
                runningServers.put(server, true);
                return;
            }

            // parallel is not supported, throw an exception
            List<Integer> ports = runningServers.keySet()
                    .stream()
                    .map(ServerImpl::port)
                    .collect(Collectors.toList());

            throw new IllegalStateException("There are already running MP servers on ports " + ports + " in this JVM. You are"
                                                    + " trying to start another "
                                                    + "server on port " + server.port + ". This is not supported. "
                                                    + "If you want to do it (even if not supported), you "
                                                    + "can configure server.support-parallel configuration option"
                                                    + " or explicitly call supportParallel method on builder to enable"
                                                    + " this support on all Server instances.");
        }

        synchronized void stop(ServerImpl server) {
            runningServers.remove(server);
        }
    }
}
