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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationPhaseReceiver;
import io.helidon.pico.ActivationRequest;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.Activator;
import io.helidon.pico.ContextualServiceQuery;
import io.helidon.pico.DeActivationRequest;
import io.helidon.pico.DeActivator;
import io.helidon.pico.DefaultActivationLogEntry;
import io.helidon.pico.DefaultActivationResult;
import io.helidon.pico.DefaultInjectionPointInfo;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.Event;
import io.helidon.pico.InjectionException;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.PostConstructMethod;
import io.helidon.pico.PreDestroyMethod;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInjectionPlanBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.Services;
import io.helidon.pico.spi.InjectionResolver;
import io.helidon.pico.spi.Resetable;

import jakarta.inject.Provider;

/**
 * Abstract base implementation for {@link io.helidon.pico.ServiceProviderBindable}, which represents the basics for regular
 * Singleton, ApplicationScoped, Provider, and ServiceProvider based managed services. All Pico code-generated services will
 * extend from this abstract base class.
 *
 * @param <T> the type of the service this provider manages
 */
public abstract class AbstractServiceProvider<T>
        implements ServiceProviderBindable<T>,
                   Activator<T>,
                   DeActivator<T>,
                   ActivationPhaseReceiver,
                   Resetable {
    private static final System.Logger LOGGER = System.getLogger(AbstractServiceProvider.class.getName());

    private final Semaphore activationSemaphore = new Semaphore(1);
    private final AtomicReference<T> serviceRef = new AtomicReference<>();
    private Phase phase;
    private long lastActivationThreadId;
    private PicoServices picoServices;
    private ActivationLog log;
    private ServiceInfo serviceInfo;
    private DependenciesInfo dependencies;
    private Map<String, InjectionPlan> injectionPlan;
    private ServiceProvider<?> interceptor;

    /**
     * The default constructor.
     */
    public AbstractServiceProvider() {
        this.phase = Phase.INIT;
    }

    /**
     * Constructor.
     *
     * @param instance     the managed service instance
     * @param phase        the current phase
     * @param serviceInfo  the service info
     * @param picoServices the pico services instance
     */
    protected AbstractServiceProvider(
            T instance,
            Phase phase,
            ServiceInfo serviceInfo,
            PicoServices picoServices) {
        this();

        if (Objects.nonNull(instance)) {
            this.serviceRef.set(instance);
            this.phase = (phase != null) ? phase : Phase.ACTIVE;
        }
        this.serviceInfo = DefaultServiceInfo.toBuilder(serviceInfo).build();
        this.picoServices = picoServices;
    }

    @Override
    public Optional<Activator<T>> activator() {
        return Optional.of(this);
    }

    @Override
    public Optional<DeActivator<T>> deActivator() {
        return Optional.of(this);
    }

    @Override
    public ServiceProviderBindable<T> serviceProviderBindable() {
        return this;
    }

    @Override
    public boolean isSingletonScope() {
        return true;
    }

    @Override
    public boolean isProvider() {
        return false;
    }

    /**
     * Identifies whether the implementation was custom written and not code generated. We assume by default this is part
     * of code-generation, and the return defaulting to false.
     *
     * @return true if a custom, user-supplied implementation (rare)
     */
    protected boolean isCustom() {
        return false;
    }

    @Override
    public ServiceInfo serviceInfo() {
        return Objects.requireNonNull(serviceInfo);
    }

    /**
     * Sets the service info that describes the managed service that is assigned.
     *
     * @param serviceInfo the service info
     */
    protected void serviceInfo(
            ServiceInfo serviceInfo) {
        Objects.requireNonNull(serviceInfo);
        if (picoServices != null) {
            throw alreadyInitialized();
        }
        this.serviceInfo = serviceInfo;
    }

    @Override
    public DependenciesInfo dependencies() {
        return Objects.requireNonNull(dependencies);
    }

    protected void dependencies(DependenciesInfo dependencies) {
        Objects.requireNonNull(dependencies);
        if (picoServices != null) {
            throw alreadyInitialized();
        }
        this.dependencies = dependencies;
    }

    @Override
    public double weight() {
        return serviceInfo().realizedWeight();
    }

    @Override
    public Phase currentActivationPhase() {
        return phase;
    }

    protected boolean isActive() {
        return isAlreadyAtTargetPhase(Phase.ACTIVE);
    }

    protected boolean isAlreadyAtTargetPhase(
            Phase ultimateTargetPhase) {
        Objects.requireNonNull(ultimateTargetPhase);
        return (currentActivationPhase() == ultimateTargetPhase);
    }

    protected PicoServices picoServices() {
        return Objects.requireNonNull(picoServices);
    }

    @Override
    public void picoServices(
            Optional<PicoServices> picoServices) {

        if (picoServices.isPresent()
                || serviceRef.get() != null) {
            PicoServices current = this.picoServices;
            if (picoServices.orElse(null) == current) {
                return;
            }

            if (current != null) {
                if (current.config().permitsDynamic()) {
                    reset(true);
                } else {
                    throw alreadyInitialized();
                }
            }
        }

        this.picoServices = picoServices.orElse(null);
    }

    @Override
    public void moduleName(
            String moduleName) {
        Objects.requireNonNull(moduleName);
        ServiceInfo serviceInfo = serviceInfo();
        String moduleInfoName = serviceInfo.moduleName().orElse(null);
        if (!Objects.equals(moduleInfoName, moduleName)) {
            if (moduleInfoName != null) {
                throw alreadyInitialized();
            }
            this.serviceInfo = DefaultServiceInfo.toBuilder(serviceInfo).moduleName(moduleName).build();
        }
    }

    @Override
    public Optional<ServiceProvider<?>> interceptor() {
        return Optional.ofNullable(interceptor);
    }

    @Override
    public void interceptor(
            ServiceProvider<?> interceptor) {
        Objects.requireNonNull(interceptor);
        if (this.picoServices != null) {
            throw alreadyInitialized();
        }
        if (interceptor != this.interceptor) {
            this.interceptor = interceptor;
        }
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(serviceInfoName());
    }

    @Override
    public boolean equals(
            Object another) {
        return (another instanceof ServiceProvider)
                && serviceInfo().equals(((ServiceProvider<?>) another).serviceInfo());
    }

    @Override
    public String toString() {
        ServiceInfo serviceInfo = serviceInfo();
        return id() + ":" + currentActivationPhase() + ":" + serviceInfo.contractsImplemented();
    }

    @Override
    public String description() {
        return id() + ":" + serviceInfoName() + ":" + currentActivationPhase();
    }

    @Override
    public String id() {
        return identityPrefix() + classSimpleName() + identitySuffix();
    }

    /**
     * Returns our service info type name.
     *
     * @return our service info type name
     */
    protected String serviceInfoName() {
        return serviceInfo().serviceTypeName();
    }

    /**
     * Returns or class' simple name.
     *
     * @return our simple class name
     */
    protected String classSimpleName() {
        return getClass().getSimpleName();
    }

    /**
     * The identity prefix, or empty string if there is no prefix.
     *
     * @return the identity prefix
     */
    protected String identityPrefix() {
        return "";
    }

    /**
     * The identity suffix, or empty string if there is no suffix.
     *
     * @return the identity suffix
     */
    protected String identitySuffix() {
        return "";
    }

    /**
     * Returns the managed service this provider has (or is in the process of) activating.
     *
     * @return the service we are managing lifecycle for
     */
    protected Optional<T> serviceRef() {
        return Optional.ofNullable(serviceRef.get());
    }

    /**
     * Returns the activation log.
     *
     * @return the activation log
     */
    protected ActivationLog activationLog() {
        assert(picoServices != null) : "not initialized";
        if (null == log) {
            log = picoServices.activationLog().orElse(DefaultActivationLog.createUnretainedLog(LOGGER));
        }
        return log;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<T> first(
            ContextualServiceQuery ctx) {
        T serviceOrProvider = maybeActivate(ctx).orElse(null);

        try {
            if (isProvider()) {
                T instance;

                if (serviceOrProvider instanceof InjectionPointProvider) {
                    instance = ((InjectionPointProvider<T>) serviceOrProvider).first(ctx).orElse(null);
                } else if (serviceOrProvider instanceof Provider) {
                    instance = ((Provider<T>) serviceOrProvider).get();
                    if (ctx.expected() && instance == null) {
                        throw expectedQualifiedServiceError(ctx);
                    }
                } else {
                    instance = NonSingletonServiceProvider.createAndActivate(this);
                }

                return Optional.ofNullable(instance);
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw unableToActivate(t);
        }

        return Optional.ofNullable(serviceOrProvider);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<T> list(
            ContextualServiceQuery ctx) {
        T serviceProvider = maybeActivate(ctx).orElse(null);

        try {
            if (isProvider()) {
                List<T> instances = null;

                if (serviceProvider instanceof InjectionPointProvider) {
                    instances = ((InjectionPointProvider<T>) serviceProvider).list(ctx);
                } else if (serviceProvider instanceof Provider) {
                    T instance = ((Provider<T>) serviceProvider).get();
                    if (ctx.expected() && instance == null) {
                        throw expectedQualifiedServiceError(ctx);
                    }
                    if (instance != null) {
                        if (instance instanceof List) {
                            instances = (List<T>) instance;
                        } else {
                            instances = List.of(instance);
                        }
                    }
                } else {
                    T instance = NonSingletonServiceProvider.createAndActivate(this);
                    instances = List.of(instance);
                }

                return instances;
            }
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw unableToActivate(t);
        }

        return (serviceProvider != null) ? List.of(serviceProvider) : List.of();
    }

//    protected <T> T get(Map<String, T> deps,
//                        String id) {
//        T val = Objects.requireNonNull(deps.get(id), "'" + id + "' expected to have been found in: " + deps.keySet());
//        return val;
//    }

    /**
     * Will trigger an activation if the managed service is not yet active.
     *
     * @param ctx the context that triggered the activation
     * @return the result of the activation
     */
    protected Optional<T> maybeActivate(
            ContextualServiceQuery ctx) {
        try {
            T serviceOrProvider = serviceRef.get();

            if (serviceOrProvider == null
                    || Phase.ACTIVE != currentActivationPhase()) {
                ActivationRequest req = ActivationRequest.create(this, Phase.ACTIVE);
                ActivationResult res = activate(req);
                if (res.failure()) {
                    if (ctx.expected()) {
                        throw activationFailed(res);
                    }
                    return Optional.empty();
                }

                serviceOrProvider = serviceRef.get();
            }

            if (ctx.expected()
                    && serviceOrProvider == null) {
                throw managedServiceInstanceShouldHaveBeenSetException();
            }

            return Optional.ofNullable(serviceOrProvider);
        } catch (InjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw unableToActivate(t);
        }
    }

    @Override
    public ActivationResult activate(
            ActivationRequest req) {
        if (isAlreadyAtTargetPhase(req.targetPhase())) {
            return ActivationResult.createSuccess(this);
        }

        DefaultActivationResult.Builder res = preambleActivate(req);
        assert (!res.finished());

        // if we get here then we own the semaphore for activation...
        try {
            if (Phase.INIT == res.finishingActivationPhase()
                    || Phase.PENDING == res.finishingActivationPhase()
                    || Phase.DESTROYED == res.finishingActivationPhase()) {
                doActivationStarting(res, Phase.ACTIVATION_STARTING, false);
            }
            if (Phase.ACTIVATION_STARTING == res.finishingActivationPhase()) {
                doGatheringDependencies(res, Phase.GATHERING_DEPENDENCIES);
            }
            if (Phase.GATHERING_DEPENDENCIES == res.finishingActivationPhase()) {
                doConstructing(res, Phase.CONSTRUCTING);
            }
            if (Phase.CONSTRUCTING == res.finishingActivationPhase()) {
                doInjecting(res, Phase.INJECTING);
            }
            if (Phase.INJECTING == res.finishingActivationPhase()) {
                doPostConstructing(res, Phase.POST_CONSTRUCTING);
            }
            if (Phase.POST_CONSTRUCTING == res.finishingActivationPhase()) {
                doActivationFinishing(res, Phase.ACTIVATION_FINISHING, false);
            }
            if (Phase.ACTIVATION_FINISHING == res.finishingActivationPhase()) {
                doActivationActive(res, Phase.ACTIVE);
            }
            onFinished(res, res.finishingActivationPhase());
        } catch (Throwable t) {
            return failedFinish(res, t, req.throwOnFailure());
        } finally {
            this.lastActivationThreadId = 0;
            activationSemaphore.release();
        }

        return res.build();
    }

    private DefaultActivationResult.Builder preambleActivate(
            ActivationRequest req) {
        assert (req.serviceProvider() == this) : "not capable of handling service provider" + req;

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate
        DefaultActivationLogEntry.Builder entry = toLogEntry(Event.STARTING, req.targetPhase());
        entry.finishedActivationPhase(Phase.PENDING);
        if (log != null) {
            log.record(entry);
        }

//        Services services = picoServices.services();
//        if (req.injectionPoint().isPresent()) {
//            services = services.contextualServices(req.injectionPoint().get());
//        }
        DefaultActivationResult.Builder res = createResultPlaceholder(req.targetPhase());

        // fail fast if we are in a recursive situation on this thread...
        if (entry.threadId() == lastActivationThreadId) {
            return failedFinish(res, recursiveActivationInjectionError(entry, log), req.throwOnFailure());
        }

        PicoServicesConfig cfg = picoServices.config();
        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                // if we couldn't get semaphore than we (or someone else) is busy activating this services, or we deadlocked
                return failedFinish(res, timedOutActivationInjectionError(entry, log), req.throwOnFailure());
            }
            didAcquire = true;

            // if we made it to here then we "own" the semaphore and the subsequent activation steps...
            this.lastActivationThreadId = Thread.currentThread().getId();

            if (res.finished()) {
                didAcquire = false;
                activationSemaphore.release();
            }

            currentActivationPhase(res, Phase.PENDING);
        } catch (Throwable t) {
            this.lastActivationThreadId = 0;

            if (didAcquire) {
                activationSemaphore.release();
            }

            return failedFinish(res, interruptedPreActivationInjectionError(entry, log, t), req.throwOnFailure());
        }

        return res;
    }

    protected DefaultActivationResult.Builder createResultPlaceholder(
            Phase ultimateTargetPhase) {
        return DefaultActivationResult.builder()
                .serviceProvider(this)
                .startingActivationPhase(currentActivationPhase())
                .finishingActivationPhase(currentActivationPhase())
                .ultimateTargetActivationPhase(ultimateTargetPhase);
    }

    @Override
    public void onPhaseEvent(
            Event event,
            Phase phase) {
        if (log != null) {
            DefaultActivationLogEntry.Builder entry =
                    toLogEntry(event, phase);
            log.record(entry);
        }
    }

    protected void recordActivationEvent(
            Event event,
            Phase phase,
            DefaultActivationResult.Builder res) {
        currentActivationPhase(res, phase);

        if (log != null) {
            DefaultActivationLogEntry.Builder entry =
                    toLogEntry(event, res.ultimateTargetActivationPhase());
            log.record(entry);
        }
    }

    protected void doActivationStarting(
            DefaultActivationResult.Builder res,
            Phase phase,
            boolean didSomething) {
        if (didSomething) {
            recordActivationEvent(Event.FINISHED, phase, res);
        } else {
            currentActivationPhase(res, phase);
        }
    }

    /**
     * Called at startup to establish the injection plan as an alternative to gathering it dynamically.
     */
    @Override
    public Optional<ServiceInjectionPlanBinder.Binder> injectionPlanBinder() {
        if (dependencies == null) {
            dependencies(dependencies());

            if (dependencies == null) {
                // couldn't accept our suggested dependencies
                return Optional.empty();
            }
        }

        if (injectionPlan != null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "this service provider already has an injection plan (which is unusual here): " + this);
        }

        final ConcurrentHashMap<String, InjectionPointInfo> idToIpInfo = new ConcurrentHashMap<>();
        dependencies.allDependencies().forEach(dep -> {
            dep.injectionPointDependencies().forEach(ipDep -> {
                String id = ipDep.id();
                InjectionPointInfo prev = idToIpInfo.put(id, ipDep);
                if (prev != null
                        && !prev.equals(ipDep)
                        && !prev.dependencyToServiceInfo().equals(ipDep.dependencyToServiceInfo())) {
                    logMultiDefInjectionNote(id, prev, ipDep);
                }
            });
        });

        ConcurrentHashMap<String, InjectionPlan> injectionPlan = new ConcurrentHashMap<>();
        AbstractServiceProvider<T> self = AbstractServiceProvider.this;
        ServiceInjectionPlanBinder.Binder result = new ServiceInjectionPlanBinder.Binder() {
            private InjectionPointInfo ipInfo;

            private ServiceProvider<?> bind(
                    ServiceProvider<?> rawSp) {
                assert (!(rawSp instanceof BoundedServiceProvider)) : rawSp;
                return BoundedServiceProvider.create(rawSp, ipInfo);
            }

            private List<ServiceProvider<?>> bind(
                    List<ServiceProvider<?>> rawList) {
                return rawList.stream().map(this::bind).collect(Collectors.toList());
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bind(
                    String id,
                    ServiceProvider<?> serviceProvider) {
                DefaultInjectionPlan plan = createBuilder(id)
                        .injectionPointQualifiedServiceProviders(Collections.singletonList(bind(serviceProvider)))
                        .build();
                Object prev = injectionPlan.put(id, plan);
                assert (Objects.isNull(prev));
                return this;
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bindMany(
                    String id,
                    ServiceProvider<?>... serviceProviders) {
                DefaultInjectionPlan plan = createBuilder(id)
                        .injectionPointQualifiedServiceProviders(bind(Arrays.asList(serviceProviders)))
                        .build();
                Object prev = injectionPlan.put(id, plan);
                assert (prev == null);
                return this;
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bindVoid(
                    String id) {
                return bind(id, VoidServiceProvider.INSTANCE);
            }

            @Override
            public ServiceInjectionPlanBinder.Binder resolvedBind(
                    String id,
                    Class<?> serviceType) {
                InjectionResolver resolver = (InjectionResolver) AbstractServiceProvider.this;
                ServiceInfo serviceInfo = ServiceInfo.create(serviceType, Optional.empty());
                DefaultInjectionPointInfo ipInfo = DefaultInjectionPointInfo.builder()
                        .id(id)
                        .dependencyToServiceInfo(serviceInfo)
                        .build();
                Object resolved = Objects.requireNonNull(
                        resolver.resolve(ipInfo, picoServices(), AbstractServiceProvider.this, false));
                InjectionPlan plan = createBuilder(id)
                        .unqualifiedProviders(List.of(resolved))
                        .resolved(false)
                        .build();
                Object prev = injectionPlan.put(id, plan);
                assert (Objects.isNull(prev));
                return this;
            }

            @Override
            public void commit() {
                if (!idToIpInfo.isEmpty()) {
                    throw new InjectionException("missing injection bindings for "
                                                         + idToIpInfo + " in "
                                                         + description(), null, self);
                }

                if ((self.injectionPlan != null) && !self.injectionPlan.equals(injectionPlan)) {
                    throw new InjectionException("injection plan has already been bound for "
                                                         + description(), null, self);
                }
                self.injectionPlan = injectionPlan;
            }

            private InjectionPointInfo safeGetIpInfo(String id) {
                InjectionPointInfo ipInfo = idToIpInfo.remove(id);
                if (Objects.isNull(ipInfo)) {
                    throw new InjectionException("expected to find a dependency for '" + id + "' from "
                                                         + description() + " in " + idToIpInfo, null, self);
                }
                return ipInfo;
            }

            private DefaultInjectionPlan.Builder createBuilder(String id) {
                ipInfo = safeGetIpInfo(id);
                return DefaultInjectionPlan.builder()
                        .injectionPointInfo(ipInfo)
                        .serviceProvider(self);
            }
        };

        return Optional.of(result);
    }

    protected void doGatheringDependencies(
            DefaultActivationResult.Builder res,
            Phase phase) {
        recordActivationEvent(Event.STARTING, phase, res);

        Map<String, InjectionPlan> plans = getOrCreateInjectionPlan(false);
        if (plans != null) {
            res.injectionPlans(plans);
        }

        Map<String, Object> deps = resolveDependencies(plans);
        if (deps != null) {
            res.resolvedDependencies(deps);
        }

        recordActivationEvent(Event.FINISHED, phase, res);
    }

    /**
     * Get or Create the injection plan.
     *
     * @param resolveIps true if the injection points should also be activated/resolved.
     * @return the injection plan
     */
    Map<String, InjectionPlan> getOrCreateInjectionPlan(
            boolean resolveIps) {
        if (injectionPlan != null) {
            return injectionPlan;
        }

        if (dependencies == null) {
            dependencies(dependencies());
        }

        final Map<String, InjectionPlan> plan =
                InjectionPlan.createInjectionPlans(picoServices(), this, dependencies, resolveIps, LOGGER);
        assert (injectionPlan == null);
        injectionPlan = Objects.requireNonNull(plan);

        return injectionPlan;
    }

    @Override
    public boolean reset(boolean deep) {
        Object service = serviceRef.get();
        boolean result = false;
        if (service != null) {
            result = true;
            LOGGER.log(System.Logger.Level.INFO, "resetting " + this);
        }

        if (service instanceof Resetable) {
            try {
                ((Resetable) service).reset(deep);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.WARNING, "unable to reset: " + this, t);
            }
        }
        injectionPlan = null;
        interceptor = null;
        picoServices = null;
        serviceRef(null);
        currentActivationPhase(null, Phase.INIT);
        return result;
    }

    Map<String, Object> resolveDependencies(
            Map<String, InjectionPlan> plans) {
        Map<String, Object> result = new LinkedHashMap<>();

        plans.forEach((key, value) -> {
            if (value.wasResolved()) {
                result.put(key, value.resolved());
            } else {
                List<ServiceProvider<?>> serviceProviders = value.injectionPointQualifiedServiceProviders();
                serviceProviders = (serviceProviders == null)
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(serviceProviders);
                Object resolved;
                if (serviceProviders.isEmpty()
                        && !value.unqualifiedProviders().isEmpty()) {
                    resolved = Collections.emptyList(); // deferred...
                } else {
                    resolved = InjectionPlan.resolve(this, value.injectionPointInfo(), serviceProviders, LOGGER);
                }
                result.put(key, resolved);
            }
        });

        return result;
    }

    protected void doConstructing(
            DefaultActivationResult.Builder res,
            Phase phase) {
        recordActivationEvent(Event.STARTING, phase, res);

        Map<String, Object> deps = res.resolvedDependencies();
        serviceRef(createServiceProvider(deps));

        recordActivationEvent(Event.FINISHED, currentActivationPhase(), res);
    }

    protected void serviceRef(
            T instance) {
        serviceRef.set(instance);
    }

    protected T createServiceProvider(
            Map<String, Object> resolvedDeps) {
        throw new InjectionException("don't know how to create an instance of " + serviceInfo(), this, log);
    }

    protected void doInjecting(
            DefaultActivationResult.Builder res,
            Phase phase) {
        Map<String, Object> deps = res.resolvedDependencies();
        if (deps == null || deps.isEmpty()) {
            recordActivationEvent(Event.FINISHED, phase, res);
        } else {
            recordActivationEvent(Event.STARTING, phase, res);

            T target = Objects.requireNonNull(serviceRef.get());
            List<String> serviceTypeOrdering = serviceTypeInjectionOrder();
            LinkedHashSet<String> injections = new LinkedHashSet<>();
            serviceTypeOrdering.forEach((forServiceType) -> {
                try {
                    doInjectingFields(target, deps, injections, forServiceType);
                    doInjectingMethods(target, deps, injections, forServiceType);
                } catch (Throwable t) {
                    throw new InjectionException("failed to activate/inject: " + serviceInfoName()
                                                         + "; dependency map was: " + deps, t, this);
                }
            });

            recordActivationEvent(Event.FINISHED, phase, res);
        }
    }

    List<String> serviceTypeInjectionOrder() {
        return Collections.singletonList(serviceInfoName());
    }

    protected void doInjectingFields(
            Object target,
            Map<String, Object> deps,
            Set<String> injections,
            String forServiceType) {
        // NOP; meant to be overridden
        boolean debugMe = true;
    }

    protected void doInjectingMethods(
            Object target,
            Map<String, Object> deps,
            Set<String> injections,
            String forServiceType) {
        // NOP; meant to be overridden
        boolean debugMe = true;
    }

    protected void doPostConstructing(
            DefaultActivationResult.Builder res,
            Phase phase) {
        Optional<PostConstructMethod> postConstruct = postConstructMethod();
        if (postConstruct.isPresent()) {
            recordActivationEvent(Event.STARTING, phase, res);
            postConstruct.get().postConstruct();
            recordActivationEvent(Event.FINISHED, phase, res);
        } else {
            currentActivationPhase(res, phase);
        }
    }

    protected void doActivationFinishing(
            DefaultActivationResult.Builder res,
            Phase phase,
            boolean didSomething) {
        if (didSomething) {
            recordActivationEvent(Event.FINISHED, phase, res);
        } else {
            currentActivationPhase(res, phase);
        }
    }

    protected void doActivationActive(
            DefaultActivationResult.Builder res,
            Phase phase) {
        recordActivationEvent(Event.FINISHED, phase, res);
    }

    @Override
    public ActivationResult deactivate(
            DeActivationRequest req) {
        assert (req.serviceProvider() == this) : "not capable of handling service provider " + req;
        if (isAlreadyAtTargetPhase(Phase.INIT) || isAlreadyAtTargetPhase(Phase.DESTROYED)) {
            return ActivationResult.createSuccess(this);
        }

        PicoServices picoServices = picoServices();
        Services services = picoServices.services();

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or
        // deactivate...
        DefaultActivationLogEntry.Builder entry = toLogEntry(Event.STARTING, Phase.DESTROYED);
        entry.finishedActivationPhase(Phase.PRE_DESTROYING);
        if (log != null) {
            log.record(entry);
        }

        final PicoServicesConfig cfg = picoServices.config();
        boolean didAcquire = false;
        final DefaultActivationResult.Builder res = DefaultActivationResult.builder()
                .serviceProvider(this)
                .finishingStatus(ActivationStatus.SUCCESS)
                .startingActivationPhase(currentActivationPhase())
                .finishingActivationPhase(Phase.PRE_DESTROYING)
                .ultimateTargetActivationPhase(Phase.DESTROYED);
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)...
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                // if we couldn't grab the semaphore than we (or someone else) is busy activating this services, or
                // we deadlocked.
                return failedFinish(res, timedOutDeActivationInjectionError(entry, log), req.throwOnFailure());
            }
            didAcquire = true;

            if (!isAlreadyAtTargetPhase(Phase.ACTIVE)) {
                return res;
            }

            // if we made it to here then we "own" the semaphore and the subsequent activation steps...
            this.lastActivationThreadId = Thread.currentThread().getId();

            if (!res.finished()) {
                // quasi-reset
                currentActivationPhase(res, Phase.PRE_DESTROYING);
                doPreDestroying(res);

                serviceRef(null);

                res.wasResolved(false);
                res.resolvedDependencies(Map.of());
            }
        } catch (Throwable e) {
            return failedFinish(res, interruptedPreActivationInjectionError(entry, log, e), req.throwOnFailure());
        } finally {
            lastActivationThreadId = 0;
//            res.setFinished(true);
            if (didAcquire) {
                activationSemaphore.release();
            }
        }

        return res;
    }

    protected void doPreDestroying(
            DefaultActivationResult.Builder res) {
        Optional<PreDestroyMethod> preDestroyMethod = preDestroyMethod();
        if (preDestroyMethod.isEmpty()) {
            recordActivationEvent(Event.FINISHED, Phase.DESTROYED, res);
        } else {
            recordActivationEvent(Event.STARTING, Phase.DESTROYED, res);
            preDestroyMethod.get().preDestroy();
            recordActivationEvent(Event.FINISHED, Phase.DESTROYED, res);
        }
    }

    @Override
    public Optional<PostConstructMethod> postConstructMethod() {
        return Optional.empty();
    }

    @Override
    public Optional<PreDestroyMethod> preDestroyMethod() {
        return Optional.empty();
    }

    private DefaultActivationResult.Builder failedFinish(
            DefaultActivationResult.Builder res,
            Throwable t,
            boolean throwOnError) {
        this.lastActivationThreadId = 0;
        return finishFailedFinish(res, t, throwOnError, log);
    }

//    private static DefaultActivationResult failedFinish(
//            Throwable t,
//            boolean throwOnError) {
//        return finishFailedFinish(new DefaultActivationResult(), t, throwOnError);
//    }

    private static DefaultActivationResult.Builder finishFailedFinish(
            DefaultActivationResult.Builder res,
            Throwable t,
            boolean throwOnError,
            ActivationLog log) {
        InjectionException e;

        Throwable prev = res.error().orElse(null);
        if (prev == null || !(t instanceof InjectionException)) {
            String msg = (t != null && t.getMessage() != null) ? t.getMessage() : "failed to complete operation";
            e = new InjectionException(msg, t, res.serviceProvider(), log);
        } else {
            e = (InjectionException) t;
        }

        res.error(e);
        res.finishingStatus(ActivationStatus.FAILURE);

        if (throwOnError) {
            throw e;
        }

        return res;
    }

    private static InjectionException recursiveActivationInjectionError(
            DefaultActivationLogEntry.Builder entry,
            ActivationLog log) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("circular dependency found during activation of " + targetServiceProvider,
                                                      targetServiceProvider,
                                                      log);
        entry.error(e);
        return e;
    }

    private static InjectionException timedOutActivationInjectionError(
            DefaultActivationLogEntry.Builder entry,
            ActivationLog log) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("timed out during activation of " + targetServiceProvider,
                                                      targetServiceProvider,
                                                      log);
        entry.error(e);
        return e;
    }

    private static InjectionException timedOutDeActivationInjectionError(
            DefaultActivationLogEntry.Builder entry,
            ActivationLog log) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("timed out during deactivation of " + targetServiceProvider,
                                                      targetServiceProvider,
                                                      log);
        entry.error(e);
        return e;
    }

    private static InjectionException interruptedPreActivationInjectionError(
            DefaultActivationLogEntry.Builder entry,
            ActivationLog log,
            Throwable cause) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("circular dependency found during activation of " + targetServiceProvider,
                                                      cause,
                                                      targetServiceProvider,
                                                      log);
        entry.error(e);
        return e;
    }

    private void logMultiDefInjectionNote(
            String id,
            Object prev,
            InjectionPointInfo ipDep) {
        LOGGER.log(System.Logger.Level.DEBUG,
                   "there are two different services sharing the same injection point info id; first = "
                        + prev + " and the second = " + ipDep + "; both use the id '" + id
                        + "'; note that the second will override the first");
    }

    DefaultActivationLogEntry.Builder toLogEntry(
            Event event,
            Phase targetPhase) {
        return DefaultActivationLogEntry.builder()
                .serviceProvider(this)
                .event(event)
                .startingActivationPhase(currentActivationPhase())
                .targetActivationPhase(targetPhase)
                .finishedActivationPhase(phase);
    }

    private DefaultActivationResult.Builder currentActivationPhase(
            DefaultActivationResult.Builder resultBuilder,
            Phase phase) {
        DefaultActivationLogEntry.Builder entry = null;
        if (log != null) {
            entry = DefaultActivationLogEntry.builder()
                    .startingActivationPhase(this.phase)
                    .targetActivationPhase(phase);
        }

        this.phase = phase;
        resultBuilder.finishingActivationPhase(phase);
        if (entry != null) {
            log.record(entry);
        }
        return resultBuilder;
    }

    private void onFinished(
            DefaultActivationResult.Builder res,
            Phase finishingActivationPhase) {
        if (res.finishingActivationPhase() != finishingActivationPhase) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "short-circuiting activation into phase '"
                        + finishingActivationPhase + "' for " + this);
            }
            currentActivationPhase(res, finishingActivationPhase);
        }
        res.finishingStatus(ActivationStatus.SUCCESS);
    }

    private InjectionException managedServiceInstanceShouldHaveBeenSetException() {
        return new InjectionException("managed service instance expected to have been set", this, activationLog());
    }

    private InjectionException expectedQualifiedServiceError(ContextualServiceQuery ctx) {
        return new InjectionException("expected to return a non-null instance for: " + ctx.injectionPointInfo()
                                              + "; with criteria matching: " + ctx.serviceInfoCriteria(), this, activationLog());
    }

    private InjectionException activationFailed(ActivationResult res) {
        return new InjectionException("activation failed: " + res, this, activationLog());
    }

    private PicoServiceProviderException unableToActivate(Throwable cause) {
        return new PicoServiceProviderException("unable to activate: " + description(), cause, this);
    }

    private PicoServiceProviderException alreadyInitialized() {
        throw new PicoServiceProviderException("already initialized", this);
    }

}
