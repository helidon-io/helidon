/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationLogEntry;
import io.helidon.pico.ActivationPhaseReceiver;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.Application;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.DefaultActivationLogEntry;
import io.helidon.pico.DefaultActivationResult;
import io.helidon.pico.DefaultInjectorOptions;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.Event;
import io.helidon.pico.Injector;
import io.helidon.pico.Metrics;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.Resetable;

import static io.helidon.pico.services.DefaultPicoServicesConfig.realizedBootStrapConfig;

/**
 * The default implementation for {@link io.helidon.pico.PicoServices}.
 */
class DefaultPicoServices implements PicoServices, Resetable {
    static final System.Logger LOGGER = System.getLogger(DefaultPicoServices.class.getName());

    private final AtomicBoolean initializingServices = new AtomicBoolean();
    private final AtomicBoolean isBinding = new AtomicBoolean();
    private final AtomicReference<DefaultServices> services = new AtomicReference<>();
    private final AtomicReference<List<io.helidon.pico.Module>> moduleList = new AtomicReference<>();
    private final AtomicReference<List<Application>> applicationList = new AtomicReference<>();
    private final Bootstrap bootstrap;
    private final PicoServicesConfig cfg;
    private final boolean isGlobal;
    private final DefaultActivationLog log;
    private CountDownLatch initializedServices = new CountDownLatch(1);

    /**
     * Constructor taking the bootstrap.
     *
     * @param bootstrap the bootstrap configuration
     * @param global    flag indicating whether this is the global con
     */
    DefaultPicoServices(
            Bootstrap bootstrap,
            boolean global) {
        this.bootstrap = Objects.requireNonNull(bootstrap);
        this.cfg = realizedBootStrapConfig(Optional.empty());
        this.isGlobal = global;
        this.log = cfg.activationLogs()
                ? DefaultActivationLog.createRetainedLog(LOGGER)
                : DefaultActivationLog.createUnretainedLog(LOGGER);
    }

    @Override
    public Bootstrap bootstrap() {
        return bootstrap;
    }

    @Override
    public PicoServicesConfig config() {
        return cfg;
    }

    @Override
    public Optional<ActivationLog> activationLog() {
        return Optional.of(log);
    }

    @Override
    public Optional<Injector> injector() {
        return Optional.of(new DefaultInjector());
    }

    @Override
    public Optional<Metrics> metrics() {
        return Optional.of(services.get().metrics());
    }

    @Override
    public Optional<ServiceBinder> createServiceBinder(
            io.helidon.pico.Module module) {
        DefaultServices.assertPermitsDynamic(cfg);
        return Optional.of(new DefaultServiceBinder(this, services(), module.name().orElse(null)));
    }

    @Override
    public DefaultServices services() {
        if (!initializingServices.getAndSet(true)) {
            try {
                initializeServices();
                initializedServices.countDown();
            } catch (Throwable t) {
                initializingServices.set(false);
                if (t instanceof PicoException) {
                    throw (PicoException) t;
                } else {
                    throw new PicoException("failed to initialize: " + t.getMessage(), t);
                }
            }
        }

        return services.get();
    }

    @Override
    public Optional<Map<String, ActivationResult>> shutdown() {
        log("started shutdown");
        Map<String, ActivationResult> result = null;
        if (services.get() != null) {
            try {
                result = doShutdown();
            } catch (Throwable t) {
                errorLog("failed to shutdown properly", t);
            }
        }
        log("finished shutdown");
        return Optional.ofNullable(result);
    }

    Map<String, ActivationResult> doShutdown() throws ExecutionException, InterruptedException {
        DefaultServices services = services();
        Injector injector = injector().orElseThrow();
        ActivationLog log = activationLog().orElseThrow();

        CompletableFuture<Map<String, ActivationResult>> completableFuture = new CompletableFuture<>();
        Map<String, ActivationResult> map = new ConcurrentHashMap<>();
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setName(PicoServicesConfig.NAME + "-shutdown-" + System.currentTimeMillis());
            return thread;
        };

        // will try to deactivate in the reverse order they were created, but only if logs were retained
        ExecutorService es = Executors.newSingleThreadExecutor(threadFactory);
        es.submit(() -> {
            try {
                List<ActivationLogEntry> fullyActivationLog = log.toQuery().orElseThrow().fullActivationLog();
                if (!fullyActivationLog.isEmpty()) {
                    LinkedHashSet<ServiceProvider<?>> serviceProviderActivations = new LinkedHashSet<>();

                    Collections.reverse(fullyActivationLog);
                    fullyActivationLog.stream()
                            .map(ActivationLogEntry::serviceProvider)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(sp -> sp.currentActivationPhase().eligibleForDeactivation())
                            .forEach(serviceProviderActivations::add);

                    // prepare for the shutdown log event sequence
                    log.toQuery().ifPresent(it -> it.reset(false));

                    // shutdown using the reverse chronological ordering in the log for starters
                    doShutdown(map, injector, serviceProviderActivations);
                }

                // next get all services that are beyond INIT state, and sort by runlevel order, and shut those down also
                List<ServiceProvider<?>> serviceProviders = services.lookupAll(DefaultServiceInfoCriteria.builder().build());
                serviceProviders = serviceProviders.stream()
                        .filter((sp) -> sp.currentActivationPhase().eligibleForDeactivation())
                        .collect(Collectors.toList());
                serviceProviders.sort((o1, o2) -> {
                    int runLevel1 = o1.serviceInfo().realizedRunLevel();
                    int runLevel2 = o2.serviceInfo().realizedRunLevel();
                    return Integer.compare(runLevel1, runLevel2);
                });
                doShutdown(map, injector, serviceProviders);

                // finally, clear everything
                reset(false);

                completableFuture.complete(map);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR, "failed in shutdown", t);
                completableFuture.completeExceptionally(t);
            } finally {
                es.shutdown();
            }
        });

        return completableFuture.get();
    }

    private void doShutdown(
            Map<String, ActivationResult> map,
            Injector injector,
            Collection<ServiceProvider<?>> serviceProviders) {
        DefaultInjectorOptions opts = DefaultInjectorOptions.builder()
                .throwOnFailure(false)
                .build();
        for (ServiceProvider<?> sp : serviceProviders) {
            assert (sp.currentActivationPhase().eligibleForDeactivation());

            ActivationResult result;
            try {
                result = injector.deactivate(sp, opts);
            } catch (Throwable t) {
                result = DefaultActivationResult.builder()
                        .serviceProvider(sp)
                        .finishingStatus(ActivationStatus.FAILURE)
                        .error(t)
                        .build();
            }
            Object prev = map.put(sp.serviceInfo().serviceTypeName(), result);
            assert (prev == null);
        }
    }

    @Override
    public boolean reset(
            boolean deep) {
        boolean result = deep;

        DefaultServices.assertPermitsDynamic(cfg);

        synchronized (services) {
            DefaultServices prev = services.get();
            if (prev != null) {
                boolean affected = prev.reset(deep);
                result |= affected;
            }

            boolean affected = log.reset(deep);
            result |= affected;

            if (deep) {
                synchronized (moduleList) {
                    isBinding.set(false);
                    initializedServices = new CountDownLatch(1);
                    moduleList.set(null);
                    applicationList.set(null);
                    if (prev != null) {
                        services.set(new DefaultServices(cfg));
                    }
                    initializingServices.set(false);
                }
            }
        }

        return result;
    }

    private void initializeServices() {
        if (services.get() == null) {
            services.set(new DefaultServices(cfg));
        }

        if (isGlobal) {
            // iterate over all modules, binding to each one's set of services, but with NO activations
            List<io.helidon.pico.Module> modules = findModules(true);
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
            services.get().allServiceProviders(false).stream()
                    .filter(sp -> sp instanceof ActivationPhaseReceiver)
                    .map(sp -> (ActivationPhaseReceiver) sp)
                    .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.POST_BIND_ALL_MODULES));
        }

        if (isGlobal || cfg.supportsCompileTime()) {
            services.get().allServiceProviders(false).stream()
                    .filter(sp -> sp instanceof ActivationPhaseReceiver)
                    .map(sp -> (ActivationPhaseReceiver) sp)
                    .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.FINAL_RESOLVE));
        }

        // notify interested service providers of "readiness"...
        services.get().allServiceProviders(false).stream()
                .filter(sp -> sp instanceof ActivationPhaseReceiver)
                .map(sp -> (ActivationPhaseReceiver) sp)
                .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.SERVICES_READY));
    }

    private List<Application> findApplications(
            boolean load) {
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

    private List<io.helidon.pico.Module> findModules(
            boolean load) {
        if (moduleList.get() != null) {
            return moduleList.get();
        }

        synchronized (moduleList) {
            if (moduleList.get() != null) {
                return moduleList.get();
            }

            List<io.helidon.pico.Module> result = new LinkedList<>();
            if (load) {
                ServiceLoader<io.helidon.pico.Module> serviceLoader = ServiceLoader.load(io.helidon.pico.Module.class);
                for (io.helidon.pico.Module module : serviceLoader) {
                    result.add(module);
                }
            }

            if (!cfg.permitsDynamic()) {
                result = Collections.unmodifiableList(result);
            }
            moduleList.set(result);

            return result;
        }
    }

    protected void bindApplications(
            DefaultServices services,
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

        DefaultInjectionPlanBinder injectionPlanBinder = new DefaultInjectionPlanBinder(services);
        apps.forEach(app -> services.bind(this, injectionPlanBinder, app));
    }

    private void bindModules(
            DefaultServices services,
            Collection<io.helidon.pico.Module> modules) {
        if (modules.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "no " + io.helidon.pico.Module.class.getName() + " was found.");
        } else {
            modules.forEach(module -> services.bind(this, module));
        }
    }

    private void log(
            String message) {
        ActivationLogEntry entry = DefaultActivationLogEntry.builder()
                .message(message)
                .build();
        log.record(entry);
    }

    private void errorLog(
            String message,
            Throwable t) {
        ActivationLogEntry entry = DefaultActivationLogEntry.builder()
                .message(message)
                .error(t)
                .build();
        log.record(entry);
    }

}
