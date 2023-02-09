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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationLogEntry;
import io.helidon.pico.ActivationLogQuery;
import io.helidon.pico.ActivationPhaseReceiver;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.Application;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.DefaultActivationLogEntry;
import io.helidon.pico.DefaultActivationResult;
import io.helidon.pico.DefaultMetrics;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.Event;
import io.helidon.pico.Injector;
import io.helidon.pico.InjectorOptions;
import io.helidon.pico.Metrics;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.spi.Resetable;

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
    private final State state = State.create(Phase.INIT);
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
        this.bootstrap = bootstrap;
        this.cfg = DefaultPicoServicesConfig.createDefaultConfigBuilder().build();
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
        DefaultServices thisServices = services.get();
        if (thisServices == null) {
            // never has been any lookup yet
            return Optional.of(DefaultMetrics.builder().build());
        }
        return Optional.of(thisServices.metrics());
    }

    @Override
    public Optional<Set<ServiceInfoCriteria>> lookups() {
        if (!cfg.serviceLookupCaching()) {
            return Optional.empty();
        }

        DefaultServices thisServices = services.get();
        if (thisServices == null) {
            // never has been any lookup yet
            return Optional.of(Set.of());
        }
        return Optional.of(thisServices.cache().keySet());
    }

    @Override
    public Optional<ServiceBinder> createServiceBinder(
            io.helidon.pico.Module module) {
        DefaultServices.assertPermitsDynamic(cfg);
        String moduleName = module.named().orElse(module.getClass().getName());
        return Optional.of(DefaultServiceBinder.create(this, moduleName, false));
    }

    @Override
    public DefaultServices services() {
        if (!initializingServices.getAndSet(true)) {
            try {
                initializeServices();
                initializedServices.countDown();
            } catch (Throwable t) {
                initializingServices.set(false);
                state.lastError(t);
                if (t instanceof PicoException) {
                    throw (PicoException) t;
                } else {
                    throw new PicoException("failed to initialize: " + t.getMessage(), t);
                }
            } finally {
                state.finished(true);
            }
        }

        DefaultServices thisServices = services.get();
        if (thisServices == null) {
            throw new PicoException("must reset() after shutdown()");
        }
        return thisServices;
    }

    @Override
    public Optional<Map<String, ActivationResult>> shutdown() {
        Map<String, ActivationResult> result = Map.of();
        DefaultServices current = services.get();
        if (services.compareAndSet(current, null) && current != null) {
            State currentState = state.clone().currentPhase(Phase.PRE_DESTROYING);
            log("started shutdown");
            result = doShutdown(current, currentState);
            log("finished shutdown");
        }
        return Optional.ofNullable(result);
    }

    private Map<String, ActivationResult> doShutdown(
            DefaultServices services,
            State state) {
        long start = System.currentTimeMillis();

        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setName(PicoServicesConfig.NAME + "-shutdown-" + System.currentTimeMillis());
            return thread;
        };

        Shutdown shutdown = new Shutdown(services, state);
        ExecutorService es = Executors.newSingleThreadExecutor(threadFactory);
        long finish;
        try {
            return es.submit(shutdown)
                    // note to self: have an appropriate timeout config for this
//                    .get(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS);
                    .get();
        } catch (Throwable t) {
            finish = System.currentTimeMillis();
            errorLog("error during shutdown (elapsed = " + (finish - start) + " ms)", t);
            throw new PicoException("error during shutdown", t);
        } finally {
            es.shutdown();
            state.finished(true);
            finish = System.currentTimeMillis();
            log("finished shutdown (elapsed = " + (finish - start) + " ms)");
        }
    }

    /**
     * Will attempt to shut down in reverse order of activation, but only if activation logs are retained.
     */
    private class Shutdown implements Callable<Map<String, ActivationResult>> {
        private final DefaultServices services;
        private final State state;
        private final ActivationLog log;
        private final Injector injector;
        private final InjectorOptions opts = InjectorOptions.DEFAULT.get();
        private final Map<String, ActivationResult> map = new LinkedHashMap<>();

        Shutdown(
                DefaultServices services,
                State state) {
            this.services = Objects.requireNonNull(services);
            this.state = Objects.requireNonNull(state);
            this.injector = injector().orElseThrow();
            this.log = activationLog().orElseThrow();
        }

        @Override
        public Map<String, ActivationResult> call() {
            state.currentPhase(Phase.DESTROYED);

            ActivationLogQuery query = log.toQuery().orElse(null);
            if (query != null) {
                // we can lean on the log entries in order to shut down in reverse chronological order
                List<ActivationLogEntry> fullyActivationLog = new ArrayList<>(query.fullActivationLog());
                if (!fullyActivationLog.isEmpty()) {
                    LinkedHashSet<ServiceProvider<?>> serviceProviderActivations = new LinkedHashSet<>();

                    Collections.reverse(fullyActivationLog);
                    fullyActivationLog.stream()
                            .map(ActivationLogEntry::serviceProvider)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .forEach(serviceProviderActivations::add);

                    // prepare for the shutdown log event sequence
                    log.toQuery().ifPresent(it -> it.reset(false));

                    // shutdown using the reverse chronological ordering in the log for starters
                    doFinalShutdown(serviceProviderActivations);
                }
            }

            // next get all services that are beyond INIT state, and sort by runlevel order, and shut those down also
            List<ServiceProvider<?>> serviceProviders = services.lookupAll(DefaultServiceInfoCriteria.builder().build(), false);
            serviceProviders = serviceProviders.stream()
                    .filter((sp) -> sp.currentActivationPhase().eligibleForDeactivation())
                    .collect(Collectors.toList());
            serviceProviders.sort((o1, o2) -> {
                int runLevel1 = o1.serviceInfo().realizedRunLevel();
                int runLevel2 = o2.serviceInfo().realizedRunLevel();
                return Integer.compare(runLevel1, runLevel2);
            });
            doFinalShutdown(serviceProviders);

            // finally, clear everything
            reset(false);

            return map;
        }

        private void doFinalShutdown(
                Collection<ServiceProvider<?>> serviceProviders) {
            for (ServiceProvider<?> csp : serviceProviders) {
                Phase startingActivationPhase = csp.currentActivationPhase();
                ActivationResult result;
                try {
                    result = injector.deactivate(csp, opts);
                } catch (Throwable t) {
                    errorLog("error during shutdown", t);
                    result = DefaultActivationResult.builder()
                            .serviceProvider(csp)
                            .startingActivationPhase(startingActivationPhase)
                            .targetActivationPhase(Phase.DESTROYED)
                            .finishingActivationPhase(csp.currentActivationPhase())
                            .finishingStatus(ActivationStatus.FAILURE)
                            .error(t)
                            .build();
                }
                map.put(csp.serviceInfo().serviceTypeName(), result);
            }
        }
    }

    @Override
    public boolean reset(
            boolean deep) {
        DefaultServices.assertPermitsDynamic(cfg);
        boolean result = deep;

        DefaultServices prev = services.get();
        if (prev != null) {
            boolean affected = prev.reset(deep);
            result |= affected;
        }

        boolean affected = log.reset(deep);
        result |= affected;

        if (deep) {
            isBinding.set(false);
            initializedServices = new CountDownLatch(1);
            moduleList.set(null);
            applicationList.set(null);
            if (prev != null) {
                services.set(new DefaultServices(cfg));
            }
            initializingServices.set(false);
            state.reset(true);
        }

        return result;
    }

    private synchronized void initializeServices() {
        if (services.get() == null) {
            services.set(new DefaultServices(cfg));
        }

        DefaultServices thisServices = services.get();
        thisServices.state(state);
        state.currentPhase(Phase.ACTIVATION_STARTING);

        if (isGlobal) {
            // iterate over all modules, binding to each one's set of services, but with NO activations
            List<io.helidon.pico.Module> modules = findModules(true);
            try {
                isBinding.set(true);
                bindModules(thisServices, modules);
            } finally {
                isBinding.set(false);
            }
        }

        state.currentPhase(Phase.GATHERING_DEPENDENCIES);

        if (isGlobal) {
            // look for the literal injection plan
            // typically only be one Application in non-testing runtimes
            List<Application> apps = findApplications(true);
            bindApplications(thisServices, apps);
        }

        state.currentPhase(Phase.POST_BIND_ALL_MODULES);

        if (isGlobal) {
            // only the global services registry gets eventing (no particular reason though)
            thisServices.allServiceProviders(false).stream()
                    .filter(sp -> sp instanceof ActivationPhaseReceiver)
                    .map(sp -> (ActivationPhaseReceiver) sp)
                    .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.POST_BIND_ALL_MODULES));
        }

        state.currentPhase(Phase.FINAL_RESOLVE);

        if (isGlobal || cfg.supportsCompileTime()) {
            thisServices.allServiceProviders(false).stream()
                    .filter(sp -> sp instanceof ActivationPhaseReceiver)
                    .map(sp -> (ActivationPhaseReceiver) sp)
                    .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.FINAL_RESOLVE));
        }

        state.currentPhase(Phase.SERVICES_READY);

        // notify interested service providers of "readiness"...
        thisServices.allServiceProviders(false).stream()
                .filter(sp -> sp instanceof ActivationPhaseReceiver)
                .map(sp -> (ActivationPhaseReceiver) sp)
                .forEach(sp -> sp.onPhaseEvent(Event.STARTING, Phase.SERVICES_READY));

        state.finished(true);
    }

    private List<Application> findApplications(
            boolean load) {
        List<Application> result = applicationList.get();
        if (result != null) {
            return result;
        }

        result = new ArrayList<>();
        if (load) {
            ServiceLoader<Application> serviceLoader = ServiceLoader.load(Application.class);
            for (Application app : serviceLoader) {
                result.add(app);
            }

            if (!cfg.permitsDynamic()) {
                applicationList.compareAndSet(null, List.copyOf(result));
                result = applicationList.get();
            }
        }
        return result;
    }

    private List<io.helidon.pico.Module> findModules(
            boolean load) {
        List<io.helidon.pico.Module> result = moduleList.get();
        if (result != null) {
            return result;
        }

        result = new ArrayList<>();
        if (load) {
            ServiceLoader<io.helidon.pico.Module> serviceLoader = ServiceLoader.load(io.helidon.pico.Module.class);
            for (io.helidon.pico.Module module : serviceLoader) {
                result.add(module);
            }

            if (!cfg.permitsDynamic()) {
                moduleList.compareAndSet(null, List.copyOf(result));
                result = moduleList.get();
            }
        }
        return result;
    }

    protected void bindApplications(
            DefaultServices services,
            Collection<Application> apps) {
        if (!cfg.usesCompileTimeApplications()) {
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
        if (!cfg.usesCompileTimeModules()) {
            LOGGER.log(System.Logger.Level.DEBUG, "module binding is disabled");
            return;
        }

        if (modules.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "no " + io.helidon.pico.Module.class.getName() + " was found.");
        } else {
            modules.forEach(module -> services.bind(this, module, isBinding.get()));
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
