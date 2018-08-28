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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.core.Application;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.spi.MpService;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Microprofile server.
 */
public interface Server {
    /**
     * Create a server instance for a JAX-RS application.
     *
     * @param applications application(s) to use
     * @return Server instance to be started
     * @throws MpException in case the server fails to be created
     * @see #builder()
     */
    @SafeVarargs
    static Server create(Application... applications) throws MpException {
        Builder builder = builder();
        Arrays.stream(applications).forEach(builder::addApplication);
        return builder.build();
    }

    /**
     * Create a server instance for a JAX-RS application class.
     *
     * @param applicationClasses application class(es) to use
     * @return Server instance to be started
     * @throws MpException in case the server fails to be created
     * @see #builder()
     */
    @SafeVarargs
    static Server create(Class<? extends Application>... applicationClasses) throws MpException {
        Builder builder = builder();
        Arrays.stream(applicationClasses).forEach(builder::addApplication);
        return builder.build();
    }

    /**
     * Create a server instance for discovered JAX-RS application (through CDI).
     *
     * @return Server instance to be started
     * @throws MpException in case the server fails to be created
     * @see #builder()
     */
    static Server create() throws MpException {
        return builder().build();
    }

    /**
     * Builder to customize Server instance.
     *
     * @return builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Get CDI container in use.
     *
     * @return CDI container instance (standard edition)
     */
    SeContainer getContainer();

    /**
     * Start this server (can only be used once).
     * This is a blocking call.
     *
     * @return Server instance, started
     * @throws MpException in case the server fails to start
     */
    Server start() throws MpException;

    /**
     * Stop this server immediately (can only be used on a started server).
     * This is a blocking call.
     *
     * @return Server instance, stopped
     * @throws MpException in case the server fails to stop
     */
    Server stop() throws MpException;

    /**
     * Get the host this server listens on.
     *
     * @return host name
     */
    String getHost();

    /**
     * Get the port this server listens on or {@code -1} if the server is not
     * running.
     *
     * @return port
     */
    int getPort();

    /**
     * Builder to build {@link Server} instance.
     */
    final class Builder {
        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());
        private static final Logger STARTUP_LOGGER = Logger.getLogger("io.helidon.microprofile.startup.builder");

        private final List<Class<?>> resourceClasses = new LinkedList<>();
        private final List<MpService> extensions = new LinkedList<>();
        private final List<JaxRsApplication> applications = new LinkedList<>();
        private ResourceConfig resourceConfig;
        private SeContainer cdiContainer;
        private MpConfig config;
        private String host;
        private int port = -1;
        private boolean containerCreated;
        private Supplier<? extends ExecutorService> defaultExecutorService;

        private Builder() {
        }

        private static ResourceConfig configForResourceClasses(List<Class<?>> resourceClasses) {
            return ResourceConfig.forApplication(new Application() {
                @Override
                public Set<Class<?>> getClasses() {
                    return new HashSet<>(resourceClasses);
                }
            });
        }

        /**
         * Build a server based on this builder.
         *
         * @return Server instance to be started
         * @throws MpException in case the server fails to be created
         */
        public Server build() {
            STARTUP_LOGGER.entering(Builder.class.getName(), "build");

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            if (null == config) {
                config = (MpConfig) ConfigProviderResolver.instance().getConfig(classLoader);
            } else {
                ConfigProviderResolver.instance().registerConfig(config, classLoader);
            }

            if (null == defaultExecutorService) {
                defaultExecutorService = ThreadPoolSupplier.from(config.getConfig());
            }

            STARTUP_LOGGER.finest("Configuration obtained");

            if (null == cdiContainer) {
                cdiContainer = getCurrentContainter()
                        .orElseGet(() -> {
                            containerCreated = true;
                            return createContainter(classLoader);
                        });
            }

            STARTUP_LOGGER.finest("CDI Container obtained");

            if (applications.isEmpty()) {
                if (!resourceClasses.isEmpty()) {
                    resourceConfig = configForResourceClasses(resourceClasses);
                }
                if (null == resourceConfig) {
                    resourcesFromContainer();
                }

                if (null != resourceConfig) {
                    applications.add(JaxRsApplication.create(resourceConfig));
                }
            }

            STARTUP_LOGGER.finest("Jersey resource configuration");

            if (null == host) {
                host = config.getOptionalValue("server.host", String.class).orElse("0.0.0.0");
            }

            if (port == -1) {
                port = config.getOptionalValue("server.port", Integer.class).orElse(7001);
            }

            return new ServerImpl(this);
        }

        private void resourcesFromContainer() {
            ServerCdiExtension extension = cdiContainer.getBeanManager().getExtension(ServerCdiExtension.class);
            if (null == extension) {
                throw new RuntimeException("Failed to find JAX-RS resource to use, extension not registered with container");
            }

            List<Class<? extends Application>> applications = extension.getApplications();

            if (applications.isEmpty()) {
                List<Class<?>> resourceClasses = extension.getResourceClasses();
                if (resourceClasses.isEmpty()) {
                    LOGGER.warning("Failed to find JAX-RS resource to use");
                }
                resourceConfig = configForResourceClasses(resourceClasses);
            } else {
                applications.forEach(this::addApplication);
            }
        }

        private Optional<SeContainer> getCurrentContainter() {
            try {
                CDI<Object> current = CDI.current();
                STARTUP_LOGGER.finest("CDI.current()");
                if (null == current) {
                    return Optional.empty();
                }
                if (current instanceof SeContainer) {
                    SeContainer currentSe = (SeContainer) current;
                    if (currentSe.isRunning()) {
                        return Optional.of(currentSe);
                    } else {
                        return Optional.empty();
                    }
                } else {
                    throw new MpException("Running in a non-SE CDI Container: " + current.getClass().getName());
                }
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }

        private SeContainer createContainter(ClassLoader classLoader) {
            // not in CDI
            SeContainerInitializer initializer = SeContainerInitializer.newInstance();
            initializer.setClassLoader(classLoader);
            Map<String, Object> props = new HashMap<>(config.getConfig()
                                                              .get("cdi")
                                                              .detach()
                                                              .asOptionalMap()
                                                              .orElse(CollectionsHelper.mapOf()));
            initializer.setProperties(props);
            STARTUP_LOGGER.finest("Initializer");
            SeContainer container = initializer.initialize();
            STARTUP_LOGGER.finest("Initalizer.initialize()");
            return container;
        }

        /**
         * Configure listen host.
         *
         * @param host hostname
         * @return modified builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder addExtension(MpService service) {
            extensions.add(service);
            return this;
        }

        /**
         * Set a supplier of an executor service to use for tasks connected with application
         * processing (JAX-RS).
         *
         * @param supplier executor service supplier, only called when an application is configured without its own executor
         *                 service
         * @return updated builder instance
         */
        public Builder setDefaultExecutorServiceSupplier(Supplier<? extends ExecutorService> supplier) {
            this.defaultExecutorService = supplier;
            return this;
        }

        /**
         * Configure listen port.
         *
         * @param port port
         * @return modified builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Configuration instance to use to configure this server (Helidon config).
         *
         * @param config configuration to use
         * @return modified builder
         */
        public Builder config(io.helidon.config.Config config) {
            this.config = (MpConfig) MpConfig.builder().config(config).build();
            return this;
        }

        /**
         * Configuration instance to use to configure this server (Microprofile config).
         *
         * @param config configuration to use
         * @return modified builder
         */
        public Builder config(Config config) {
            this.config = (MpConfig) config;
            return this;
        }

        /**
         * Configure CDI container to use.
         * Use this method if you need to manually configure the CDI container.
         * Also understand, that whatever happens during container initialization is already done and cannot be undone, such
         * as when microprofile configuration is used. If you use this method and then set explicit config using
         * {@link #config(Config)}, you may end up with some classes configured from default MP config.
         *
         * @param cdiContainer container to use, currently this requires Weld, as Jersey CDI integration depends on it;
         *                     not other CDI provider is tested
         * @return modified builder
         */
        public Builder cdiContainer(SeContainer cdiContainer) {
            this.cdiContainer = cdiContainer;
            return this;
        }

        /**
         * JAX-RS resource configuration to use.
         * <p>
         * Order is (e.g. if application is defined, resource classes are ignored):
         * <ul>
         * <li>All Applications and Application classes</li>
         * <li>Resource classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param config configuration to bootstrap Jersey
         * @return modified builder
         */
        public Builder resourceConfig(ResourceConfig config) {
            this.resourceConfig = config;
            return this;
        }

        /**
         * Add a JAX-RS application with all possible options to this server.
         *
         * @param application application to add
         * @return updated builder instance
         */
        public Builder addApplication(JaxRsApplication application) {
            this.applications.add(application);
            return this;
        }

        /**
         * JAX-RS application to use. If more than one application is added, they must be registered
         * on different {@link javax.ws.rs.ApplicationPath}.
         * Also you must make sure that paths do not overlap, as that may cause unexpected results (e.g.
         * registering one application under root ("/") and another under "/app1" would not work as expected).
         *
         * <p>
         * Order is (e.g. if application is defined, resource classes are ignored):
         * <ul>
         * <li>All Applications and Application classes</li>
         * <li>Resource classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param application application to bootstrap Jersey
         * @return modified builder
         */
        public Builder addApplication(Application application) {
            this.applications.add(JaxRsApplication.create(application));
            return this;
        }

        /**
         * JAX-RS application to use. If more than one application is added, they must be registered
         * on different {@link javax.ws.rs.ApplicationPath}.
         * Also you must make sure that paths do not overlap, as that may cause unexpected results (e.g.
         * registering one application under root ("/") and another under "/app1" would not work as expected).
         *
         * <p>
         * Order is (e.g. if application is defined, resource classes are ignored):
         * <ul>
         * <li>All Applications and Application classes</li>
         * <li>Resource classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param contextRoot context root this application will be available under
         * @param application application to bootstrap Jersey
         * @return modified builder
         */
        public Builder addApplication(String contextRoot, Application application) {
            this.applications.add(JaxRsApplication.builder()
                                          .application(application)
                                          .contextRoot(contextRoot)
                                          .build());
            return this;
        }

        /**
         * JAX-RS application class to use.
         * <p>
         * Order is (e.g. if application is defined, resource classes are ignored):
         * <ul>
         * <li>Application class</li>
         * <li>Application</li>
         * <li>Resource classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param applicationClass application class to bootstrap Jersey
         * @return modified builder
         */
        public Builder addApplication(Class<? extends Application> applicationClass) {
            this.applications.add(JaxRsApplication.create(applicationClass));
            return this;
        }

        /**
         * JAX-RS application class to use.
         * <p>
         * Order is (e.g. if application is defined, resource classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param contextRoot      context root to serve this application under
         * @param applicationClass application class to bootstrap Jersey
         * @return modified builder
         */
        public Builder addApplication(String contextRoot, Class<? extends Application> applicationClass) {
            this.applications.add(JaxRsApplication.builder()
                                          .application(applicationClass)
                                          .contextRoot(contextRoot)
                                          .build());
            return this;
        }

        /**
         * Add a JAX-RS resource class to use.
         * <p>
         * Order is (e.g. if application is defined, resource classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param resource resource class to add, list of these classes is used to bootstrap Jersey
         * @return modified builder
         */
        public Builder addResourceClass(Class<?> resource) {
            this.resourceClasses.add(resource);
            return this;
        }

        public List<JaxRsApplication> getApplications() {
            return new LinkedList<>(applications);
        }

        SeContainer getCdiContainer() {
            return cdiContainer;
        }

        Config getConfig() {
            return config;
        }

        String getHost() {
            return host;
        }

        int getPort() {
            return port;
        }

        List<MpService> getExtensions() {
            return extensions;
        }

        boolean getContainerCreated() {
            return containerCreated;
        }

        ExecutorService getDefaultExecutorService() {
            return defaultExecutorService.get();
        }
    }
}
