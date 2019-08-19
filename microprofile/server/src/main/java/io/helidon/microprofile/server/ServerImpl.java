/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.inject.se.SeContainer;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.OptionalHelper;
import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.spi.MpServiceContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
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
    private static final StartedServers STARTED_SERVERS = new StartedServers();

    private final SeContainer container;
    private final boolean containerCreated;
    private final String host;
    private final WebServer server;
    private final Context context;
    private final boolean supportParallelRun;
    private int port = -1;

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

        applications
                .forEach(app -> {
                    JerseySupport js = JerseySupport.builder(app.resourceConfig())
                            .executorService(app.executorService()
                                                     .orElseGet(builder::defaultExecutorService))
                            .build();

                    if ("/".equals(app.contextRoot())) {
                        routingBuilder.register(js);
                    } else {
                        routingBuilder.register(app.contextRoot(), js);
                    }
                });

        STARTUP_LOGGER.finest("Registered jersey application(s)");

        WebServer.Builder serverBuilder = WebServer.builder(routingBuilder.build());
        namedRoutings.forEach(serverBuilder::addNamedRouting);

        server = serverBuilder.config(serverConfigBuilder.build())
                .build();

        STARTUP_LOGGER.finest("Server created");
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

        long beforeT = System.nanoTime();
        server.start()
                .whenComplete((webServer, throwable) -> {
                    if (null != throwable) {
                        STARTUP_LOGGER.log(Level.FINEST, "Startup failed", throwable);
                        throwRef.set(throwable);
                    } else {
                        long t = TimeUnit.MILLISECONDS.convert(System.nanoTime() - beforeT, TimeUnit.NANOSECONDS);

                        port = webServer.port();
                        STARTUP_LOGGER.finest("Started up");
                        if ("0.0.0.0".equals(host)) {
                            // listening on all addresses
                            LOGGER.info(() -> "Server started on http://localhost:" + port + " (and all other host addresses) "
                                    + "in " + t + " milliseconds.");
                        } else {
                            LOGGER.info(() -> "Server started on http://" + host + ":" + port + " in " + t + " milliseconds.");
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
            return this;
        } else {
            throw new MpException("Failed to start server", throwRef.get());
        }

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
        return port;
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
