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

package io.helidon.microprofile.tyrus;

import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.WebServer;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
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
public class TyrusCdiExtension implements Extension {
    private static final System.Logger LOGGER = System.getLogger(TyrusCdiExtension.class.getName());
    private static final String DEFAULT_WEBSOCKET_PATH = "/";
    private final TyrusApplication.Builder appBuilder = TyrusApplication.builder();
    private Config config;
    private ServerCdiExtension serverCdiExtension;

    /**
     * Overrides a websocket application class.
     *
     * @param applicationClass Application class.
     */
    public void applicationClass(Class<? extends ServerApplicationConfig> applicationClass) {
        LOGGER.log(Level.TRACE, () -> "Using manually set application class  " + applicationClass);
        appBuilder.updateApplicationClass(applicationClass);
    }

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
        LOGGER.log(Level.TRACE, () -> "Application class found " + applicationClass.getAnnotatedType().getJavaClass());
        appBuilder.applicationClass(applicationClass.getAnnotatedType().getJavaClass());
    }

    /**
     * Collect annotated endpoints.
     *
     * @param endpoint The endpoint.
     */
    void endpointClasses(@Observes @WithAnnotations(ServerEndpoint.class) ProcessAnnotatedType<?> endpoint) {
        LOGGER.log(Level.TRACE, () -> "Annotated endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.annotatedEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects programmatic endpoints.
     *
     * @param endpoint The endpoint.
     */
    void endpointConfig(@Observes ProcessAnnotatedType<? extends Endpoint> endpoint) {
        LOGGER.log(Level.TRACE, () -> "Programmatic endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.programmaticEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects extensions.
     *
     * @param extension The extension.
     */
    void extension(@Observes ProcessAnnotatedType<? extends jakarta.websocket.Extension> extension) {
        LOGGER.log(Level.TRACE, () -> "Extension found " + extension.getAnnotatedType().getJavaClass());

        Class<? extends jakarta.websocket.Extension> cls = extension.getAnnotatedType().getJavaClass();
        try {
            jakarta.websocket.Extension instance = cls.getConstructor().newInstance();
            appBuilder.extension(instance);
        } catch (NoSuchMethodException e) {
            LOGGER.log(Level.WARNING, () -> "Extension does not have no-args constructor for "
                    + extension.getAnnotatedType().getJavaClass() + "! Skipping.");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to load WebSocket extension", e);
        }
    }

    private void registerWebSockets() {
        try {
            TyrusApplication app = appBuilder.build();
            TyrusRouting.Builder tyrusRoutingBuilder = TyrusRouting.builder();

            Set<Class<? extends ServerApplicationConfig>> appClasses = app.applicationClasses();
            if (appClasses.isEmpty()) {
                // Direct registration without calling application class
                app.annotatedEndpoints().forEach(aClass -> tyrusRoutingBuilder.endpoint(DEFAULT_WEBSOCKET_PATH, aClass));
                app.programmaticEndpoints().forEach(wsCfg -> tyrusRoutingBuilder.endpoint(DEFAULT_WEBSOCKET_PATH, wsCfg));
                app.extensions().forEach(tyrusRoutingBuilder::extension);

                // Create routing
                serverCdiExtension.addRouting(tyrusRoutingBuilder);
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
                    endpointClasses.forEach(aClass -> tyrusRoutingBuilder.endpoint(rootPath, aClass));
                    endpointConfigs.forEach(wsCfg -> tyrusRoutingBuilder.endpoint(rootPath, wsCfg));

                    // Create routing
                    serverCdiExtension.addRouting(tyrusRoutingBuilder,
                                                  namedRouting.orElse(WebServer.DEFAULT_SOCKET_NAME),
                                                  routingNameRequired,
                                                  appClass.getName());
                });
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to load WebSocket extension", e);
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
