/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.inject.se.SeContainer;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.spi.MpService;
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

    private static final int DEFAULT_PRIORITY = 100;

    private final SeContainer container;
    private final boolean containerCreated;
    private final String host;
    private final WebServer server;
    private final IdentityHashMap<Class<?>, Object> register = new IdentityHashMap<>();
    private int port = -1;

    ServerImpl(Builder builder) {
        MpConfig mpConfig = (MpConfig) builder.getConfig();
        Config config = mpConfig.getConfig();
        this.container = builder.getCdiContainer();
        this.containerCreated = builder.getContainerCreated();

        InetAddress listenHost;
        if (null == builder.getHost()) {
            listenHost = InetAddress.getLoopbackAddress();
        } else {
            try {
                listenHost = InetAddress.getByName(builder.getHost());
            } catch (UnknownHostException e) {
                throw new MpException("Failed to create address for host: " + getHost(), e);
            }
        }
        this.host = listenHost.getHostName();

        Routing.Builder routingBuilder = Routing.builder();
        Config serverConfig = config.get("server");
        ServerConfiguration.Builder serverConfigBuilder = ServerConfiguration.builder(serverConfig)
                .port(builder.getPort())
                .bindAddress(listenHost);

        STARTUP_LOGGER.finest("Builders ready");

        List<JaxRsApplication> applications = builder.getApplications();
        loadExtensions(builder, mpConfig, config, applications, routingBuilder, serverConfigBuilder);

        STARTUP_LOGGER.finest("Extensions loaded");

        applications.stream().map(JaxRsApplication::getConfig).forEach(resourceConfig -> {
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

            StaticContentSupport.Builder cpBuilder = StaticContentSupport.builder(cpConfig.get("location").asString());
            cpBuilder.welcomeFileName(cpConfig.get("welcome")
                                              .value()
                                              .orElse("index.html"));
            StaticContentSupport staticContent = cpBuilder.build();

            if (context.exists()) {
                routingBuilder.register(context.asString(), staticContent);
            } else {
                routingBuilder.register(staticContent);
            }
        });

        STARTUP_LOGGER.finest("Static classpath");

        serverConfig.get("static.path").ifExists(pathConfig -> {
            Config context = pathConfig.get("context");
            StaticContentSupport.Builder pBuilder = StaticContentSupport.builder(pathConfig.get("location").as(Path.class));
            pathConfig.get("welcome")
                    .value()
                    .ifPresent(pBuilder::welcomeFileName);
            StaticContentSupport staticContent = pBuilder.build();

            if (context.exists()) {
                routingBuilder.register(context.asString(), staticContent);
            } else {
                routingBuilder.register(staticContent);
            }
        });

        STARTUP_LOGGER.finest("Static path");

        applications
                .forEach(app -> {
                    JerseySupport js = JerseySupport.builder(app.getConfig())
                            .executorService(app.getExecutorService()
                                                     .orElseGet(builder::getDefaultExecutorService))
                            .build();

                    if ("/".equals(app.getContextRoot())) {
                        routingBuilder.register(js);
                    } else {
                        routingBuilder.register(app.getContextRoot(), js);
                    }
                });

        STARTUP_LOGGER.finest("Registered jersey application(s)");

        server = routingBuilder
                .build()
                .createServer(serverConfigBuilder.build());

        STARTUP_LOGGER.finest("Server created");
    }

    private static int findPriority(Class<?> aClass) {
        Priority priorityAnnot = aClass.getAnnotation(Priority.class);
        if (null != priorityAnnot) {
            return priorityAnnot.value();
        }

        return DEFAULT_PRIORITY;
    }

    private void loadExtensions(Builder builder,
                                MpConfig mpConfig,
                                Config config,
                                List<JaxRsApplication> apps,
                                Routing.Builder routingBuilder, ServerConfiguration.Builder serverConfigBuilder) {
        // extensions
        List<MpService> extensions = new LinkedList<>(builder.getExtensions());
        ServiceLoader.load(MpService.class).forEach(extensions::add);

        List<JaxRsApplication> newApps = new LinkedList<>();
        // TODO order by Priority

        MpServiceContext context = new MpServiceContext() {
            @Override
            public org.eclipse.microprofile.config.Config getConfig() {
                return mpConfig;
            }

            @Override
            public List<ResourceConfig> getApplications() {
                return apps.stream()
                        .map(JaxRsApplication::getConfig)
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
            public Config getHelidonConfig() {
                return config;
            }

            @Override
            public SeContainer getCdiContainer() {
                return container;
            }

            @Override
            public ServerConfiguration.Builder getServerConfigBuilder() {
                return serverConfigBuilder;
            }

            @Override
            public Routing.Builder getServerRoutingBuilder() {
                return routingBuilder;
            }

            @Override
            public <U> void register(Class<? extends U> key, U instance) {
                register.put(key, instance);
            }
        };
        for (MpService extension : extensions) {
            extension.configure(context);
            apps.addAll(newApps);
            newApps.clear();
        }
    }

    @Override
    public SeContainer getContainer() {
        return container;
    }

    @Override
    public Server start() {
        STARTUP_LOGGER.entering(ServerImpl.class.getName(), "start");

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
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }
}
