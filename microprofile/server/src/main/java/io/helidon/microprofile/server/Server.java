/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.core.Application;

import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.context.Contexts;
import io.helidon.config.mp.MpConfig;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.cdi.HelidonContainer;

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
    String host();

    /**
     * Get the port this server listens on or {@code -1} if the server is not
     * running.
     *
     * @return port
     */
    int port();

    /**
     * Builder to build {@link Server} instance.
     */
    final class Builder {
        private static final Logger STARTUP_LOGGER = Logger.getLogger("io.helidon.microprofile.startup.builder");

        private final List<Class<?>> resourceClasses = new LinkedList<>();
        private final List<JaxRsApplication> applications = new LinkedList<>();
        private Config config;
        private String host;
        private String basePath;
        private int port = -1;
        private Supplier<? extends ExecutorService> defaultExecutorService;
        private JaxRsCdiExtension jaxRs;
        private boolean retainDiscovered = false;

        private Builder() {
            ServerCdiExtension server = null;
            try {
                server = CDI.current()
                        .getBeanManager()
                        .getExtension(ServerCdiExtension.class);
            } catch (IllegalStateException ignored) {
                // CDI is not started, so we should be fine
            }

            if (null != server) {
                if (server.started()) {
                    throw new IllegalStateException("Server is already started. Maybe you have initialized CDI yourself? "
                                                            + "If you do so, then you cannot use Server.builder() to set up "
                                                            + "your server. "
                                                            + "Config is initialized with defaults or using "
                                                            + "meta-config.yaml; applications are discovered using CDI. "
                                                            + "To use custom configuration, you can use "
                                                            + "ConfigProviderResolver.instance().registerConfig(config, "
                                                            + "classLoader);");
                }
            }
        }

        /**
         * Build a server based on this builder.
         *
         * @return Server instance to be started
         * @throws MpException in case the server fails to be created
         */
        public Server build() {
            STARTUP_LOGGER.entering(Builder.class.getName(), "build");

            // configuration must be initialized before we start the container
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            if (null == config) {
                this.config = ConfigProviderResolver.instance().getConfig(classLoader);
            }

            // make sure the config is available to application
            ConfigProviderResolver.instance().registerConfig(config, classLoader);

            // we may have shutdown the original instance, this is to ensure we use the current CDI.
            HelidonContainer instance = HelidonContainer.instance();

            try {
                // now run the build within context already
                return Contexts.runInContext(instance.context(), this::doBuild);
            } catch (Exception e) {
                try {
                    ((SeContainer) CDI.current()).close();
                } catch (IllegalStateException ignored) {
                    // no cdi registered
                }

                throw e;
            }
        }

        private Server doBuild() {
            ServerCdiExtension server = CDI.current()
                    .getBeanManager()
                    .getExtension(ServerCdiExtension.class);

            if (null == defaultExecutorService) {
                defaultExecutorService = ServerThreadPoolSupplier.builder()
                        .name("server")
                        .config(MpConfig.toHelidonConfig(config)
                                        .get("server.executor-service"))
                        .build();
            }

            server.defaultExecutorService(defaultExecutorService);
            if (null != basePath) {
                server.basePath(basePath);
            }

            STARTUP_LOGGER.finest("Configuration obtained");

            // explicit application configuration
            // if there are any resource classes explicitly added, create an application for them
            this.jaxRs = CDI.current()
                    .getBeanManager()
                    .getExtension(JaxRsCdiExtension.class);

            if (!applications.isEmpty() || !resourceClasses.isEmpty()) {
                // only use applications from container if none explicitly configured
                if (!retainDiscovered) {
                    jaxRs.removeApplications();
                }
            }

            if (!resourceClasses.isEmpty()) {
                jaxRs.removeResourceClasses();

                // if resource classes are configured, use them as defaults
                jaxRs.addResourceClasses(resourceClasses);
            }

            // add all explicit applications
            jaxRs.addApplications(applications);

            // and add a synthetic app (last)
            if (!resourceClasses.isEmpty()) {
                jaxRs.addSyntheticApplication(resourceClasses);
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

        /**
         * Configure a path to which the server would redirect when a root path is requested.
         * E.g. when static content is available at "/static" and you want to start there on index.html,
         * you may want to configure this to "/static/index.html".
         * When user requests "http://host:port" or "http://host:port/", the user would be redirected to
         * "http://host:port/static/index.html"
         *
         * @param basePath path to redirect user from root path
         * @return updated builder instance
         */
        public Builder basePath(String basePath) {
            this.basePath = basePath;
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
        public Builder defaultExecutorServiceSupplier(Supplier<? extends ExecutorService> supplier) {
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
            this.config = ConfigProviderResolver.instance()
                    .getBuilder()
                    .withSources(MpConfigSources.create(config))
                    .build();

            return this;
        }

        /**
         * Configuration instance to use to configure this server (Microprofile config).
         *
         * @param config configuration to use
         * @return modified builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * JAX-RS resource configuration to use. This will add the provided resource config
         * as an additional application.
         * <p>
         *
         * @param config configuration to bootstrap Jersey
         * @return modified builder
         */
        public Builder resourceConfig(ResourceConfig config) {
            Application application = config.getApplication();

            JaxRsApplication.Builder builder = JaxRsApplication.builder()
                    .appName(config.getApplicationName())
                    .config(config);

            if (null != application) {
                builder.applicationClass(application.getClass());
            }

            this.applications.add(builder.build());

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
         * If any application or resource class is added through this builder, applications discovered by CDI are ignored.
         * You can change this behavior by setting the retain discovered applications to {@code true}.
         *
         * <p>
         * If you use {@link Server.Builder} and explicitly add a JAX-RS application or JAX-RS resource class to it,
         * then classes discovered via CDI mechanisms will be ignored (e.g. all JAX-RS applications with bean defining
         * annotations and all JAX-RS resources in bean archives).
         * If you set the flag to true, we will retain both the "discovered" and "explicitly configured" applications and
         * resource classes.
         *
         * @param retain whether to keep applications discovered by CDI even if any are explicitly added
         * @return updated builder instance
         */
        public Builder retainDiscoveredApplications(boolean retain) {
            this.retainDiscovered = retain;
            return this;
        }

        /**
         * Replace existing applications with the ones provided.
         *
         * @param applications new applications to use with this server builder
         * @return updated builder instance
         */
        public Builder applications(Application... applications) {
            this.applications.clear();
            for (Application application : applications) {
                addApplication(application);
            }
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

        String host() {
            return host;
        }

        int port() {
            return port;
        }
    }
}
