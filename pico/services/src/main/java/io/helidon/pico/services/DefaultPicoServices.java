/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.services;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.Application;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.EventReceiver;
import io.helidon.pico.Injector;
import io.helidon.pico.Metrics;
import io.helidon.pico.Module;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.Resetable;

/**
 * The default implementation for {@link io.helidon.pico.PicoServices}.
 */
class DefaultPicoServices implements PicoServices, Resetable {
    static final System.Logger LOGGER = System.getLogger(DefaultPicoServices.class.getName());

    private final AtomicBoolean initializingServices = new AtomicBoolean();
    private final AtomicBoolean isBinding = new AtomicBoolean();
    private final AtomicReference<DefaultServices> services = new AtomicReference<>();
    private final AtomicReference<List<Module>> moduleList = new AtomicReference<>();
    private final AtomicReference<List<Application>> applicationList = new AtomicReference<>();
    //    private final AtomicReference<DefaultInjector> injector = new AtomicReference<>();
    private final Bootstrap bootstrap;
    private final PicoServicesConfig cfg;
    private final boolean isGlobal;
    private final DefaultActivationLog log;
    private CountDownLatch initializedServices = new CountDownLatch(1);
    private Thread initializingThread;


    /**
     * Constructor taking the bootstrap.
     *
     * @param bootstrap the bootstrap configuration
     * @param global    flag indicating whether this is the global con
     */
    DefaultPicoServices(Bootstrap bootstrap,
                        boolean global) {
        this(bootstrap, BasicPicoServicesConfig.create(), global);
    }

    /**
     * Constructor taking a configuration.
     *
     * @param bootstrap the bootstrap
     * @param cfg       the config
     * @param global    flag indicating if this is the global singleton
     */
    DefaultPicoServices(Bootstrap bootstrap,
                                  PicoServicesConfig cfg,
                                  boolean global) {
        this.bootstrap = Objects.requireNonNull(bootstrap);
        this.cfg = Objects.requireNonNull(cfg);
        this.isGlobal = global;
        this.log = cfg.activationLogs()
                ? DefaultActivationLog.createRetainedLog(LOGGER)
                : DefaultActivationLog.createUnretainedLog(LOGGER);
        this.services.set(new DefaultServices(cfg));
        this.initializingThread = Thread.currentThread();
    }

    @Override
    public Bootstrap bootstrap() {
        return bootstrap;
    }

    @Override
    public Optional<PicoServicesConfig> config() {
        return Optional.of(cfg);
    }

    @Override
    public Optional<ActivationLog> activationLog() {
        return Optional.of(log);
    }

    @Override
    public Optional<Metrics> metrics() {
        return Optional.of(services.get().metrics());
    }

    @Override
    public Services services() {
        if (!initializingServices.getAndSet(true)) {
            try {
                initializingThread = Thread.currentThread();
                initializeServices();
                initializedServices.countDown();
            } catch (Throwable t) {
                if (t instanceof PicoException) {
                    throw (PicoException) t;
                } else {
                    throw new PicoException("Failed to initialize: " + t.getMessage(), t);
                }
            }
        }

        return services.get();
    }

    @Override
    public Optional<ServiceBinder> createServiceBinder(Module module) {
        return Optional.empty();
    }

    @Override
    public Optional<Injector> injector() {
        // TODO:
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, ActivationResult<?>>> shutdown() {
        // TODO:
        return Optional.empty();
    }

    @Override
    public boolean reset(boolean deep) {
        synchronized (services) {
            services.get().reset(deep);
            services.set(null);
            log.reset(deep);

            synchronized (moduleList) {
                isBinding.set(false);
                initializedServices = new CountDownLatch(1);
                initializingServices.set(false);
                moduleList.set(null);
                applicationList.set(null);
            }
        }

        return true;
    }

    //    @Override
    //    public Optional<? extends Injector> getInjector() {
    //        if (Objects.nonNull(injector.get())) {
    //            return Optional.of(injector.get());
    //        }
    //
    //        synchronized (injector) {
    //            if (Objects.nonNull(injector.get())) {
    //                return Optional.of(injector.get());
    //            }
    //
    //            io.helidon.pico.spi.impl.DefaultInjector injector = createInjector();
    //            this.injector.set(injector);
    //
    //            return Optional.of(injector);
    //        }
    //    }

//        @Override
//        public Optional<ServiceBinder> createServiceBinder(Module module) {
//            if (!cfg.permitsDynamic()) {
//                return Optional.empty();
//            }
//            return Optional.of(createServices()
//                                       .createServiceBinder(this, getServices(), io.helidon.pico.spi.impl.DefaultServices
//                                       .toModuleName(module)));
//        }

    //    @Override
    //    public Future<Map<String, ActivationResult<?>>> shutdown() {
    //        LOGGER.log(System.Logger.Level.INFO, "in shutdown");
    //        io.helidon.pico.spi.impl.DefaultServices services = getServices();
    //        io.helidon.pico.spi.impl.DefaultInjector injector = (io.helidon.pico.spi.impl.DefaultInjector) getInjector().get();
    //        CompletableFuture<Map<String, ActivationResult<?>>> completableFuture = new CompletableFuture<>();
    //        ConcurrentHashMap<String, ActivationResult<?>> map = new ConcurrentHashMap<>();
    //
    //        ThreadFactory threadFactory = r -> {
    //            Thread thread = new Thread(r);
    //            thread.setDaemon(false);
    //            thread.setPriority(Thread.MAX_PRIORITY);
    //            thread.setName(PicoServicesConfig.NAME + "-shutdown-" + System.currentTimeMillis());
    //            return thread;
    //        };
    //
    //        ExecutorService es = Executors.newSingleThreadExecutor(threadFactory);
    //        es.submit(() -> {
    //            try {
    //                List<ActivationLog.ActivationEntry> log = Objects.isNull(activationLog) ? null : activationLog.getLog();
    //                if (Objects.nonNull(log)) {
    //                    log = new ArrayList<>(log);
    //                    Collections.reverse(log);
    //
    //                    List<ServiceProvider<Object>> serviceProviders =
    //                            log.stream()
    //                                    .map(ActivationLog.ActivationEntry::getServiceProvider)
    //                                    .filter((sp) -> sp.getCurrentActivationPhase().isEligibleForDeactivation())
    //                                    .collect(Collectors.toList());
    //
    //                    // prepare for the shutdown log event sequence
    //                    activationLog.clear();
    //
    //                    // shutdown using the reverse chrono ordering in the log for starters...
    //                    shutdown(map, services, injector, activationLog, serviceProviders);
    //                }
    //
    //                // next get all services that are beyond INIT state, and sort by runlevel order, and shut those down
    //                // too...
    //                List<ServiceProvider<Object>> serviceProviders = services.lookup(DefaultServiceInfo.builder().build(),
    //                                                                                 false);
    //                serviceProviders = serviceProviders.stream()
    //                        .filter((sp) -> sp.getCurrentActivationPhase().isEligibleForDeactivation())
    //                        .collect(Collectors.toList());
    //                serviceProviders.sort((o1, o2) -> {
    //                    Integer runLevel1 = o1.getServiceInfo().getRunLevel();
    //                    if (Objects.isNull(runLevel1)) {
    //                        runLevel1 = RunLevel.NORMAL;
    //                    }
    //                    Integer runLevel2 = o2.getServiceInfo().getRunLevel();
    //                    if (Objects.isNull(runLevel2)) {
    //                        runLevel2 = RunLevel.NORMAL;
    //                    }
    //                    return Integer.compare(runLevel1, runLevel2);
    //                });
    //                shutdown(map, services, injector, activationLog, serviceProviders);
    //
    //                // finally, clear everything...
    //                clear();
    //
    //                completableFuture.complete(map);
    //            } catch (Throwable t) {
    //                LOGGER.log(System.Logger.Level.ERROR, "failed in shutdown", t);
    //                completableFuture.completeExceptionally(t);
    //            } finally {
    //                es.shutdown();
    //            }
    //        });
    //
    //        return completableFuture;
    //    }
    //
    //    protected void shutdown(ConcurrentHashMap<String, ActivationResult<?>> map,
    //                            io.helidon.pico.spi.impl.DefaultServices services,
    //                            io.helidon.pico.spi.impl.DefaultInjector injector,
    //                            io.helidon.pico.spi.impl.DefaultActivationLog activationLog,
    //                            List<ServiceProvider<Object>> serviceProviders) {
    //        for (ServiceProvider<Object> sp : serviceProviders) {
    //            if (!sp.getCurrentActivationPhase().isEligibleForDeactivation()) {
    //                continue;
    //            }
    //
    //            ActivationResult<?> result;
    //            try {
    //                result = injector.deactivate(sp, services, activationLog, Injector.Strategy.ANY);
    //            } catch (Throwable t) {
    //                result = DefaultActivationResult.builder()
    //                        .serviceProvider(sp)
    //                        .finishingStatus(ActivationStatus.FAILURE)
    //                        .error(t)
    //                        .build();
    //                map.put(sp.getServiceInfo().getServiceTypeName(), result);
    //            }
    //            map.put(sp.getServiceInfo().getServiceTypeName(), result);
    //        }
    //    }
    //
    //    protected io.helidon.pico.spi.impl.DefaultServices createServices() {
    //        return new io.helidon.pico.spi.impl.DefaultServices(getConfig().get());
    //    }
    //
    //    protected io.helidon.pico.spi.impl.DefaultInjector createInjector() {
    //        return new io.helidon.pico.spi.impl.DefaultInjector();
    //    }
    //
    //    /**
    //     * Services as the multiplexer from {@link io.helidon.pico.spi.Application} to each constituent
    //     * {@link io.helidon.pico.spi.ServiceProviderBindable} from our service registry. If the service is not found in
    //     * our
    //     * registry we will generate a runtime exception. If the constituent is found, but does not provide binding
    //     * capabilities
    //     * then this instance is also used to gracefully and quietly consume the plan via a no-op.
    //     */
    //    protected static class InjectionPlanBinder
    //            implements ServiceInjectionPlanBinder, ServiceInjectionPlanBinder.Binder {
    //
    //        private final io.helidon.pico.spi.impl.DefaultServices services;
    //
    //        protected InjectionPlanBinder(io.helidon.pico.spi.impl.DefaultServices services) {
    //            this.services = services;
    //        }
    //
    //        @Override
    //        public Binder bindTo(ServiceProvider<?> serviceProvider) {
    //            ServiceProvider<?> sp = services.getServiceProvider(serviceProvider);
    //            if (Objects.isNull(sp)) {
    //                throw new InjectionException("expected to find a service in the service registry: "
    //                                                     + serviceProvider, null, serviceProvider, null);
    //            }
    //            ServiceProviderBindable<?> bindable = ServiceProviderBindable.toBindableProvider(sp);
    //            Binder binder = Objects.nonNull(bindable) ? bindable.toInjectionPlanBinder() : null;
    //            if (Objects.isNull(binder)) {
    //                LOGGER.log(System.Logger.Level.WARNING,
    //                           "this service provider is not capable of being bound to injection points: " + sp);
    //                return this;
    //            }
    //            return binder;
    //        }
    //
    //        @Override
    //        public <T> Binder bind(String ipIdentity, ServiceProvider<T> serviceProvider) {
    //            // NOP
    //            return this;
    //        }
    //
    //        @Override
    //        public Binder bindMany(String ipIdentity, ServiceProvider<?>... serviceProviders) {
    //            // NOP
    //            return this;
    //        }
    //
    //        @Override
    //        public Binder bindVoid(String ipIdentity) {
    //            // NOP
    //            return this;
    //        }
    //
    //        @Override
    //        public Binder resolvedBind(String ipIdentity, Class<?> serviceType) {
    //            // NOP
    //            return this;
    //        }
    //
    //        @Override
    //        public void commit() {
    //            // NOP
    //        }
    //    }

    private void initializeServices() {
        if (isGlobal) {
            // iterate over all modules, binding to each one's set of services, but with NO activations
            List<Module> modules = findModules(true);
            try {
                isBinding.set(true);
                bindModules(services.get(), modules);
            } finally {
                isBinding.set(false);
            }
        }

        if (isGlobal) {
            // look for the literal injection plan
            // typically only be one Application in non-testing runtimes
            List<Application> apps = findApplications(true);
            bindApplications(services.get(), apps);
        }

        if (isGlobal) {
            services.get().allServiceProviders(false).forEach(sp -> {
                if (sp instanceof EventReceiver) {
                    ((EventReceiver) sp).onEvent(EventReceiver.Event.POST_BIND_ALL_MODULES);
                }
            });
        }

        if (isGlobal || cfg.supportsCompileTime()) {
            services.get().allServiceProviders(false).forEach(sp -> {
                if (sp instanceof EventReceiver) {
                    ((EventReceiver) sp).onEvent(EventReceiver.Event.FINAL_RESOLVE);
                }
            });
        }

        // notify interested service providers of "readiness"...
        services.get().allServiceProviders(false).stream()
                .filter(sp -> sp instanceof EventReceiver)
                .forEach(sp -> ((EventReceiver) sp)
                        .onEvent(EventReceiver.Event.SERVICES_READY));
    }

    private List<Application> findApplications(boolean load) {
        if (applicationList.get() != null) {
            return applicationList.get();
        }

        synchronized (applicationList) {
            if (applicationList.get() != null) {
                return applicationList.get();
            }

            List<Application> result = new LinkedList<>();
            if (load) {
                ServiceLoader<Application> serviceLoader = ServiceLoader.load(Application.class);
                for (Application app : serviceLoader) {
                    result.add(app);
                }
            }

            if (!cfg.permitsDynamic()) {
                result = Collections.unmodifiableList(result);
                applicationList.set(result);
            }

            return result;
        }
    }

    private List<Module> findModules(boolean load) {
        if (moduleList.get() != null) {
            return moduleList.get();
        }

        synchronized (moduleList) {
            if (moduleList.get() != null) {
                return moduleList.get();
            }

            List<Module> result = new LinkedList<>();
            if (load) {
                ServiceLoader<Module> serviceLoader = ServiceLoader.load(Module.class);
                for (Module module : serviceLoader) {
                    result.add(module);
                }
            }

            if (!cfg.permitsDynamic()) {
                result = Collections.unmodifiableList(result);
                moduleList.set(result);
            }

            return result;
        }
    }

    protected void bindApplications(DefaultServices services,
                                    Collection<Application> apps) {
        if (!cfg.usesCompileTime()) {
            LOGGER.log(System.Logger.Level.DEBUG, "application binding is disabled");
            return;
        }

        if (apps.size() > 1) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "there is typically only 1 application instance; app instances = " + apps);
        } else if (apps.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "no " + Application.class.getName() + " was found.");
            return;
        }

        apps.forEach((app) -> {
            // toDO:
//            ServiceInjectionPlanBinder injectionPlanBinder = new InjectionPlanBinder(services);
//            app.configure(injectionPlanBinder);
        });
    }

    private void bindModules(DefaultServices services,
                             Collection<Module> modules) {
        if (modules.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "no " + Module.class.getName() + " was found.");
        } else {
            modules.forEach(module -> services.bind(this, module));
        }
    }

    /**
     * Returns true if the target qualifies for injection.
     *
     * @param sp the service provider of the target
     * @return true if the target qualifies for injection
     */
    static boolean isQualifiedInjectionTarget(ServiceProvider<?> sp) {
        ServiceInfo serviceInfo = sp.serviceInfo();
        Set<String> contractsImplemented = serviceInfo.contractsImplemented();
        return !contractsImplemented.isEmpty()
                && !contractsImplemented.contains(Module.class.getName())
                && !contractsImplemented.contains(Application.class.getName());
    }

}
