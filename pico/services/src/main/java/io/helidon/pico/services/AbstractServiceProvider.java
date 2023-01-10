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
import io.helidon.pico.Application;
import io.helidon.pico.ContextualServiceQuery;
import io.helidon.pico.DeActivationRequest;
import io.helidon.pico.DeActivator;
import io.helidon.pico.DefaultActivationLogEntry;
import io.helidon.pico.DefaultActivationResult;
import io.helidon.pico.DefaultDependenciesInfo;
import io.helidon.pico.DefaultInjectionPointInfo;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.DefaultServiceInfoCriteria;
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
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceInjectionPlanBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.spi.InjectionResolver;
import io.helidon.pico.spi.Resetable;
import io.helidon.pico.types.DefaultTypeName;

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
    private static final DependenciesInfo NO_DEPS = DefaultDependenciesInfo.builder().build();

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
    protected AbstractServiceProvider() {
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
        if (instance != null) {
            this.serviceRef.set(instance);
            this.phase = (phase != null) ? phase : Phase.ACTIVE;
        }
        this.serviceInfo = DefaultServiceInfo.toBuilder(serviceInfo).build();
        this.picoServices = Objects.requireNonNull(picoServices);
        this.log = picoServices.activationLog().orElseThrow();
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

//    /**
//     * Identifies whether the implementation was custom written and not code generated. We assume by default this is part
//     * of code-generation, and the return defaulting to false.
//     *
//     * @return true if a custom, user-supplied implementation (rare)
//     */
//    protected boolean isCustom() {
//        return false;
//    }

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
        return (dependencies == null) ? NO_DEPS : dependencies;
    }

    protected void dependencies(
            DependenciesInfo dependencies) {
        Objects.requireNonNull(dependencies);
        if (this.dependencies != null) {
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
        return System.identityHashCode(serviceInfo.serviceTypeName());
    }

    @Override
    public boolean equals(
            Object another) {
        return (another instanceof ServiceProvider)
                && id().equals(((ServiceProvider<?>) another).id())
                && serviceInfo().equals(((ServiceProvider<?>) another).serviceInfo());
    }

    @Override
    public String toString() {
        return description();
    }

    @Override
    public String description() {
        return name(true) + ":" + currentActivationPhase();
    }

    @Override
    public String id() {
        return identityPrefix() + name(false) + identitySuffix();
    }

    /**
     * The name assigned to this provider. Simple names are not unique.
     *
     * @param simple flag to indicate simple name usage
     * @return this name assigned to this provider
     */
    protected String name(
            boolean simple) {
        String name = serviceInfo.serviceTypeName();
        return (simple) ? DefaultTypeName.createFromTypeName(name).className() : name;
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
        assert (picoServices != null) : "not initialized";
        if (log == null) {
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

        LogEntryAndResult logEntryAndResult = preambleActivate(req);
        DefaultActivationResult.Builder res = logEntryAndResult.activationResult;

        // if we get here then we own the semaphore for activation...
        try {
            if (Phase.INIT == res.finishingActivationPhase()
                    || Phase.PENDING == res.finishingActivationPhase()
                    || Phase.DESTROYED == res.finishingActivationPhase()) {
                doStartingLifecycle(logEntryAndResult);
            }
            if (Phase.ACTIVATION_STARTING == res.finishingActivationPhase()) {
                doGatheringDependencies(logEntryAndResult);
            }
            if (Phase.GATHERING_DEPENDENCIES == res.finishingActivationPhase()) {
                doConstructing(logEntryAndResult);
            }
            if (Phase.CONSTRUCTING == res.finishingActivationPhase()) {
                doInjecting(logEntryAndResult);
            }
            if (Phase.INJECTING == res.finishingActivationPhase()) {
                doPostConstructing(logEntryAndResult);
            }
            if (Phase.POST_CONSTRUCTING == res.finishingActivationPhase()) {
                doActivationFinishing(logEntryAndResult);
            }
            if (Phase.ACTIVATION_FINISHING == res.finishingActivationPhase()) {
                doActivationActive(logEntryAndResult);
            }
        } catch (Throwable t) {
            failedFinish(logEntryAndResult, t, req.throwOnFailure());
        } finally {
            this.lastActivationThreadId = 0;
            activationSemaphore.release();
        }

        return logEntryAndResult.activationResult.build();
    }

    // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate
    private LogEntryAndResult preambleActivate(
            ActivationRequest req) {
        assert (req.serviceProvider() == this) : "not capable of handling service provider" + req;
        assert (picoServices != null) : "not initialized";

        LogEntryAndResult logEntryAndResult = createLogEntryAndResult(req.targetPhase());
        req.injectionPoint().ifPresent(logEntryAndResult.logEntry::injectionPoint);
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PENDING);

        // fail fast if we are in a recursive situation on this thread...
        if (logEntryAndResult.logEntry.threadId() == lastActivationThreadId && lastActivationThreadId > 0) {
            failedFinish(logEntryAndResult, recursiveActivationInjectionError(logEntryAndResult.logEntry), req.throwOnFailure());
            return logEntryAndResult;
        }

        PicoServicesConfig cfg = picoServices.config();
        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                // if we couldn't get semaphore than we (or someone else) is busy activating this services, or we deadlocked
                failedFinish(logEntryAndResult, timedOutActivationInjectionError(logEntryAndResult.logEntry), req.throwOnFailure());
                return logEntryAndResult;
            }
            didAcquire = true;

            // if we made it to here then we "own" the semaphore and the subsequent activation steps...
            lastActivationThreadId = Thread.currentThread().getId();
            logEntryAndResult.logEntry.threadId(lastActivationThreadId);

            if (logEntryAndResult.activationResult.finished()) {
                didAcquire = false;
                activationSemaphore.release();
            }

            finishedTransitionCurrentActivationPhase(logEntryAndResult);
        } catch (Throwable t) {
            this.lastActivationThreadId = 0;
            if (didAcquire) {
                activationSemaphore.release();
            }

            InjectionException e = interruptedPreActivationInjectionError(logEntryAndResult.logEntry, t);
            failedFinish(logEntryAndResult, e, req.throwOnFailure());
        }

        return logEntryAndResult;
    }

    @Override
    public void onPhaseEvent(
            Event event,
            Phase phase) {
        // NOP
    }

    private void doStartingLifecycle(
            LogEntryAndResult logEntryAndResult) {
        startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_STARTING);
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

        ConcurrentHashMap<String, InjectionPointInfo> idToIpInfo = new ConcurrentHashMap<>();
        dependencies.allDependencies().forEach(dep ->
            dep.injectionPointDependencies().forEach(ipDep -> {
                String id = ipDep.id();
                InjectionPointInfo prev = idToIpInfo.put(id, ipDep);
                if (prev != null
                        && !prev.equals(ipDep)
                        && !prev.dependencyToServiceInfo().equals(ipDep.dependencyToServiceInfo())) {
                    logMultiDefInjectionNote(id, prev, ipDep);
                }
            }));

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
                ServiceInfoCriteria serviceInfo = DefaultServiceInfoCriteria.builder()
                        .serviceTypeName(serviceType.getName())
                        .build();
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

    private void doGatheringDependencies(
            LogEntryAndResult logEntryAndResult) {
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.GATHERING_DEPENDENCIES);

        Map<String, InjectionPlan> plans = Objects.requireNonNull(getOrCreateInjectionPlan(false));
        logEntryAndResult.activationResult.injectionPlans(plans);

        Map<String, Object> deps = resolveDependencies(plans);
        if (deps != null) {
            logEntryAndResult.activationResult.resolvedDependencies(deps);
        }

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
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
                DefaultInjectionPlans.createInjectionPlans(picoServices(), this, dependencies, resolveIps, LOGGER);
        assert (injectionPlan == null);
        injectionPlan = Objects.requireNonNull(plan);

        return injectionPlan;
    }

    @Override
    public boolean reset(boolean deep) {
        Object service = serviceRef.get();
        boolean result = (service != null);
        if (service != null) {
            LOGGER.log(System.Logger.Level.INFO, "resetting " + this);
            if (service instanceof Resetable) {
                try {
                    result = ((Resetable) service).reset(deep);
                } catch (Throwable t) {
                    LOGGER.log(System.Logger.Level.WARNING, "unable to reset: " + this, t);
                }
            }
        }

        if (deep) {
            injectionPlan = null;
            interceptor = null;
            picoServices = null;
            result = true;
        }

        serviceRef.set(null);
        phase = Phase.INIT;
        return result;
    }

    @Override
    public Optional<PostConstructMethod> postConstructMethod() {
        return Optional.empty();
    }

    @Override
    public Optional<PreDestroyMethod> preDestroyMethod() {
        return Optional.empty();
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
                    resolved = DefaultInjectionPlans.resolve(this, value.injectionPointInfo(), serviceProviders, LOGGER);
                }
                result.put(key, resolved);
            }
        });

        return result;
    }

    private void doConstructing(
            LogEntryAndResult logEntryAndResult) {
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.CONSTRUCTING);

        Map<String, Object> deps = logEntryAndResult.activationResult.resolvedDependencies();
        serviceRef(createServiceProvider(deps));

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    void serviceRef(
            T instance) {
        serviceRef.set(instance);
    }

    /**
     * Creates the service with the supplied resolved dependencies, key'ed by each injection point id.
     *
     * @param resolvedDeps the resolved dependencies
     * @return the newly created managed service
     */
    protected T createServiceProvider(
            Map<String, Object> resolvedDeps) {
        throw new InjectionException("don't know how to create an instance of " + serviceInfo(), this)
                .activationLog(activationLog());
    }

    static boolean isQualifiedInjectionTarget(
            ServiceProvider<?> sp) {
        ServiceInfo serviceInfo = sp.serviceInfo();
        Set<String> contractsImplemented = serviceInfo.contractsImplemented();
        return !contractsImplemented.isEmpty()
                && !contractsImplemented.contains(io.helidon.pico.Module.class.getName())
                && !contractsImplemented.contains(Application.class.getName());
    }

    private void doInjecting(
            LogEntryAndResult logEntryAndResult) {
        if (!isQualifiedInjectionTarget(this)) {
            startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.INJECTING);
            return;
        }

        Map<String, Object> deps = logEntryAndResult.activationResult.resolvedDependencies();
        if (deps == null || deps.isEmpty()) {
            startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.INJECTING);
            return;
        }

        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.INJECTING);

        T target = Objects.requireNonNull(serviceRef.get());
        List<String> serviceTypeOrdering = serviceTypeInjectionOrder();
        LinkedHashSet<String> injections = new LinkedHashSet<>();
        serviceTypeOrdering.forEach((forServiceType) -> {
            try {
                doInjectingFields(target, deps, injections, forServiceType);
                doInjectingMethods(target, deps, injections, forServiceType);
            } catch (Throwable t) {
                throw new InjectionException("failed to activate/inject: " + this
                                                     + "; dependency map was: " + deps, t, this);
            }
        });

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    protected List<String> serviceTypeInjectionOrder() {
        return Collections.singletonList(serviceInfo.serviceTypeName());
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

    void doPostConstructing(
            LogEntryAndResult logEntryAndResult) {
        Optional<PostConstructMethod> postConstruct = postConstructMethod();
        if (postConstruct.isPresent()) {
            startTransitionCurrentActivationPhase(logEntryAndResult, Phase.POST_CONSTRUCTING);
            postConstruct.get().postConstruct();
            finishedTransitionCurrentActivationPhase(logEntryAndResult);
        } else {
            startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.POST_CONSTRUCTING);
        }
    }

    private void doActivationFinishing(
            LogEntryAndResult logEntryAndResult) {
        startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_FINISHING);
    }

    private void doActivationActive(
            LogEntryAndResult logEntryAndResult) {
        startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVE);
    }

    @Override
    public ActivationResult deactivate(
            DeActivationRequest req) {
        assert (req.serviceProvider() == this) : "not capable of handling service provider " + req;
        if (!currentActivationPhase().eligibleForDeactivation()) {
            return ActivationResult.createSuccess(this);
        }

        PicoServices picoServices = picoServices();
        PicoServicesConfig cfg = picoServices.config();

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or
        // deactivate...
        LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.DESTROYED);
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PRE_DESTROYING);

        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)...
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                // if we couldn't grab the semaphore than we (or someone else) is busy activating this services, or
                // we deadlocked.
                InjectionException e = timedOutDeActivationInjectionError(logEntryAndResult.logEntry);
                failedFinish(logEntryAndResult, e, req.throwOnFailure());
                return logEntryAndResult.activationResult.build();
            }
            didAcquire = true;

            // if we made it to here then we "own" the semaphore and the subsequent activation steps...
            this.lastActivationThreadId = Thread.currentThread().getId();

            doPreDestroying(logEntryAndResult);
            if (Phase.PRE_DESTROYING == logEntryAndResult.activationResult.finishingActivationPhase()) {
                doDestroying(logEntryAndResult);
            }
        } catch (Throwable t) {
            InjectionException e = interruptedPreActivationInjectionError(logEntryAndResult.logEntry, t);
            failedFinish(logEntryAndResult, e, req.throwOnFailure());
        } finally {
            lastActivationThreadId = 0;
            //            res.setFinished(true);
            if (didAcquire) {
                activationSemaphore.release();
            }
        }

        return logEntryAndResult.activationResult.build();
    }

    private void doPreDestroying(
            LogEntryAndResult logEntryAndResult) {
        Optional<PreDestroyMethod> preDestroyMethod = preDestroyMethod();
        if (preDestroyMethod.isEmpty()) {
            startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.PRE_DESTROYING);
        } else {
            startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PRE_DESTROYING);
            preDestroyMethod.get().preDestroy();
            finishedTransitionCurrentActivationPhase(logEntryAndResult);
        }
    }

    private void doDestroying(
            LogEntryAndResult logEntryAndResult) {
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.DESTROYED);
        logEntryAndResult.activationResult.wasResolved(false);
        logEntryAndResult.activationResult.resolvedDependencies(Map.of());
        serviceRef(null);
        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    private void failedFinish(
            LogEntryAndResult logEntryAndResult,
            Throwable t,
            boolean throwOnError) {
        this.lastActivationThreadId = 0;
        failedFinish(logEntryAndResult, t, throwOnError, activationLog());
    }

    void failedFinish(
            LogEntryAndResult logEntryAndResult,
            Throwable t,
            boolean throwOnError,
            ActivationLog log) {
        InjectionException e;

        DefaultActivationLogEntry.Builder res = logEntryAndResult.logEntry;
        Throwable prev = res.error().orElse(null);
        if (prev == null || !(t instanceof InjectionException)) {
            String msg = (t != null && t.getMessage() != null) ? t.getMessage() : "failed to complete operation";
            e = new InjectionException(msg, t, this)
                    .activationLog(log);
        } else {
            e = (InjectionException) t;
        }

        res.error(e);
        logEntryAndResult.activationResult.finishingStatus(ActivationStatus.FAILURE);

        if (throwOnError) {
            throw e;
        }
    }

    private InjectionException recursiveActivationInjectionError(
            DefaultActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("circular dependency found during activation of " + targetServiceProvider,
                                                      targetServiceProvider)
                .activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException timedOutActivationInjectionError(
            DefaultActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("timed out during activation of " + targetServiceProvider,
                                                      targetServiceProvider)
                .activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException timedOutDeActivationInjectionError(
            DefaultActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("timed out during deactivation of " + targetServiceProvider,
                                                      targetServiceProvider)
                .activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException interruptedPreActivationInjectionError(
            DefaultActivationLogEntry.Builder entry,
            Throwable cause) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("circular dependency found during activation of " + targetServiceProvider,
                                                      cause, targetServiceProvider)
                .activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException managedServiceInstanceShouldHaveBeenSetException() {
        return new InjectionException("managed service instance expected to have been set", this)
                .activationLog(activationLog());
    }

    private InjectionException expectedQualifiedServiceError(
            ContextualServiceQuery ctx) {
        return new InjectionException("expected to return a non-null instance for: " + ctx.injectionPointInfo()
                                              + "; with criteria matching: " + ctx.serviceInfoCriteria(), this)
                .activationLog(activationLog());
    }

    private InjectionException activationFailed(
            ActivationResult res) {
        return new InjectionException("activation failed: " + res, this)
                .activationLog(activationLog());
    }

    private PicoServiceProviderException unableToActivate(
            Throwable cause) {
        return new PicoServiceProviderException("unable to activate: " + description(), cause, this);
    }

    private PicoServiceProviderException alreadyInitialized() {
        throw new PicoServiceProviderException("already initialized", this);
    }

    private void logMultiDefInjectionNote(
            String id,
            Object prev,
            InjectionPointInfo ipDep) {
        String message = "there are two different services sharing the same injection point info id; first = "
                + prev + " and the second = " + ipDep + "; both use the id '" + id
                + "'; note that the second will override the first";
        if (log != null) {
            log.record(DefaultActivationLogEntry.builder()
                               .serviceProvider(this)
                               .injectionPoint(ipDep)
                               .message(message)
                               .build());
        } else {
            LOGGER.log(System.Logger.Level.DEBUG, message);
        }
    }

    LogEntryAndResult createLogEntryAndResult(
            Phase targetPhase) {
        Phase currentPhase = currentActivationPhase();
        DefaultActivationResult.Builder activationResult = DefaultActivationResult.builder()
                .serviceProvider(this)
                .startingActivationPhase(currentPhase)
                .finishingActivationPhase(currentPhase)
                .targetActivationPhase(targetPhase);
        DefaultActivationLogEntry.Builder logEntry = DefaultActivationLogEntry.builder()
                .serviceProvider(this)
                .event(Event.STARTING)
                .threadId(Thread.currentThread().getId())
                .activationResult(activationResult);
        return new LogEntryAndResult(logEntry, activationResult);
    }

    void startAndFinishTransitionCurrentActivationPhase(
            LogEntryAndResult logEntryAndResult,
            Phase newPhase) {
        startTransitionCurrentActivationPhase(logEntryAndResult, newPhase);
        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    void startTransitionCurrentActivationPhase(
            LogEntryAndResult logEntryAndResult,
            Phase newPhase) {
        Objects.requireNonNull(newPhase);
        logEntryAndResult.activationResult
                .finishingActivationPhase(newPhase);
        this.phase = newPhase;
        logEntryAndResult.logEntry
                .event(Event.STARTING)
                .activationResult(logEntryAndResult.activationResult.build());
        activationLog().record(logEntryAndResult.logEntry.build());
        onPhaseEvent(Event.STARTING, this.phase);
    }

    void finishedTransitionCurrentActivationPhase(
            LogEntryAndResult logEntryAndResult) {
        logEntryAndResult.logEntry
                .event(Event.FINISHED)
                .activationResult(logEntryAndResult.activationResult.build());
        activationLog().record(logEntryAndResult.logEntry.build());
        onPhaseEvent(Event.FINISHED, this.phase);
    }


    // note that for one result, there may be N logEntry records we will build and write to the log
    static class LogEntryAndResult {
        final DefaultActivationResult.Builder activationResult;
        final DefaultActivationLogEntry.Builder logEntry;

        LogEntryAndResult(
                DefaultActivationLogEntry.Builder logEntry,
                DefaultActivationResult.Builder activationResult) {
            this.logEntry = logEntry;
            this.activationResult = activationResult;
        }
    }

}
