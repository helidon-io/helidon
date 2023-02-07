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

import io.helidon.builder.types.DefaultTypeName;
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
    private static final DependenciesInfo NO_DEPS = DefaultDependenciesInfo.builder().build();
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
        onInitialized();
    }

    /**
     * The logger.
     *
     * @return the logger
     */
    protected System.Logger logger() {
        return LOGGER;
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
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return Optional.of(this);
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
     * of code-generation, and the default is to return false.
     *
     * @return true if a custom, user-supplied implementation (rare)
     */
    public boolean isCustom() {
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
        if (this.picoServices != null) {
            throw alreadyInitialized();
        }
        this.serviceInfo = serviceInfo;
    }

    @Override
    public DependenciesInfo dependencies() {
        return (dependencies == null) ? NO_DEPS : dependencies;
    }

    /**
     * Used to set the dependencies from this service provider.
     *
     * @param dependencies the dependencies from this service provider
     */
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

    /**
     * Returns true if the current activation phase has reached the given target phase.
     *
     * @param targetPhase the target phase
     * @return true if the targetPhase has been reached
     */
    protected boolean isAlreadyAtTargetPhase(
            Phase targetPhase) {
        Objects.requireNonNull(targetPhase);
        return (currentActivationPhase() == targetPhase);
    }

    /**
     * Used to access the current pico services instance assigned to this service provider.
     *
     * @return the pico services assigned to this service provider
     */
    public PicoServices picoServices() {
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
        if (this.picoServices != null) {
            onInitialized();
        }
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
        if (this.interceptor != null || activationSemaphore.availablePermits() == 0 || phase != Phase.INIT) {
            throw alreadyInitialized();
        }
        this.interceptor = interceptor;
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
        return name(true) + identitySuffix() + ":" + currentActivationPhase();
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
    protected Optional<ActivationLog> activationLog() {
        if (log == null && picoServices != null) {
            log = picoServices.activationLog().orElse(DefaultActivationLog.createUnretainedLog(logger()));
        }
        return Optional.ofNullable(log);
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
     * Called by the generated code when it is attempting to resolve a specific injection point dependency by id.
     *
     * @param deps  the entire map of resolved dependencies
     * @param id    the id of the dependency to lookup
     * @return      the resolved object
     * @param <T> the type of the dependency
     */
    protected <T> T get(
            Map<String, T> deps, String id) {
        return Objects.requireNonNull(deps.get(id), "'" + id + "' expected to have been found in: " + deps.keySet());
    }

    /**
     * Will trigger an activation if the managed service is not yet active.
     *
     * @param ctx the context that triggered the activation
     * @return the result of the activation
     */
    protected Optional<T> maybeActivate(
            ContextualServiceQuery ctx) {
        Objects.requireNonNull(ctx);

        try {
            T serviceOrProvider = serviceRef.get();

            if (serviceOrProvider == null
                    || Phase.ACTIVE != currentActivationPhase()) {
                ActivationRequest req = ActivationRequest.DEFAULT.get();
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
            if (res.targetActivationPhase().ordinal() >= Phase.ACTIVATION_STARTING.ordinal()
                    && (Phase.INIT == res.finishingActivationPhase()
                             || Phase.PENDING == res.finishingActivationPhase()
                             || Phase.ACTIVATION_STARTING == res.finishingActivationPhase()
                             || Phase.DESTROYED == res.finishingActivationPhase())) {
                doStartingLifecycle(logEntryAndResult);
            }
            if (res.targetActivationPhase().ordinal() >= Phase.GATHERING_DEPENDENCIES.ordinal()
                    && (Phase.ACTIVATION_STARTING == res.finishingActivationPhase())) {
                doGatheringDependencies(logEntryAndResult);
            }
            if (res.targetActivationPhase().ordinal() >= Phase.CONSTRUCTING.ordinal()
                    && (Phase.GATHERING_DEPENDENCIES == res.finishingActivationPhase())) {
                doConstructing(logEntryAndResult);
            }
            if (res.targetActivationPhase().ordinal() >= Phase.INJECTING.ordinal()
                    && (Phase.CONSTRUCTING == res.finishingActivationPhase())) {
                doInjecting(logEntryAndResult);
            }
            if (res.targetActivationPhase().ordinal() >= Phase.POST_CONSTRUCTING.ordinal()
                    && (Phase.INJECTING == res.finishingActivationPhase())) {
                doPostConstructing(logEntryAndResult);
            }
            if (res.targetActivationPhase().ordinal() >= Phase.ACTIVATION_FINISHING.ordinal()
                    && (Phase.POST_CONSTRUCTING == res.finishingActivationPhase())) {
                doActivationFinishing(logEntryAndResult);
            }
            if (res.targetActivationPhase().ordinal() >= Phase.ACTIVE.ordinal()
                    && (Phase.ACTIVATION_FINISHING == res.finishingActivationPhase())) {
                doActivationActive(logEntryAndResult);
            }

            onFinished(logEntryAndResult);
        } catch (Throwable t) {
            onFailedFinish(logEntryAndResult, t, req.throwIfError());
        } finally {
            this.lastActivationThreadId = 0;
            activationSemaphore.release();
        }

        return logEntryAndResult.activationResult.build();
    }

    // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate
    private LogEntryAndResult preambleActivate(
            ActivationRequest req) {
        assert (picoServices != null) : "not initialized";

        LogEntryAndResult logEntryAndResult = createLogEntryAndResult(req.targetPhase());
        req.injectionPoint().ifPresent(logEntryAndResult.logEntry::injectionPoint);
        Phase startingPhase = req.startingPhase().orElse(Phase.PENDING);
        startTransitionCurrentActivationPhase(logEntryAndResult, startingPhase);

        // fail fast if we are in a recursive situation on this thread...
        if (logEntryAndResult.logEntry.threadId() == lastActivationThreadId && lastActivationThreadId > 0) {
            onFailedFinish(logEntryAndResult, recursiveActivationInjectionError(logEntryAndResult.logEntry), req.throwIfError());
            return logEntryAndResult;
        }

        PicoServicesConfig cfg = picoServices.config();
        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                // if we couldn't get semaphore than we (or someone else) is busy activating this services, or we deadlocked
                onFailedFinish(logEntryAndResult,
                               timedOutActivationInjectionError(logEntryAndResult.logEntry),
                               req.throwIfError());
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
            onFailedFinish(logEntryAndResult, e, req.throwIfError());
        }

        return logEntryAndResult;
    }

    @Override
    public void onPhaseEvent(
            Event event,
            Phase phase) {
        // NOP
        int debugMe = 0;
    }

    private void onInitialized() {
        if (logger().isLoggable(System.Logger.Level.DEBUG)) {
            logger().log(System.Logger.Level.DEBUG, this + " initialized.");
        }
    }

    /**
     * Called on a successful finish of activation.
     *
     * @param logEntryAndResult the record holding the result
     * @see #onFailedFinish(io.helidon.pico.services.AbstractServiceProvider.LogEntryAndResult, Throwable, boolean)
     */
    protected void onFinished(
            LogEntryAndResult logEntryAndResult) {
        // NOP;
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
            logger().log(System.Logger.Level.WARNING,
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
                InjectionPlan plan = createBuilder(id)
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
                InjectionPlan plan = createBuilder(id)
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
                                                         + this, null, self);
                }

                if ((self.injectionPlan != null) && !self.injectionPlan.equals(injectionPlan)) {
                    throw new InjectionException("injection plan has already been bound for "
                                                         + this, null, self);
                }
                self.injectionPlan = injectionPlan;
            }

            private InjectionPointInfo safeGetIpInfo(String id) {
                InjectionPointInfo ipInfo = idToIpInfo.remove(id);
                if (Objects.isNull(ipInfo)) {
                    throw new InjectionException("expected to find a dependency for '" + id + "' from "
                                                         + this + " in " + idToIpInfo, null, self);
                }
                return ipInfo;
            }

            private DefaultInjectionPlan.Builder createBuilder(
                    String id) {
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
        Map<String, Object> deps = resolveDependencies(plans);
        if (!deps.isEmpty()) {
            logEntryAndResult.activationResult.resolvedDependencies(deps);
        }
        logEntryAndResult.activationResult.injectionPlans(plans);

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    /**
     * Get or Create the injection plan.
     *
     * @param resolveIps true if the injection points should also be activated/resolved.
     * @return the injection plan
     */
    public Map<String, InjectionPlan> getOrCreateInjectionPlan(
            boolean resolveIps) {
        if (this.injectionPlan != null) {
            return this.injectionPlan;
        }

        if (this.dependencies == null) {
            dependencies(dependencies());
        }

        final Map<String, InjectionPlan> plan =
                DefaultInjectionPlans.createInjectionPlans(picoServices(), this, dependencies, resolveIps, logger());
        assert (this.injectionPlan == null);
        this.injectionPlan = Objects.requireNonNull(plan);

        return this.injectionPlan;
    }

    @Override
    public boolean reset(
            boolean deep) {
        Object service = serviceRef.get();
        boolean result = false;
        boolean didAcquire = false;
        try {
            didAcquire = activationSemaphore.tryAcquire(1, TimeUnit.MILLISECONDS);

            if (service != null) {
                logger().log(System.Logger.Level.INFO, "resetting " + this);
                if (deep && service instanceof Resetable) {
                    try {
                        if (((Resetable) service).reset(deep)) {
                            result = true;
                        }
                    } catch (Throwable t) {
                        logger().log(System.Logger.Level.WARNING, "unable to reset: " + this, t); // eat it
                    }
                }
            }

            if (deep) {
                injectionPlan = null;
                interceptor = null;
                picoServices = null;
                serviceRef.set(null);
                phase = Phase.INIT;

                result = true;
            }
        } catch (Exception e) {
            if (didAcquire) {
                throw new PicoServiceProviderException("unable to reset", e, this);
            } else {
                throw new PicoServiceProviderException("unable to reset during activation", e, this);
            }
        } finally {
            if (didAcquire) {
                activationSemaphore.release();
            }
        }

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
            Map<String, InjectionPlan> mutablePlans) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map.copyOf(mutablePlans).forEach((key, value) -> {
            Object resolved;
            if (value.wasResolved()) {
                resolved = value.resolved();
                result.put(key, resolveOptional(value, resolved));
            } else {
                List<ServiceProvider<?>> serviceProviders = value.injectionPointQualifiedServiceProviders();
                serviceProviders = (serviceProviders == null)
                        ? Collections.emptyList()
                        : Collections.unmodifiableList(serviceProviders);
                if (serviceProviders.isEmpty()
                        && !value.unqualifiedProviders().isEmpty()) {
                    resolved = List.of(); // deferred
                } else {
                    resolved = DefaultInjectionPlans.resolve(this, value.injectionPointInfo(), serviceProviders, logger());
                }
                result.put(key, resolveOptional(value, resolved));
            }

            if (value.resolved().isEmpty()) {
                // update the original plans map to properly reflect the resolved value
                mutablePlans.put(key, DefaultInjectionPlan.toBuilder(value)
                        .wasResolved(true)
                        .resolved(resolved)
                        .build());
            }
        });

        return result;
    }

    @SuppressWarnings("unchecked")
    private Object resolveOptional(
            InjectionPlan plan,
            Object resolved) {
        if (!plan.injectionPointInfo().optionalWrapped() && resolved instanceof Optional) {
            return ((Optional<Object>) resolved).orElse(null);
        }
        return resolved;
    }

    /**
     * Called during construction phase.
     *
     * @param logEntryAndResult the record that holds the results
     */
    protected void doConstructing(
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

    private void doInjecting(
            LogEntryAndResult logEntryAndResult) {
        if (!Utils.isQualifiedInjectionTarget(this)) {
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

    /**
     * Used to control the order of injection. Jsr-330 is particular about this.
     *
     * @return the order of injection
     */
    protected List<String> serviceTypeInjectionOrder() {
        return Collections.singletonList(serviceInfo.serviceTypeName());
    }

    /**
     * Called during the injection of fields.
     *
     * @param target            the target
     * @param deps              the dependencies
     * @param injections        the injections
     * @param forServiceType    the service type
     */
    protected void doInjectingFields(
            Object target,
            Map<String, Object> deps,
            Set<String> injections,
            String forServiceType) {
        // NOP; meant to be overridden
        boolean debugMe = true;
    }

    /**
     * Called during the injection of methods.
     *
     * @param target            the target
     * @param deps              the dependencies
     * @param injections        the injections
     * @param forServiceType    the service type
     */
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
                onFailedFinish(logEntryAndResult, e, req.throwIfError());
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
            onFailedFinish(logEntryAndResult, e, req.throwIfError());
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

    /**
     * Called on a failed finish of activation.
     *
     * @param logEntryAndResult the log entry holding the result
     * @param t the error that was observed
     * @param throwOnError the flag indicating whether we should throw on error
     * @see #onFinished(io.helidon.pico.services.AbstractServiceProvider.LogEntryAndResult)
     */
    protected void onFailedFinish(
            LogEntryAndResult logEntryAndResult,
            Throwable t,
            boolean throwOnError) {
        this.lastActivationThreadId = 0;
        onFailedFinish(logEntryAndResult, t, throwOnError, activationLog());
    }

    void onFailedFinish(
            LogEntryAndResult logEntryAndResult,
            Throwable t,
            boolean throwOnError,
            Optional<ActivationLog> log) {
        InjectionException e;

        DefaultActivationLogEntry.Builder res = logEntryAndResult.logEntry;
        Throwable prev = res.error().orElse(null);
        if (prev == null || !(t instanceof InjectionException)) {
            String msg = (t != null && t.getMessage() != null) ? t.getMessage() : "failed to complete operation";
            e = new InjectionException(msg, t, this).activationLog(log);
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
                                                      targetServiceProvider).activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException timedOutActivationInjectionError(
            DefaultActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("timed out during activation of " + targetServiceProvider,
                                                      targetServiceProvider).activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException timedOutDeActivationInjectionError(
            DefaultActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("timed out during deactivation of " + targetServiceProvider,
                                                      targetServiceProvider).activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException interruptedPreActivationInjectionError(
            DefaultActivationLogEntry.Builder entry,
            Throwable cause) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        InjectionException e = new InjectionException("circular dependency found during activation of " + targetServiceProvider,
                                                      cause, targetServiceProvider).activationLog(activationLog());
        entry.error(e);
        return e;
    }

    private InjectionException managedServiceInstanceShouldHaveBeenSetException() {
        return new InjectionException("managed service instance expected to have been set", this)
                .activationLog(activationLog());
    }

    /**
     * Creates an injection exception appropriate when there are no matching qualified services for the context provided.
     *
     * @param ctx the context
     * @return the injection exception
     */
    protected InjectionException expectedQualifiedServiceError(
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
        return new PicoServiceProviderException("unable to activate: " + this, cause, this);
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
            logger().log(System.Logger.Level.DEBUG, message);
        }
    }

    /**
     * Creates a log entry result based upon the target phase provided.
     *
     * @param targetPhase the target phase
     * @return a new log entry and result record
     */
    protected LogEntryAndResult createLogEntryAndResult(
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

    /**
     * Starts transitioning to a new phase.
     *
     * @param logEntryAndResult the record that will hold the state of the transition
     * @param newPhase the target new phase
     */
    protected void startTransitionCurrentActivationPhase(
            LogEntryAndResult logEntryAndResult,
            Phase newPhase) {
        Objects.requireNonNull(logEntryAndResult);
        Objects.requireNonNull(newPhase);
        logEntryAndResult.activationResult
                .finishingActivationPhase(newPhase);
        this.phase = newPhase;
        logEntryAndResult.logEntry
                .event(Event.STARTING)
                .activationResult(logEntryAndResult.activationResult.build());
        ActivationLog log = activationLog().orElse(null);
        if (log != null) {
            log.record(logEntryAndResult.logEntry.build());
        }
        onPhaseEvent(Event.STARTING, this.phase);
    }

    void finishedTransitionCurrentActivationPhase(
            LogEntryAndResult logEntryAndResult) {
        logEntryAndResult.logEntry
                .event(Event.FINISHED)
                .activationResult(logEntryAndResult.activationResult.build());
        ActivationLog log = activationLog().orElse(null);
        if (log != null) {
            log.record(logEntryAndResult.logEntry.build());
        }
        onPhaseEvent(Event.FINISHED, this.phase);
    }

    /**
     * Will test and downcast the passed service provider to an instance of
     * {@link io.helidon.pico.services.AbstractServiceProvider}.
     *
     * @param sp        the service provider
     * @param expected  is the result expected to be present
     * @return          the abstract service provider
     * @param <T>       the managed service type
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<AbstractServiceProvider<T>> toAbstractServiceProvider(
            ServiceProvider<?> sp,
            boolean expected) {
        if (!(sp instanceof AbstractServiceProvider)) {
            if (expected) {
                throw new IllegalStateException("expected provider to be of type " + AbstractServiceProvider.class.getName());
            }
            return Optional.empty();
        }
        return Optional.of((AbstractServiceProvider<T>) sp);
    }

    /**
     * Represents a result of a phase transition.
     *
     * @see #createLogEntryAndResult(io.helidon.pico.Phase)
     */
    // note that for one result, there may be N logEntry records we will build and write to the log
    protected static class LogEntryAndResult {
        private final DefaultActivationResult.Builder activationResult;
        private final DefaultActivationLogEntry.Builder logEntry;

        LogEntryAndResult(
                DefaultActivationLogEntry.Builder logEntry,
                DefaultActivationResult.Builder activationResult) {
            this.logEntry = logEntry;
            this.activationResult = activationResult;
        }

        DefaultActivationResult.Builder activationResult() {
            return activationResult;
        }

        DefaultActivationLogEntry.Builder logEntry() {
            return logEntry;
        }
    }

}
