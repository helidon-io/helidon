/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.websocket.WebSocketRouting;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Configure Tyrus related things.
 */
public class WebSocketCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(WebSocketCdiExtension.class.getName());

    private static final String DEFAULT_WEBSOCKET_PATH = "/";

    private Config config;

    private ServerCdiExtension serverCdiExtension;

    private final WebSocketApplication.Builder appBuilder = WebSocketApplication.builder();
    private ExecutorService executorService;

    void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    void startServer(@Observes @Priority(PLATFORM_AFTER + 99) @Initialized(ApplicationScoped.class) Object event,
                             BeanManager beanManager) {
        serverCdiExtension = beanManager.getExtension(ServerCdiExtension.class);
        registerWebSockets();
    }

    /**
     * Collect application class extending {@code ServerApplicationConfig}.
     *
     * @param applicationClass Application class.
     */
    void applicationClass(@Observes ProcessAnnotatedType<? extends ServerApplicationConfig> applicationClass) {
        LOGGER.finest(() -> "Application class found " + applicationClass.getAnnotatedType().getJavaClass());
        appBuilder.applicationClass(applicationClass.getAnnotatedType().getJavaClass());
    }

    /**
     * Overrides a websocket application class.
     *
     * @param applicationClass Application class.
     */
    public void applicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
        LOGGER.finest(() -> "Using manually set application class  " + applicationClass);
        appBuilder.updateApplicationClass(applicationClass);
    }

    /**
     * Collect annotated endpoints.
     *
     * @param endpoint The endpoint.
     */
    void endpointClasses(@Observes @WithAnnotations(ServerEndpoint.class) ProcessAnnotatedType<?> endpoint) {
        LOGGER.finest(() -> "Annotated endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.annotatedEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects programmatic endpoints.
     *
     * @param endpoint The endpoint.
     */
    void endpointConfig(@Observes ProcessAnnotatedType<? extends Endpoint> endpoint) {
        LOGGER.finest(() -> "Programmatic endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.programmaticEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects extensions.
     *
     * @param extension The extension.
     */
    void extension(@Observes ProcessAnnotatedType<? extends jakarta.websocket.Extension> extension) {
        LOGGER.finest(() -> "Extension found " + extension.getAnnotatedType().getJavaClass());

        Class<? extends jakarta.websocket.Extension> cls = extension.getAnnotatedType().getJavaClass();
        try {
            jakarta.websocket.Extension instance = cls.getConstructor().newInstance();
            appBuilder.extension(instance);
        } catch (NoSuchMethodException e) {
            LOGGER.warning(() -> "Extension does not have no-args constructor for "
                    + extension.getAnnotatedType().getJavaClass() + "! Skppping.");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to load WebSocket extension", e);
        }
    }

    /**
     * Provides access to websocket application.
     *
     * @return Application.
     */
    WebSocketApplication toWebSocketApplication() {
        return appBuilder.build();
    }

    private void registerWebSockets() {
        try {
            WebSocketApplication app = toWebSocketApplication();

            // If application present call its methods
            WebSocketRouting.Builder wsRoutingBuilder = WebSocketRouting.builder();

            executorService = ThreadPoolSupplier.builder()
                    .threadNamePrefix("helidon-websocket-")
                    .build().get();

            wsRoutingBuilder.executor(executorService);

            Set<Class<? extends ServerApplicationConfig>> appClasses = app.applicationClasses();

            if (appClasses.isEmpty()) {
                // Direct registration without calling application class
                app.annotatedEndpoints().forEach(aClass -> wsRoutingBuilder.endpoint(DEFAULT_WEBSOCKET_PATH, aClass));
                app.programmaticEndpoints().forEach(wsCfg -> wsRoutingBuilder.endpoint(DEFAULT_WEBSOCKET_PATH, wsCfg));
                app.extensions().forEach(wsRoutingBuilder::extension);

                // Create routing wsRoutingBuilder
                serverCdiExtension.serverBuilder().addRouting(wsRoutingBuilder.build());
            } else {
                appClasses.forEach(appClass -> {
                    Optional<String> contextRoot = findContextRoot(config, appClass);
                    String rootPath = contextRoot.orElse(DEFAULT_WEBSOCKET_PATH);
                    Optional<String> namedRouting = findNamedRouting(config, appClass);
                    boolean routingNameRequired = isNamedRoutingRequired(config, appClass);

                    // Attempt to instantiate via CDI
                    ServerApplicationConfig instance = null;
                    try {
                        instance = CDI.current().select(appClass).get();
                    } catch (UnsatisfiedResolutionException e) {
                        // falls through
                    }

                    // Otherwise, we create instance directly
                    if (instance == null) {
                        try {
                            instance = appClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException("Unable to instantiate websocket application " + appClass, e);
                        }
                    }

                    // Call methods in application class
                    Set<ServerEndpointConfig> endpointConfigs = instance.getEndpointConfigs(app.programmaticEndpoints());
                    Set<Class<?>> endpointClasses = instance.getAnnotatedEndpointClasses(app.annotatedEndpoints());

                    // Register classes and configs
                    endpointClasses.forEach(aClass -> wsRoutingBuilder.endpoint(rootPath, aClass));
                    endpointConfigs.forEach(wsCfg -> wsRoutingBuilder.endpoint(rootPath, wsCfg));

                    // Create routing wsRoutingBuilder
                    addWsRouting(wsRoutingBuilder.build(), namedRouting, routingNameRequired, appClass.getName());
                });
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to load WebSocket extension", e);
        }
    }

    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        executorService.shutdown();
    }

    private void addWsRouting(WebSocketRouting routing,
                             Optional<String> namedRouting,
                             boolean routingNameRequired,
                             String appName) {
        WebServer.Builder serverBuilder = serverCdiExtension.serverBuilder();
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

                    serverBuilder.addRouting(routing);
                }
            } else {
                serverBuilder.addNamedRouting(socket, routing);
            }
        } else {
            serverBuilder.addRouting(routing);
        }
    }

    private Optional<String> findContextRoot(io.helidon.config.Config config,
                                             Class<? extends ServerApplicationConfig> applicationClass) {
        return config.get(applicationClass.getName() + "." + RoutingPath.CONFIG_KEY_PATH)
                .asString()
                .or(() -> Optional.ofNullable(applicationClass.getAnnotation(RoutingPath.class))
                        .map(RoutingPath::value))
                .map(path -> path.startsWith("/") ? path : ("/" + path));
    }

    private Optional<String> findNamedRouting(io.helidon.config.Config config,
                                              Class<? extends ServerApplicationConfig> applicationClass) {
        return config.get(applicationClass.getName() + "." + RoutingName.CONFIG_KEY_NAME)
                .asString()
                .or(() -> Optional.ofNullable(applicationClass.getAnnotation(RoutingName.class))
                        .map(RoutingName::value))
                .flatMap(name -> RoutingName.DEFAULT_NAME.equals(name) ? Optional.empty() : Optional.of(name));
    }

    private boolean isNamedRoutingRequired(io.helidon.config.Config config,
                                           Class<? extends ServerApplicationConfig> applicationClass) {
        return config.get(applicationClass.getName() + "." + RoutingName.CONFIG_KEY_REQUIRED)
                .asBoolean()
                .or(() -> Optional.ofNullable(applicationClass.getAnnotation(RoutingName.class))
                        .map(RoutingName::required))
                .orElse(false);
    }
}
