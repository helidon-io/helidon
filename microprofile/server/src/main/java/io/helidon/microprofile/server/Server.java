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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.server.spi.MpService;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.glassfish.jersey.server.ResourceConfig;
import org.jboss.weld.environment.se.Weld;

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
    SeContainer cdiContainer();

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
     * Get the port of the named socket.
     *
     * @param socketName name of the socket
     * @return port
     */
    int port(String socketName);

    /**
     * Builder to build {@link Server} instance.
     */
    final class Builder {

        static {
            // Load the initialization start time as early as possible from non-public code.
            ServerImpl.recordInitStart(System.nanoTime());
        }

        // there should only be one
        private static final AtomicInteger MP_SERVER_COUNTER = new AtomicInteger(1);
        private static final Logger STARTUP_LOGGER = Logger.getLogger("io.helidon.microprofile.startup.builder");

        private final List<Class<?>> resourceClasses = new LinkedList<>();
        private final List<Class<?>> providerClasses = new LinkedList<>();
        private final List<JaxRsApplication> applications = new LinkedList<>();
        private HelidonServiceLoader.Builder<MpService> extensionBuilder;
        private ResourceConfig resourceConfig;
        private SeContainer cdiContainer;
        private MpConfig config;
        private String host;
        private String basePath;
        private int port = -1;
        private boolean containerCreated;
        private Supplier<? extends ExecutorService> defaultExecutorService;
        private Context parentContext;
        private Context serverContext;
        private Boolean supportParallelRun;

        private Builder() {
            extensionBuilder = HelidonServiceLoader.builder(ServiceLoader.load(MpService.class));
        }

        private static ResourceConfig configForClasses(List<Class<?>> resourceClasses, List<Class<?>> providerClasses) {
            return ResourceConfig.forApplication(new Application() {
                @Override
                public Set<Class<?>> getClasses() {
                    HashSet<Class<?>> classes = new HashSet<>(resourceClasses);
                    classes.addAll(providerClasses);
                    return classes;
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
            if (null == parentContext) {
                serverContext = Context.builder()
                        .id("mp-" + MP_SERVER_COUNTER.getAndIncrement())
                        .build();
            } else {
                serverContext = Context.builder()
                        .parent(parentContext)
                        .id(parentContext.id() + ":mp-" + MP_SERVER_COUNTER.getAndIncrement())
                        .build();
            }

            // now run the build within context already
            return Contexts.runInContext(serverContext, this::doBuild);
        }

        private Server doBuild() {
            STARTUP_LOGGER.entering(Builder.class.getName(), "build");

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            if (null == config) {
                config = (MpConfig) ConfigProviderResolver.instance().getConfig(classLoader);
            } else {
                ConfigProviderResolver.instance().registerConfig(config, classLoader);
            }

            if (null == defaultExecutorService) {
                defaultExecutorService = ServerThreadPoolSupplier.builder()
                                                                 .name("server")
                                                                 .config(config.helidonConfig().get("server.executor-service"))
                                                                 .build();
            }

            STARTUP_LOGGER.finest("Configuration obtained");

            if (null == cdiContainer) {
                cdiContainer = currentContainer()
                        .orElseGet(() -> {
                            containerCreated = true;
                            return createContainer(classLoader);
                        });
            }

            STARTUP_LOGGER.finest("CDI Container obtained");

            if (applications.isEmpty()) {
                // no explicit or discovered applications
                if (!resourceClasses.isEmpty()) {
                    resourceConfig = configForClasses(resourceClasses, providerClasses);
                }
                if (null == resourceConfig) {
                    addResourcesFromContainer();
                }
                if (null != resourceConfig) {
                    applications.add(JaxRsApplication.create(resourceConfig));
                }
            } else if (!resourceClasses.isEmpty()) {
                applications.add(JaxRsApplication.create(configForClasses(resourceClasses, providerClasses)));
            }

            STARTUP_LOGGER.finest("Jersey resource configuration");

            if (null == host) {
                host = config.getOptionalValue("server.host", String.class).orElse("0.0.0.0");
            }

            if (port == -1) {
                port = config.getOptionalValue("server.port", Integer.class).orElse(7001);
            }

            if (null == supportParallelRun) {
                supportParallelRun = config.getOptionalValue("server.support-parallel", Boolean.class).orElse(false);
            }

            return new ServerImpl(this);
        }

        private void addResourcesFromContainer() {
            ServerCdiExtension extension = cdiContainer.getBeanManager().getExtension(ServerCdiExtension.class);
            if (null == extension) {
                throw new RuntimeException("Failed to find JAX-RS resource to use, extension not registered with container");
            }

            List<Class<? extends Application>> applications = extension.getApplications();

            if (applications.isEmpty()) {
                resourceClasses.addAll(extension.getResourceClasses());
                providerClasses.addAll(extension.getProviderClasses());
                resourceConfig = configForClasses(resourceClasses, providerClasses);
            } else {
                applications.forEach(this::addApplication);
            }
        }

        private Optional<SeContainer> currentContainer() {
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

        private SeContainer createContainer(ClassLoader classLoader) {
            // not in CDI
            Weld initializer = new Weld();
            initializer.addBeanDefiningAnnotations(Path.class);
            initializer.setClassLoader(classLoader);
            Map<String, Object> props = new HashMap<>(config.helidonConfig()
                                                              .get("cdi")
                                                              .detach()
                                                              .asMap()
                                                              .orElse(CollectionsHelper.mapOf()));
            initializer.setProperties(props);

            // add resource classes explicitly configured without CDI annotations
            this.resourceClasses.stream()
                    .filter(this::notACdiBean)
                    .forEach(initializer::addBeanClass);
            // add provider classes explicitly configured without CDI annotations
            this.providerClasses.stream()
                    .filter(this::notACdiBean)
                    .forEach(initializer::addBeanClass);

            STARTUP_LOGGER.finest("Initializer");
            SeContainer container = initializer.initialize();
            STARTUP_LOGGER.finest("Initalizer.initialize()");
            return container;
        }

        private boolean notACdiBean(Class<?> clazz) {
            if (clazz.getAnnotation(RequestScoped.class) != null) {
                // CDI bean
                return false;
            }

            if (clazz.getAnnotation(ApplicationScoped.class) != null) {
                //CDI bean
                return false;
            }

            if (clazz.getAnnotation(Dependent.class) != null) {
                //CDI bean
                return false;
            }

            return true;
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
         * Configure the extension builder.
         * This allows a fully customized handling of {@link io.helidon.microprofile.server.spi.MpService} instances
         * to be used by the created {@link io.helidon.microprofile.server.Server}.
         *
         * @param loaderBuilder builder of server extensions
         * @return updated builder instance
         * @see io.helidon.common.serviceloader.HelidonServiceLoader.Builder#useSystemServiceLoader(boolean)
         */
        public Builder extensionsService(HelidonServiceLoader.Builder<MpService> loaderBuilder) {
            this.extensionBuilder = loaderBuilder;
            return this;
        }

        /**
         * Add an extension to the list of extensions.
         * All {@link io.helidon.microprofile.server.spi.MpService} configured for Java Service loader are loaded
         * automatically.
         * This serves as a possibility to add a service that is not loaded through a service loader.
         * <p>
         * To have a fully customized list of extensions, use
         * {@link #extensionsService(io.helidon.common.serviceloader.HelidonServiceLoader.Builder)}.
         *
         * @param service service implementation
         * @return updated builder instance
         */
        public Builder addExtension(MpService service) {
            extensionBuilder.addService(service);
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
         * Order is (e.g. if application is defined, resource and provider classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource and provider classes</li>
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
         * Order is (e.g. if application is defined, resource and provider classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource and provider classes</li>
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
         * Order is (e.g. if application is defined, resource and provider classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource and provider classes</li>
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
         * Order is (e.g. if application is defined, resource and provider classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource and provider classes</li>
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
         * Order is (e.g. if application is defined, resource and provider classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource and provider classes</li>
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

        /**
         * Add a JAX-RS provider class to use.
         * <p>
         * Order is (e.g. if application is defined, resource and provider classes are ignored):
         * <ul>
         * <li>Applications and application classes</li>
         * <li>Resource and provider classes</li>
         * <li>Resource config</li>
         * </ul>
         *
         * @param provider provider class to add, list of these classes is used to bootstrap Jersey
         * @return modified builder
         */
        public Builder addProviderClass(Class<?> provider) {
            this.providerClasses.add(provider);
            return this;
        }

        /**
         * Configure the parent context to be used by this server.
         *
         * @param parentContext context to serve as a parent
         * @return updated builder instance
         */
        public Builder context(Context parentContext) {
            this.parentContext = parentContext;
            return this;
        }

        /**
         * Enabled (or disable) support for more than one MP Server running in parallel.
         * By default this is not supported, as a single JVM shares class loader and CDI, so running
         * more than one server in a single JVM can lead to unexpected behavior.
         *
         * @param supportParallelRun set to {@code true} to start more than one {@link io.helidon.microprofile.server.Server}
         *                           in the same JVM
         * @return updated builder instance
         */
        public Builder supportParallel(boolean supportParallelRun) {
            this.supportParallelRun = supportParallelRun;
            return this;
        }

        List<JaxRsApplication> applications() {
            return new LinkedList<>(applications);
        }

        SeContainer cdiContainer() {
            return cdiContainer;
        }

        Config config() {
            return config;
        }

        String host() {
            return host;
        }

        int port() {
            return port;
        }

        List<MpService> extensions() {
            return extensionBuilder.build().asList();
        }

        boolean containerCreated() {
            return containerCreated;
        }

        ExecutorService defaultExecutorService() {
            return defaultExecutorService.get();
        }

        String basePath() {
            return basePath;
        }

        Context context() {
            return serverContext;
        }

        boolean supportParallelRun() {
            return supportParallelRun;
        }
    }
}
