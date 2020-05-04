/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.mp.MpConfig;
import io.helidon.config.mp.MpConfigProviderResolver;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.weld.AbstractCDI;
import org.jboss.weld.bean.builtin.BeanManagerProxy;
import org.jboss.weld.bootstrap.WeldBootstrap;
import org.jboss.weld.bootstrap.api.Environments;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.config.ConfigurationKey;
import org.jboss.weld.configuration.spi.ExternalConfiguration;
import org.jboss.weld.configuration.spi.helpers.ExternalConfigurationBuilder;
import org.jboss.weld.environment.deployment.WeldDeployment;
import org.jboss.weld.environment.deployment.WeldResourceLoader;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.environment.se.events.ContainerBeforeShutdown;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.jboss.weld.environment.se.events.ContainerShutdown;
import org.jboss.weld.environment.se.logging.WeldSELogger;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.resources.spi.ResourceLoader;

import static org.jboss.weld.config.ConfigurationKey.EXECUTOR_THREAD_POOL_TYPE;
import static org.jboss.weld.executor.ExecutorServicesFactory.ThreadPoolType.COMMON;

/**
 * Helidon CDI implementation.
 * This class inherits most of its functionality from {@link org.jboss.weld.environment.se.Weld}.
 * This is a needed extension to support ahead of time (AOT) compilation when
 * using GraalVM native-image.
 * It separates the {@link #init()} sequence from the {@link #start()} sequence.
 * <p>Initialization should happen statically and is part of the compiled native image.
 * Start happens at runtime with current configuration.
 * <p>When running in JIT mode (or on any regular JDK), this works as if Weld is used directly.
 * <p>Important note - you need to explicitly use this class. Using {@link javax.enterprise.inject.se.SeContainerInitializer} will
 * boot Weld.
 */
final class HelidonContainerImpl extends Weld implements HelidonContainer {
    private static final Logger LOGGER = Logger.getLogger(HelidonContainerImpl.class.getName());
    private static final AtomicBoolean IN_RUNTIME = new AtomicBoolean();
    private static final String EXIT_ON_STARTED_KEY = "exit.on.started";
    private static final boolean EXIT_ON_STARTED = "!".equals(System.getProperty(EXIT_ON_STARTED_KEY));
    private static final Context ROOT_CONTEXT;

    static {
        HelidonFeatures.flavor(HelidonFlavor.MP);
        HelidonFeatures.register(HelidonFlavor.MP, "CDI");

        Context.Builder contextBuilder = Context.builder()
                .id("helidon-cdi");

        Contexts.context()
                .ifPresent(contextBuilder::parent);

        ROOT_CONTEXT = contextBuilder.build();

        CDI.setCDIProvider(new HelidonCdiProvider());
    }

    private final WeldBootstrap bootstrap;
    private final String id;
    private HelidonCdi cdi;

    HelidonContainerImpl() {
        this.bootstrap = new WeldBootstrap();
        id = UUID.randomUUID().toString();
    }

    /**
     * Creates and initializes the CDI container.
     * @return a new initialized CDI container
     */
    static HelidonContainerImpl create() {

        HelidonContainerImpl container = new HelidonContainerImpl();

        container.initInContext();

        return container;
    }

    void initInContext() {
        long time = System.nanoTime();

        Contexts.runInContext(ROOT_CONTEXT, this::init);

        time = System.nanoTime() - time;
        long t = TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS);
        LOGGER.fine("Container initialized in " + t + " millis");
    }

    @SuppressWarnings("unchecked")
    private HelidonContainerImpl init() {
        LOGGER.fine(() -> "Initializing CDI container " + id);

        addHelidonBeanDefiningAnnotations("javax.ws.rs.Path", "javax.websocket.server.ServerEndpoint");

        ResourceLoader resourceLoader = new WeldResourceLoader() {
            @Override
            public Collection<URL> getResources(String name) {
                Collection<URL> resources = super.getResources(name);
                return new HashSet<>(resources);    // drops duplicates when using patch-module
            }
        };
        setResourceLoader(resourceLoader);

        Config mpConfig = ConfigProvider.getConfig();
        io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);

        Map<String, String> properties = config.get("cdi")
                .detach()
                .asMap()
                .orElseGet(Map::of);

        setProperties(new HashMap<>(properties));

        ServiceLoader.load(Extension.class).findFirst().ifPresent(it -> {
            // adding an empty extension to start even with just extensions on classpath
            // Weld would fail (as it sets the extensions after checking if they are empty)
            addExtension(new Extension() { });
        });

        Deployment deployment = createDeployment(resourceLoader, bootstrap);

        ExternalConfigurationBuilder configurationBuilder = new ExternalConfigurationBuilder()
                // weld-se uses CommonForkJoinPoolExecutorServices by default
                .add(EXECUTOR_THREAD_POOL_TYPE.get(), COMMON.toString())
                // weld-se uses relaxed construction by default
                .add(ConfigurationKey.RELAXED_CONSTRUCTION.get(), true)
                // allow optimized cleanup by default
                .add(ConfigurationKey.ALLOW_OPTIMIZED_CLEANUP.get(), isEnabled(ALLOW_OPTIMIZED_CLEANUP, true));
        for (Map.Entry<String, String> property : properties.entrySet()) {
            String key = property.getKey();
            if (SHUTDOWN_HOOK_SYSTEM_PROPERTY.equals(key) || ARCHIVE_ISOLATION_SYSTEM_PROPERTY
                    .equals(key) || DEV_MODE_SYSTEM_PROPERTY.equals(key)
                    || SCAN_CLASSPATH_ENTRIES_SYSTEM_PROPERTY.equals(key) || JAVAX_ENTERPRISE_INJECT_SCAN_IMPLICIT.equals(key)) {
                continue;
            }
            configurationBuilder.add(key, property.getValue());
        }
        deployment.getServices().add(ExternalConfiguration.class, configurationBuilder.build());

        bootstrap.startContainer(id, Environments.SE, deployment);

        bootstrap.startInitialization();

        Collection<BeanDeploymentArchive> archives = deployment.getBeanDeploymentArchives();
        if (archives.isEmpty()) {
            throw new IllegalStateException("No deployment archive");
        }
        BeanManagerImpl beanManager = bootstrap.getManager(archives.iterator().next());

        beanManager.getEvent().select(BuildTimeStart.Literal.INSTANCE).fire(id);

        bootstrap.deployBeans();

        cdi = new HelidonCdi(id, bootstrap, deployment);
        HelidonCdiProvider.setCdi(cdi);

        beanManager.getEvent().select(BuildTimeEnd.Literal.INSTANCE).fire(id);

        return this;
    }

    @SuppressWarnings("unchecked")
    private void addHelidonBeanDefiningAnnotations(String... classNames) {
        // I have to do this using reflection since annotation may not be in classpath
        for (String className : classNames) {
            try {
                Class<? extends Annotation> clazz = (Class<? extends Annotation>) Class.forName(className);
                addBeanDefiningAnnotations(clazz);
            } catch (Throwable e) {
                LOGGER.log(Level.FINEST, e, () -> className + " is not in the classpath, it will be ignored by CDI");
            }
        }
    }

    /**
     * Start this container.
     * @return
     */
    @Override
    public SeContainer start() {
        if (IN_RUNTIME.get()) {
            // already started
            return cdi;
        }
        LogConfig.configureRuntime();
        Contexts.runInContext(ROOT_CONTEXT, this::doStart);
        if (EXIT_ON_STARTED) {
            exitOnStarted();
        }
        return cdi;
    }

    @Override
    public Context context() {
        return ROOT_CONTEXT;
    }

    @Override
    public void shutdown() {
        cdi.close();
    }

    private HelidonContainerImpl doStart() {
        long now = System.currentTimeMillis();

        IN_RUNTIME.set(true);

        BeanManager bm = null;
        try {
            bm = CDI.current().getBeanManager();
        } catch (IllegalStateException e) {
            LOGGER.log(Level.FINEST, "Cannot get current CDI, probably restarted", e);
            // cannot access CDI - CDI is not yet initialized (probably shut down and started again)
            initInContext();
            bm = CDI.current().getBeanManager();
        }

        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();

        MpConfigProviderResolver.runtimeStart(config);

        bm.getEvent().select(RuntimeStart.Literal.INSTANCE).fire(config);

        bootstrap.validateBeans();
        bootstrap.endInitialization();

        // adding a shutdown hook
        // we need to workaround that logging stops printing output during shutdown hooks
        // let's add a Handler
        // this is to workaround https://bugs.openjdk.java.net/browse/JDK-8161253

        Thread shutdownHook = new Thread(() -> {
            cdi.close();
        }, "helidon-cdi-shutdown-hook");

        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        Handler[] newHandlers = new Handler[handlers.length + 1];
        newHandlers[0] = new Handler() {
            @Override
            public void publish(LogRecord record) {
                // noop
            }

            @Override
            public void flush() {
                // noop
            }

            @Override
            public void close() throws SecurityException {
                try {
                    shutdownHook.join();
                } catch (InterruptedException ignored) {
                }
            }
        };
        System.arraycopy(handlers, 0, newHandlers, 1, handlers.length);
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }
        for (Handler newHandler : newHandlers) {
            rootLogger.addHandler(newHandler);
        }

        bm.getEvent().select(Initialized.Literal.APPLICATION).fire(new ContainerInitialized(id));

        now = System.currentTimeMillis() - now;
        LOGGER.fine("Container started in " + now + " millis (this excludes the initialization time)");

        HelidonFeatures.print(HelidonFlavor.MP,
                              config.getOptionalValue("features.print-details", Boolean.class).orElse(false));

        // shutdown hook should be added after all initialization is done, otherwise a race condition may happen
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        return this;
    }

    private void exitOnStarted() {
        LOGGER.info(String.format("Exiting, -D%s set.", EXIT_ON_STARTED_KEY));
        System.exit(0);
    }

    /**
     * Are we in runtime or build time.
     * @return {@code true} if this is runtime, {@code false} if this is build time
     */
    public static boolean isRuntime() {
        return IN_RUNTIME.get();
    }

    private static final class HelidonCdi extends AbstractCDI<Object> implements SeContainer {
        private final AtomicBoolean isRunning = new AtomicBoolean(true);
        private final String id;
        private final WeldBootstrap bootstrap;
        private final Deployment deployment;

        private HelidonCdi(String id, WeldBootstrap bootstrap, Deployment deployment) {
            this.id = id;
            this.bootstrap = bootstrap;
            this.deployment = deployment;
        }

        @Override
        public void close() {
            if (isRunning.compareAndSet(true, false)) {
                try {
                    beanManager().getEvent().select(BeforeDestroyed.Literal.APPLICATION).fire(new ContainerBeforeShutdown(id));
                } finally {
                    // Destroy all the dependent beans correctly
                    try {
                        beanManager().getEvent().select(Destroyed.Literal.APPLICATION).fire(new ContainerShutdown(id));
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, e, () -> "Failed to fire ApplicationScoped Destroyed event");
                    }
                    bootstrap.shutdown();
                    WeldSELogger.LOG.weldContainerShutdown(id);
                }
            }
            IN_RUNTIME.set(false);
            // need to reset - if somebody decides to restart CDI (such as a test)
            ContainerInstanceHolder.reset();
        }

        @Override
        public boolean isRunning() {
            return isRunning.get();
        }

        @Override
        public BeanManager getBeanManager() {
            if (isRunning.get()) {
                return new BeanManagerProxy(beanManager());
            }
            throw new IllegalStateException("Container not running");
        }

        private BeanManagerImpl beanManager() {
            return BeanManagerProxy.unwrap(bootstrap.getManager(getArchive(deployment)));
        }

        private BeanDeploymentArchive getArchive(Deployment deployment) {
            Collection<BeanDeploymentArchive> beanDeploymentArchives = deployment.getBeanDeploymentArchives();
            if (beanDeploymentArchives.size() == 1) {
                // Only one bean archive or isolation is disabled
                return beanDeploymentArchives.iterator().next();
            }
            for (BeanDeploymentArchive beanDeploymentArchive : beanDeploymentArchives) {
                if (WeldDeployment.SYNTHETIC_BDA_ID.equals(beanDeploymentArchive.getId())) {
                    // Synthetic bean archive takes precedence
                    return beanDeploymentArchive;
                }
            }
            for (BeanDeploymentArchive beanDeploymentArchive : beanDeploymentArchives) {
                if (!WeldDeployment.ADDITIONAL_BDA_ID.equals(beanDeploymentArchive.getId())) {
                    // Get the first non-additional bean deployment archive
                    return beanDeploymentArchive;
                }
            }
            return deployment.loadBeanDeploymentArchive(WeldContainer.class);
        }
    }
}
