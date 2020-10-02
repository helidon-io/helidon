/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;
import io.helidon.webserver.tyrus.TyrusSupport;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Configure Tyrus related things.
 */
public class WebSocketCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(WebSocketCdiExtension.class.getName());

    private static final String DEFAULT_WEBSOCKET_PATH = "/";

    private Config config;

    private ServerCdiExtension serverCdiExtension;

    private final WebSocketApplication.Builder appBuilder = WebSocketApplication.builder();

    private void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    private void startServer(@Observes @Priority(PLATFORM_AFTER + 99) @Initialized(ApplicationScoped.class) Object event,
                             BeanManager beanManager) {
        serverCdiExtension = beanManager.getExtension(ServerCdiExtension.class);
        registerWebSockets();
    }

    /**
     * Collect application class extending {@code ServerApplicationConfig}.
     *
     * @param applicationClass Application class.
     */
    private void applicationClass(@Observes ProcessAnnotatedType<? extends ServerApplicationConfig> applicationClass) {
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
    private void endpointClasses(@Observes @WithAnnotations(ServerEndpoint.class) ProcessAnnotatedType<?> endpoint) {
        LOGGER.finest(() -> "Annotated endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.annotatedEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects programmatic endpoints.
     *
     * @param endpoint The endpoint.
     */
    private void endpointConfig(@Observes ProcessAnnotatedType<? extends Endpoint> endpoint) {
        LOGGER.finest(() -> "Programmatic endpoint found " + endpoint.getAnnotatedType().getJavaClass());
        appBuilder.programmaticEndpoint(endpoint.getAnnotatedType().getJavaClass());
    }

    /**
     * Collects extensions.
     *
     * @param extension The extension.
     */
    private void extension(@Observes ProcessAnnotatedType<? extends javax.websocket.Extension> extension) {
        LOGGER.finest(() -> "Extension found " + extension.getAnnotatedType().getJavaClass());

        Class<? extends javax.websocket.Extension> cls = extension.getAnnotatedType().getJavaClass();
        try {
            javax.websocket.Extension instance = cls.getConstructor().newInstance();
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
            TyrusSupport.Builder builder = TyrusSupport.builder();
            Optional<Class<? extends ServerApplicationConfig>> appClass = app.applicationClass();

            Optional<String> contextRoot = appClass.flatMap(c -> findContextRoot(config, c));
            Optional<String> namedRouting = appClass.flatMap(c -> findNamedRouting(config, c));
            boolean routingNameRequired = appClass.map(c -> isNamedRoutingRequired(config, c)).orElse(false);

            Routing.Builder routing;
            if (appClass.isPresent()) {
                Class<? extends ServerApplicationConfig> c = appClass.get();

                // Attempt to instantiate via CDI
                ServerApplicationConfig instance = null;
                try {
                    instance = CDI.current().select(c).get();
                } catch (UnsatisfiedResolutionException e) {
                    // falls through
                }

                // Otherwise, we create instance directly
                if (instance == null) {
                    try {
                        instance = c.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to instantiate websocket application " + c, e);
                    }
                }

                // Call methods in application class
                Set<ServerEndpointConfig> endpointConfigs = instance.getEndpointConfigs(app.programmaticEndpoints());
                Set<Class<?>> endpointClasses = instance.getAnnotatedEndpointClasses(app.annotatedEndpoints());

                // Register classes and configs
                endpointClasses.forEach(builder::register);
                endpointConfigs.forEach(builder::register);

                // Create routing builder
                routing = serverCdiExtension.routingBuilder(namedRouting, routingNameRequired, c.getName());
            } else {
                // Direct registration without calling application class
                app.annotatedEndpoints().forEach(builder::register);
                app.programmaticEndpoints().forEach(builder::register);
                app.extensions().forEach(builder::register);

                // Create routing builder
                routing = serverCdiExtension.serverRoutingBuilder();
            }

            // Finally register WebSockets in Helidon routing
            String rootPath = contextRoot.orElse(DEFAULT_WEBSOCKET_PATH);
            LOGGER.info("Registering websocket application at " + rootPath);
            routing.register(rootPath, new TyrusSupportMp(builder.build()));
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
