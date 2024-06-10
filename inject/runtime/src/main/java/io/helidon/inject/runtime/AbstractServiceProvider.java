/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.runtime;

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

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.ActivationLog;
import io.helidon.inject.api.ActivationLogEntry;
import io.helidon.inject.api.ActivationPhaseReceiver;
import io.helidon.inject.api.ActivationRequest;
import io.helidon.inject.api.ActivationResult;
import io.helidon.inject.api.ActivationStatus;
import io.helidon.inject.api.Activator;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.DeActivationRequest;
import io.helidon.inject.api.DeActivator;
import io.helidon.inject.api.DependenciesInfo;
import io.helidon.inject.api.ElementKind;
import io.helidon.inject.api.Event;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.InjectionServiceProviderException;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.InjectionServicesConfig;
import io.helidon.inject.api.Phase;
import io.helidon.inject.api.PostConstructMethod;
import io.helidon.inject.api.PreDestroyMethod;
import io.helidon.inject.api.Resettable;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceInjectionPlanBinder;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.ServiceProviderBindable;
import io.helidon.inject.api.ServiceProviderInjectionException;
import io.helidon.inject.spi.InjectionResolver;

import jakarta.inject.Provider;

/**
 * Abstract base implementation for {@link ServiceProviderBindable}, which represents the basics for regular
 * Singleton, ApplicationScoped, Provider, and ServiceProvider based managed services. All code-generated services will
 * extend from this abstract base class.
 *
 * @param <T> the type of the service this provider manages
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public abstract class AbstractServiceProvider<T>
        implements ServiceProviderBindable<T>,
                   Activator,
                   DeActivator,
                   ActivationPhaseReceiver,
                   Resettable {
    static final DependenciesInfo NO_DEPS = DependenciesInfo.builder().build();
    private static final System.Logger LOGGER = System.getLogger(AbstractServiceProvider.class.getName());

    private final Semaphore activationSemaphore = new Semaphore(1);
    private final AtomicReference<T> serviceRef = new AtomicReference<>();
    private Phase phase;
    private long lastActivationThreadId;
    private InjectionServices injectionServices;
    private ActivationLog log;
    private ServiceInfo serviceInfo;
    private DependenciesInfo dependencies;
    private Map<String, HelidonInjectionPlan> injectionPlan;
    private ServiceProvider<?> interceptor;
    private boolean thisIsAnInterceptor;

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
     * @param injectionServices the services instance
     */
    protected AbstractServiceProvider(T instance,
                                      Phase phase,
                                      ServiceInfo serviceInfo,
                                      InjectionServices injectionServices) {
        this();
        if (instance != null) {
            this.serviceRef.set(instance);
            this.phase = (phase != null) ? phase : Phase.ACTIVE;
        }
        this.serviceInfo = ServiceInfo.builder(serviceInfo).build();
        this.injectionServices = Objects.requireNonNull(injectionServices);
        this.log = injectionServices.activationLog().orElseThrow();
        onInitialized();
    }

    /**
     * Will test and downcast the passed service provider to an instance of
     * {@link AbstractServiceProvider}.
     *
     * @param sp       the service provider
     * @param expected is the result expected to be present
     * @param <T>      the managed service type
     * @return the abstract service provider
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<AbstractServiceProvider<T>> toAbstractServiceProvider(ServiceProvider<?> sp,
                                                                                     boolean expected) {
        if (!(sp instanceof AbstractServiceProvider)) {
            if (expected) {
                throw new IllegalStateException("Expected provider to be of type " + AbstractServiceProvider.class.getName());
            }
            return Optional.empty();
        }
        return Optional.of((AbstractServiceProvider<T>) sp);
    }

    @Override
    public Optional<Activator> activator() {
        return Optional.of(this);
    }

    @Override
    public Optional<DeActivator> deActivator() {
        return Optional.of(this);
    }

    @Override
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return Optional.of(this);
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
        return Objects.requireNonNull(serviceInfo, getClass().getName() + " should have been initialized.");
    }

    @Override
    public DependenciesInfo dependencies() {
        return (dependencies == null) ? NO_DEPS : dependencies;
    }

    @Override
    public double weight() {
        return serviceInfo().realizedWeight();
    }

    @Override
    public Phase currentActivationPhase() {
        return phase;
    }

    @Override
    public Optional<InjectionServices> injectionServices() {
        return Optional.ofNullable(injectionServices);
    }

    InjectionServices requiredInjectionServices() {
        return injectionServices()
                .orElseThrow(() -> new InjectionServiceProviderException(description()
                                                                + ": injectionServices should have been previously set",
                                                             this));
    }

    @Override
    public void injectionServices(Optional<InjectionServices> injectionServices) {
        if (injectionServices.isPresent()
                || serviceRef.get() != null) {
            InjectionServices current = this.injectionServices;
            if (injectionServices.orElse(null) == current) {
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

        this.injectionServices = injectionServices.orElse(null);
        this.phase = Phase.INIT;
        if (this.injectionServices != null) {
            onInitialized();
        }
    }

    @Override
    public void moduleName(String moduleName) {
        Objects.requireNonNull(moduleName);
        ServiceInfo serviceInfo = serviceInfo();
        String moduleInfoName = serviceInfo.moduleName().orElse(null);
        if (!Objects.equals(moduleInfoName, moduleName)) {
            if (moduleInfoName != null) {
                throw alreadyInitialized();
            }
            this.serviceInfo = ServiceInfo.builder(serviceInfo).moduleName(moduleName).build();
        }
    }

    @Override
    public boolean isInterceptor() {
        return thisIsAnInterceptor;
    }

    @Override
    public Optional<ServiceProvider<?>> interceptor() {
        return Optional.ofNullable(interceptor);
    }

    @Override
    public void interceptor(ServiceProvider<?> interceptor) {
        Objects.requireNonNull(interceptor);
        if (this.interceptor != null || activationSemaphore.availablePermits() == 0 || phase != Phase.INIT) {
            throw alreadyInitialized();
        }
        this.interceptor = interceptor;
        if (interceptor instanceof AbstractServiceProvider<?>) {
            ((AbstractServiceProvider<?>) interceptor).intercepted(this);
        }
    }

    /**
     * Incorporate the intercepted qualifiers into our own qualifiers.
     *
     * @param intercepted the service being intercepted
     */
    void intercepted(AbstractServiceProvider<?> intercepted) {
        if (activationSemaphore.availablePermits() == 0 || phase != Phase.INIT) {
            throw alreadyInitialized();
        }
        this.thisIsAnInterceptor = true;
        this.serviceInfo = ServiceInfo.builder(this.serviceInfo)
                .addQualifiers(intercepted.serviceInfo().qualifiers())
                .build();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(serviceInfo.serviceTypeName());
    }

    @Override
    public boolean equals(Object another) {
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
    public String name(boolean simple) {
        TypeName name = serviceInfo().serviceTypeName();
        return (simple) ? name.classNameWithEnclosingNames().replace('.', '$') : name.resolvedName();
    }

    @Override
    public T get() {
        return first(InjectionServices.SERVICE_QUERY_REQUIRED)
                .orElseThrow(() -> new InjectionServiceProviderException("Expected to find a match", this));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<T> first(ContextualServiceQuery ctx) {
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

                if (ctx.expected() && instance == null) {
                    throw new InjectionServiceProviderException("Expected to find a match: " + ctx, this);
                }

                return Optional.ofNullable(instance);
            }
        } catch (ServiceProviderInjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            logger().log(System.Logger.Level.ERROR, "unable to activate: " + getClass().getName(), t);
            throw unableToActivate(t);
        }

        return Optional.ofNullable(serviceOrProvider);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<T> list(ContextualServiceQuery ctx) {
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
        } catch (ServiceProviderInjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw unableToActivate(t);
        }

        return (serviceProvider != null) ? List.of(serviceProvider) : List.of();
    }

    @Override
    public ActivationResult activate(ActivationRequest req) {
        if (isAlreadyAtTargetPhase(req.targetPhase())) {
            return ActivationResult.builder()
                    .serviceProvider(this)
                    .startingActivationPhase(currentActivationPhase())
                    .finishingActivationPhase(currentActivationPhase())
                    .targetActivationPhase(currentActivationPhase())
                    .finishingStatus(ActivationStatus.SUCCESS)
                    .build();
        }

        LogEntryAndResult logEntryAndResult = preambleActivate(req);
        ActivationResult.Builder res = logEntryAndResult.activationResult;

        // if we get here then we own the semaphore for activation...
        try {
            Phase finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.ACTIVATION_STARTING.ordinal()
                    && (Phase.INIT == res.finishingActivationPhase().orElse(null)
                                || Phase.PENDING == finishing
                                || Phase.ACTIVATION_STARTING == finishing
                                || Phase.DESTROYED == finishing)) {
                doStartingLifecycle(logEntryAndResult);
            }
            finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.GATHERING_DEPENDENCIES.ordinal()
                    && (Phase.ACTIVATION_STARTING == finishing)) {
                doGatheringDependencies(logEntryAndResult);
            }
            finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.CONSTRUCTING.ordinal()
                    && (Phase.GATHERING_DEPENDENCIES == finishing)) {
                doConstructing(logEntryAndResult);
            }
            finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.INJECTING.ordinal()
                    && (Phase.CONSTRUCTING == finishing)) {
                doInjecting(logEntryAndResult);
            }
            finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.POST_CONSTRUCTING.ordinal()
                    && (Phase.INJECTING == finishing)) {
                doPostConstructing(logEntryAndResult);
            }
            finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.ACTIVATION_FINISHING.ordinal()
                    && (Phase.POST_CONSTRUCTING == finishing)) {
                doActivationFinishing(logEntryAndResult);
            }
            finishing = res.finishingActivationPhase().orElse(null);
            if (res.targetActivationPhase().ordinal() >= Phase.ACTIVE.ordinal()
                    && (Phase.ACTIVATION_FINISHING == finishing)) {
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

    @Override
    public void onPhaseEvent(Event event,
                             Phase phase) {
        // NOP
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
        dependencies.allDependencies()
                .forEach(dep ->
                                 dep.injectionPointDependencies().forEach(ipDep -> {
                                     String id = ipDep.id();
                                     InjectionPointInfo prev = idToIpInfo.put(id, ipDep);
                                     if (prev != null
                                             && !prev.equals(ipDep)
                                             && !prev.dependencyToServiceInfo().equals(ipDep.dependencyToServiceInfo())) {
                                         logMultiDefInjectionNote(id, prev, ipDep);
                                     }
                                 }));

        ConcurrentHashMap<String, HelidonInjectionPlan> injectionPlan = new ConcurrentHashMap<>();
        AbstractServiceProvider<T> self = AbstractServiceProvider.this;
        ServiceInjectionPlanBinder.Binder result = new ServiceInjectionPlanBinder.Binder() {
            private InjectionPointInfo ipInfo;

            @Override
            public ServiceInjectionPlanBinder.Binder bind(String id,
                                                          ServiceProvider<?> serviceProvider) {
                HelidonInjectionPlan plan = createBuilder(id)
                        .injectionPointQualifiedServiceProviders(List.of(bind(serviceProvider)))
                        .build();
                Object prev = injectionPlan.put(id, plan);
                assert (prev == null);
                return this;
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bindMany(String id,
                                                              ServiceProvider<?>... serviceProviders) {
                HelidonInjectionPlan plan = createBuilder(id)
                        .injectionPointQualifiedServiceProviders(bind(Arrays.asList(serviceProviders)))
                        .build();
                Object prev = injectionPlan.put(id, plan);
                assert (prev == null);
                return this;
            }

            @Override
            public ServiceInjectionPlanBinder.Binder bindVoid(String id) {
                return bind(id, VoidServiceProvider.INSTANCE);
            }

            @Override
            public ServiceInjectionPlanBinder.Binder resolvedBind(String id,
                                                                  Class<?> serviceType) {
                try {
                    InjectionResolver resolver = (InjectionResolver) AbstractServiceProvider.this;
                    TypeName typeName = TypeName.create(serviceType);
                    ServiceInfoCriteria serviceInfo = ServiceInfoCriteria.builder()
                            .serviceTypeName(typeName)
                            .build();

                    InjectionPointInfo ipInfo = InjectionPointInfo.builder()
                            .id(id)
                            .dependencyToServiceInfo(serviceInfo)
                            // the following values are required, but dummy in this instance
                            .elementKind(ElementKind.METHOD)
                            .elementTypeName(typeName)
                            .elementName("none")
                            .serviceTypeName(typeName)
                            .access(AccessModifier.PUBLIC)
                            .ipType(typeName)
                            .ipName("none")
                            .baseIdentity("none")
                            .build();
                    Object resolved = Objects.requireNonNull(
                            resolver.resolve(ipInfo, requiredInjectionServices(), AbstractServiceProvider.this, false));
                    HelidonInjectionPlan plan = createBuilder(id)
                            .unqualifiedProviders(List.of(resolved))
                            .resolved(false)
                            .build();
                    Object prev = injectionPlan.put(id, plan);
                    assert (prev == null);
                    return this;
                } catch (Exception e) {
                    throw new InjectionServiceProviderException("Failed to process: " + id, e, AbstractServiceProvider.this);
                }
            }

            @Override
            public void commit() {
                if (!idToIpInfo.isEmpty()) {
                    throw new ServiceProviderInjectionException("Missing injection bindings for "
                                                         + idToIpInfo + " in "
                                                         + this, null, self);
                }

                if ((self.injectionPlan != null) && !self.injectionPlan.equals(injectionPlan)) {
                    throw new ServiceProviderInjectionException("Injection plan has already been bound for " + this, null, self);
                }
                self.injectionPlan = injectionPlan;
            }

            private ServiceProvider<?> bind(ServiceProvider<?> rawSp) {
                assert (!(rawSp instanceof BoundedServiceProvider)) : rawSp;
                return BoundedServiceProvider.create(rawSp, ipInfo);
            }

            private List<ServiceProvider<?>> bind(List<ServiceProvider<?>> rawList) {
                return rawList.stream().map(this::bind).collect(Collectors.toList());
            }

            private InjectionPointInfo safeGetIpInfo(String id) {
                InjectionPointInfo ipInfo = idToIpInfo.remove(id);
                if (ipInfo == null) {
                    throw new ServiceProviderInjectionException("Expected to find a dependency for '" + id + "' from "
                                                         + this + " in " + idToIpInfo, null, self);
                }
                return ipInfo;
            }

            private HelidonInjectionPlan.Builder createBuilder(String id) {
                ipInfo = safeGetIpInfo(id);
                return HelidonInjectionPlan.builder()
                        .injectionPointInfo(ipInfo)
                        .serviceProvider(self);
            }
        };

        return Optional.of(result);
    }

    /**
     * Get or Create the injection plan.
     *
     * @param resolveIps true if the injection points should also be activated/resolved.
     * @return the injection plan
     */
    public Map<String, HelidonInjectionPlan> getOrCreateInjectionPlan(boolean resolveIps) {
        if (this.injectionPlan != null) {
            return this.injectionPlan;
        }

        if (this.dependencies == null) {
            dependencies(dependencies());
        }

        Map<String, HelidonInjectionPlan> plan = DefaultInjectionPlans
                .createInjectionPlans(requiredInjectionServices(), this, dependencies, resolveIps, logger());
        assert (this.injectionPlan == null);
        this.injectionPlan = Objects.requireNonNull(plan);

        return this.injectionPlan;
    }

    @Override
    public boolean reset(boolean deep) {
        Object service = serviceRef.get();
        boolean result = false;
        boolean didAcquire = false;
        try {
            didAcquire = activationSemaphore.tryAcquire(1, TimeUnit.MILLISECONDS);

            if (service != null) {
                System.Logger.Level level = (
                        InjectionServices.injectionServices().map(InjectionServices::config)
                                                     .map(InjectionServicesConfig::shouldDebug)
                                                     .orElse(false))
                        ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
                logger().log(level, "Resetting " + this);
                if (deep && service instanceof Resettable) {
                    try {
                        if (((Resettable) service).reset(deep)) {
                            result = true;
                        }
                    } catch (Throwable t) {
                        logger().log(System.Logger.Level.WARNING, "Unable to reset: " + this, t); // eat it
                    }
                }
            }

            if (deep) {
                injectionPlan = null;
                interceptor = null;
                injectionServices = null;
                serviceRef.set(null);
                phase = Phase.INIT;

                result = true;
            }
        } catch (Exception e) {
            if (didAcquire) {
                throw new InjectionServiceProviderException("Unable to reset", e, this);
            } else {
                throw new InjectionServiceProviderException("Unable to reset during activation", e, this);
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

    @Override
    public ActivationResult deactivate(DeActivationRequest req) {
        if (!currentActivationPhase().eligibleForDeactivation()) {
            return ActivationResult.builder()
                    .serviceProvider(this)
                    .startingActivationPhase(currentActivationPhase())
                    .finishingActivationPhase(currentActivationPhase())
                    .targetActivationPhase(currentActivationPhase())
                    .finishingStatus(ActivationStatus.SUCCESS)
                    .build();
        }

        InjectionServices injectionServices = requiredInjectionServices();
        InjectionServicesConfig cfg = injectionServices.config();

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate
        LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.DESTROYED);
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PRE_DESTROYING);

        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                // if we couldn't grab the semaphore than we (or someone else) is busy activating this services, or
                // we deadlocked.
                ServiceProviderInjectionException e = timedOutDeActivationInjectionError(logEntryAndResult.logEntry);
                onFailedFinish(logEntryAndResult, e, req.throwIfError());
                return logEntryAndResult.activationResult.build();
            }
            didAcquire = true;

            // if we made it to here then we "own" the semaphore and the subsequent activation steps
            this.lastActivationThreadId = Thread.currentThread().getId();

            doPreDestroying(logEntryAndResult);
            if (Phase.PRE_DESTROYING == logEntryAndResult.activationResult.finishingActivationPhase().orElse(null)) {
                doDestroying(logEntryAndResult);
            }
            onFinished(logEntryAndResult);
        } catch (Throwable t) {
            ServiceProviderInjectionException e = interruptedPreActivationInjectionError(logEntryAndResult.logEntry, t);
            onFailedFinish(logEntryAndResult, e, req.throwIfError());
        } finally {
            if (didAcquire) {
                activationSemaphore.release();
            }
            onFinalShutdown();
        }

        return logEntryAndResult.activationResult.build();
    }

    /**
     * Called on the final leg of the shutdown sequence.
     */
    protected void onFinalShutdown() {
        this.lastActivationThreadId = 0;
        this.injectionPlan = null;
        this.phase = Phase.DESTROYED;
        this.serviceRef.set(null);
        this.injectionServices = null;
        this.log = null;
    }

    /**
     * Called on a failed finish of activation.
     *
     * @param logEntryAndResult the log entry holding the result
     * @param t                 the error that was observed
     * @param throwOnError      the flag indicating whether we should throw on error
     * @see #onFinished(AbstractServiceProvider.LogEntryAndResult)
     */
    protected void onFailedFinish(LogEntryAndResult logEntryAndResult,
                                  Throwable t,
                                  boolean throwOnError) {
        this.lastActivationThreadId = 0;
        onFailedFinish(logEntryAndResult, t, throwOnError, activationLog());
    }

    /**
     * The logger.
     *
     * @return the logger
     */
    protected System.Logger logger() {
        return LOGGER;
    }

    /**
     * Sets the service info that describes the managed service that is assigned.
     *
     * @param serviceInfo the service info
     */
    protected void serviceInfo(ServiceInfo serviceInfo) {
        Objects.requireNonNull(serviceInfo);
        if (this.injectionServices != null && this.serviceInfo != null) {
            throw alreadyInitialized();
        }
        this.serviceInfo = serviceInfo;
    }

    /**
     * Used to set the dependencies from this service provider.
     *
     * @param dependencies the dependencies from this service provider
     */
    protected void dependencies(DependenciesInfo dependencies) {
        Objects.requireNonNull(dependencies);
        if (this.dependencies != null) {
            throw alreadyInitialized();
        }
        this.dependencies = dependencies;
    }

    /**
     * Returns true if the current activation phase has reached the given target phase.
     *
     * @param targetPhase the target phase
     * @return true if the targetPhase has been reached
     */
    protected boolean isAlreadyAtTargetPhase(Phase targetPhase) {
        Objects.requireNonNull(targetPhase);
        return (currentActivationPhase() == targetPhase);
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
        if (log == null && injectionServices != null) {
            log = injectionServices.activationLog().orElse(DefaultActivationLog.createUnretainedLog(logger()));
        }
        return Optional.ofNullable(log);
    }

    /**
     * Called by the generated code when it is attempting to resolve a specific injection point dependency by id.
     *
     * @param deps the entire map of resolved dependencies
     * @param id   the id of the dependency to lookup
     * @param <T>  the type of the dependency
     * @return the resolved object
     */
    protected <T> T get(Map<String, T> deps, String id) {
        return Objects.requireNonNull(deps.get(id), "'" + id + "' expected to have been found in: " + deps.keySet());
    }

    /**
     * Will trigger an activation if the managed service is not yet active.
     *
     * @param ctx the context that triggered the activation
     * @return the result of the activation
     */
    protected Optional<T> maybeActivate(ContextualServiceQuery ctx) {
        Objects.requireNonNull(ctx);

        try {
            T serviceOrProvider = serviceRef.get();

            if (serviceOrProvider == null
                    || Phase.ACTIVE != currentActivationPhase()) {
                ActivationRequest req = InjectionServices.createActivationRequestDefault();
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
        } catch (ServiceProviderInjectionException ie) {
            throw ie;
        } catch (Throwable t) {
            throw unableToActivate(t);
        }
    }

    /**
     * Called on a successful finish of activation.
     *
     * @param logEntryAndResult the record holding the result
     * @see #onFailedFinish(AbstractServiceProvider.LogEntryAndResult, Throwable, boolean)
     */
    protected void onFinished(LogEntryAndResult logEntryAndResult) {
        // NOP
    }

    /**
     * Called during construction phase.
     *
     * @param logEntryAndResult the record that holds the results
     */
    protected void doConstructing(LogEntryAndResult logEntryAndResult) {
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.CONSTRUCTING);

        Map<String, Object> deps = logEntryAndResult.activationResult.resolvedDependencies();
        serviceRef(createServiceProvider(deps));

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    /**
     * Creates the service with the supplied resolved dependencies, key'ed by each injection point id.
     *
     * @param resolvedDeps the resolved dependencies
     * @return the newly created managed service
     * @throws ServiceProviderInjectionException since this is a base method for what is expected to be a code-generated derived
     *                            {@link Activator} then this method will throw an exception if the derived class does not
     *                            implement this method as it
     *                            normally should
     */
    protected T createServiceProvider(Map<String, Object> resolvedDeps) {
        ServiceProviderInjectionException e =
                new ServiceProviderInjectionException("Don't know how to create an instance of " + serviceInfo()
                                                                                            + ". Was the Activator generated?",
                                                                                    this);
        activationLog().ifPresent(e::activationLog);
        throw e;
    }

    /**
     * Used to control the order of injection. Jsr-330 is particular about this.
     *
     * @return the order of injection
     */
    protected List<TypeName> serviceTypeInjectionOrder() {
        return Collections.singletonList(serviceInfo.serviceTypeName());
    }

    /**
     * Called during the injection of fields.
     *
     * @param target         the target
     * @param deps           the dependencies
     * @param injections     the injections
     * @param forServiceType the service type
     */
    protected void doInjectingFields(Object target,
                                     Map<String, Object> deps,
                                     Set<String> injections,
                                     TypeName forServiceType) {
        // NOP; meant to be overridden
    }

    /**
     * Called during the injection of methods.
     *
     * @param target         the target
     * @param deps           the dependencies
     * @param injections     the injections
     * @param forServiceType the service type
     */
    protected void doInjectingMethods(Object target,
                                      Map<String, Object> deps,
                                      Set<String> injections,
                                      TypeName forServiceType) {
        // NOP; meant to be overridden
    }

    /**
     * Called during the {@link PostConstructMethod} process.
     *
     * @param logEntryAndResult the entry holding the result
     */
    protected void doPostConstructing(LogEntryAndResult logEntryAndResult) {
        Optional<PostConstructMethod> postConstruct = postConstructMethod();
        if (postConstruct.isPresent()) {
            startTransitionCurrentActivationPhase(logEntryAndResult, Phase.POST_CONSTRUCTING);
            postConstruct.get().postConstruct();
            finishedTransitionCurrentActivationPhase(logEntryAndResult);
        } else {
            startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.POST_CONSTRUCTING);
        }
    }

    /**
     * Called during the {@link PreDestroyMethod} process.
     *
     * @param logEntryAndResult the entry holding the result
     */
    protected void doPreDestroying(LogEntryAndResult logEntryAndResult) {
        Optional<PreDestroyMethod> preDestroyMethod = preDestroyMethod();
        if (preDestroyMethod.isEmpty()) {
            startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.PRE_DESTROYING);
        } else {
            startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PRE_DESTROYING);
            preDestroyMethod.get().preDestroy();
            finishedTransitionCurrentActivationPhase(logEntryAndResult);
        }
    }

    /**
     * Called after the {@link PreDestroyMethod} process.
     *
     * @param logEntryAndResult the entry holding the result
     */
    protected void doDestroying(LogEntryAndResult logEntryAndResult) {
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.DESTROYED);
        logEntryAndResult.activationResult.wasResolved(false);
        logEntryAndResult.activationResult.resolvedDependencies(Map.of());
        serviceRef(null);
        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    /**
     * Creates an injection exception appropriate when there are no matching qualified services for the context provided.
     *
     * @param ctx the context
     * @return the injection exception
     */
    protected ServiceProviderInjectionException expectedQualifiedServiceError(ContextualServiceQuery ctx) {
        ServiceProviderInjectionException e = new ServiceProviderInjectionException("Expected to return a non-null instance for: "
                                                          + ctx.injectionPointInfo()
                                                          + "; with criteria matching: " + ctx.serviceInfoCriteria(), this);
        activationLog().ifPresent(e::activationLog);
        return e;
    }

    /**
     * Creates a log entry result based upon the target phase provided.
     *
     * @param targetPhase the target phase
     * @return a new log entry and result record
     */
    protected LogEntryAndResult createLogEntryAndResult(Phase targetPhase) {
        Phase currentPhase = currentActivationPhase();
        ActivationResult.Builder activationResult = ActivationResult.builder()
                .serviceProvider(this)
                .startingActivationPhase(currentPhase)
                .finishingActivationPhase(currentPhase)
                .targetActivationPhase(targetPhase);
        ActivationLogEntry.Builder logEntry = ActivationLogEntry.builder()
                .serviceProvider(this)
                .event(Event.STARTING)
                .threadId(Thread.currentThread().getId())
                .activationResult(activationResult.build());
        return new LogEntryAndResult(logEntry, activationResult);
    }

    /**
     * Starts transitioning to a new phase.
     *
     * @param logEntryAndResult the record that will hold the state of the transition
     * @param newPhase          the target new phase
     */
    protected void startTransitionCurrentActivationPhase(LogEntryAndResult logEntryAndResult,
                                                         Phase newPhase) {
        Objects.requireNonNull(logEntryAndResult);
        Objects.requireNonNull(newPhase);
        logEntryAndResult.activationResult
                .finishingActivationPhase(newPhase);
        this.phase = newPhase;
        logEntryAndResult.logEntry
                .event(Event.STARTING)
                .activationResult(logEntryAndResult.activationResult.build());
        activationLog().ifPresent(log -> log.record(logEntryAndResult.logEntry.build()));
        onPhaseEvent(Event.STARTING, this.phase);
    }

    Map<String, Object> resolveDependencies(Map<String, HelidonInjectionPlan> mutablePlans) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map.copyOf(mutablePlans).forEach((key, value) -> {
            Object resolved;
            if (value.wasResolved()) {
                resolved = value.resolved();
                result.put(key, resolveOptional(value, resolved));
            } else {
                List<ServiceProvider<?>> serviceProviders = value.injectionPointQualifiedServiceProviders();
                serviceProviders = (serviceProviders == null)
                        ? List.of()
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
                mutablePlans.put(key, HelidonInjectionPlan.builder(value)
                        .wasResolved(true)
                        .update(builder -> {
                            if (resolved != null) {
                                builder.resolved(resolved);
                            }
                        })
                        .build());
            }
        });

        return result;
    }

    void serviceRef(T instance) {
        serviceRef.set(instance);
    }

    void onFailedFinish(LogEntryAndResult logEntryAndResult,
                        Throwable t,
                        boolean throwOnError,
                        Optional<ActivationLog> log) {
        ServiceProviderInjectionException e;

        ActivationLogEntry.Builder res = logEntryAndResult.logEntry;
        Throwable prev = res.error().orElse(null);
        if (prev == null || !(t instanceof ServiceProviderInjectionException)) {
            String msg = (t != null && t.getMessage() != null) ? t.getMessage() : "Failed to complete operation";
            e = new ServiceProviderInjectionException(msg, t, this);
            log.ifPresent(e::activationLog);
        } else {
            e = (ServiceProviderInjectionException) t;
        }

        res.error(e);
        logEntryAndResult.activationResult.finishingStatus(ActivationStatus.FAILURE);

        if (throwOnError) {
            throw e;
        }
    }

    void startAndFinishTransitionCurrentActivationPhase(LogEntryAndResult logEntryAndResult,
                                                        Phase newPhase) {
        startTransitionCurrentActivationPhase(logEntryAndResult, newPhase);
        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    void finishedTransitionCurrentActivationPhase(LogEntryAndResult logEntryAndResult) {
        logEntryAndResult.logEntry
                .event(Event.FINISHED)
                .activationResult(logEntryAndResult.activationResult.build());
        ActivationLog log = activationLog().orElse(null);
        if (log != null) {
            log.record(logEntryAndResult.logEntry.build());
        }
        onPhaseEvent(Event.FINISHED, this.phase);
    }

    // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate
    private LogEntryAndResult preambleActivate(ActivationRequest req) {
        assert (injectionServices != null) : "not initialized";

        LogEntryAndResult logEntryAndResult = createLogEntryAndResult(req.targetPhase());
        req.injectionPoint().ifPresent(logEntryAndResult.logEntry::injectionPoint);
        Phase startingPhase = req.startingPhase().orElse(Phase.PENDING);
        startTransitionCurrentActivationPhase(logEntryAndResult, startingPhase);

        // fail fast if we are in a recursive situation on this thread...
        if (logEntryAndResult.logEntry.threadId() == lastActivationThreadId && lastActivationThreadId > 0) {
            onFailedFinish(logEntryAndResult, recursiveActivationInjectionError(logEntryAndResult.logEntry), req.throwIfError());
            return logEntryAndResult;
        }

        InjectionServicesConfig cfg = injectionServices.config();
        boolean didAcquire = false;
        try {
            // let's wait a bit on the semaphore until we read timeout (probably detecting a deadlock situation)
            if (!activationSemaphore.tryAcquire(cfg.activationDeadlockDetectionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
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

            if (logEntryAndResult.activationResult.build().finished()) {
                didAcquire = false;
                activationSemaphore.release();
            }

            finishedTransitionCurrentActivationPhase(logEntryAndResult);
        } catch (Throwable t) {
            this.lastActivationThreadId = 0;
            if (didAcquire) {
                activationSemaphore.release();
            }

            ServiceProviderInjectionException e = interruptedPreActivationInjectionError(logEntryAndResult.logEntry, t);
            onFailedFinish(logEntryAndResult, e, req.throwIfError());
        }

        return logEntryAndResult;
    }

    private void onInitialized() {
        if (logger().isLoggable(System.Logger.Level.DEBUG)) {
            logger().log(System.Logger.Level.DEBUG, this + " initialized.");
        }
    }

    private void doStartingLifecycle(LogEntryAndResult logEntryAndResult) {
        startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_STARTING);
    }

    private void doGatheringDependencies(LogEntryAndResult logEntryAndResult) {
        startTransitionCurrentActivationPhase(logEntryAndResult, Phase.GATHERING_DEPENDENCIES);

        Map<String, HelidonInjectionPlan> plans = Objects.requireNonNull(getOrCreateInjectionPlan(false));
        Map<String, Object> deps = resolveDependencies(plans);
        if (!deps.isEmpty()) {
            logEntryAndResult.activationResult.resolvedDependencies(deps);
        }
        logEntryAndResult.activationResult.injectionPlans(plans);

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    @SuppressWarnings("unchecked")
    private Object resolveOptional(HelidonInjectionPlan plan,
                                   Object resolved) {
        if (!plan.injectionPointInfo().optionalWrapped() && resolved instanceof Optional) {
            return ((Optional<Object>) resolved).orElse(null);
        }
        return resolved;
    }

    private void doInjecting(LogEntryAndResult logEntryAndResult) {
        if (!ServiceUtils.isQualifiedInjectionTarget(this)) {
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
        List<TypeName> serviceTypeOrdering = serviceTypeInjectionOrder();
        LinkedHashSet<String> injections = new LinkedHashSet<>();
        serviceTypeOrdering.forEach((forServiceType) -> {
            try {
                doInjectingFields(target, deps, injections, forServiceType);
                doInjectingMethods(target, deps, injections, forServiceType);
            } catch (Throwable t) {
                throw new ServiceProviderInjectionException("Failed to activate/inject: " + this
                                                                    + "; dependency map was: " + deps, t, this);
            }
        });

        finishedTransitionCurrentActivationPhase(logEntryAndResult);
    }

    private void doActivationFinishing(LogEntryAndResult logEntryAndResult) {
        startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_FINISHING);
    }

    private void doActivationActive(LogEntryAndResult logEntryAndResult) {
        startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVE);
    }

    private ServiceProviderInjectionException recursiveActivationInjectionError(ActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        ServiceProviderInjectionException e =
                new ServiceProviderInjectionException("A circular dependency found during activation of " + targetServiceProvider,
                                                                                    targetServiceProvider);
        activationLog().ifPresent(e::activationLog);
        entry.error(e);
        return e;
    }

    private ServiceProviderInjectionException timedOutActivationInjectionError(ActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        ServiceProviderInjectionException e =
                new ServiceProviderInjectionException("Timed out during activation of " + targetServiceProvider,
                                                                                    targetServiceProvider);
        activationLog().ifPresent(e::activationLog);
        entry.error(e);
        return e;
    }

    private ServiceProviderInjectionException timedOutDeActivationInjectionError(ActivationLogEntry.Builder entry) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        ServiceProviderInjectionException e =
                new ServiceProviderInjectionException("Timed out during deactivation of " + targetServiceProvider,
                                                                                    targetServiceProvider);
        activationLog().ifPresent(e::activationLog);
        entry.error(e);
        return e;
    }

    private ServiceProviderInjectionException interruptedPreActivationInjectionError(ActivationLogEntry.Builder entry,
                                                                                     Throwable cause) {
        ServiceProvider<?> targetServiceProvider = entry.serviceProvider().orElseThrow();
        ServiceProviderInjectionException e = new ServiceProviderInjectionException(
                "A circular dependency found during activation of " + targetServiceProvider,
                cause,
                targetServiceProvider);
        activationLog().ifPresent(e::activationLog);
        entry.error(e);
        return e;
    }

    private ServiceProviderInjectionException managedServiceInstanceShouldHaveBeenSetException() {
        ServiceProviderInjectionException e = new ServiceProviderInjectionException(
                "This managed service instance expected to have been set",
                this);
        activationLog().ifPresent(e::activationLog);
        return e;
    }

    private ServiceProviderInjectionException activationFailed(ActivationResult res) {
        ServiceProviderInjectionException e = new ServiceProviderInjectionException("Activation failed: " + res, this);
        activationLog().ifPresent(e::activationLog);
        return e;
    }

    private InjectionServiceProviderException unableToActivate(Throwable cause) {
        return new InjectionServiceProviderException("Unable to activate: " + getClass().getName(), cause, this);
    }

    private InjectionServiceProviderException alreadyInitialized() {
        throw new InjectionServiceProviderException("Already initialized", this);
    }

    private void logMultiDefInjectionNote(String id,
                                          Object prev,
                                          InjectionPointInfo ipDep) {
        String message = "There are two different services sharing the same injection point id; first = "
                + prev + " and the second = " + ipDep + "; both use the id '" + id
                + "'; note that the second will override the first";
        if (log != null) {
            log.record(ActivationLogEntry.builder()
                               .serviceProvider(this)
                               .injectionPoint(ipDep)
                               .message(message)
                               .build());
        } else {
            logger().log(System.Logger.Level.DEBUG, message);
        }
    }

    /**
     * Represents a result of a phase transition.
     *
     * @see #createLogEntryAndResult(Phase)
     */
    // note that for one result, there may be N logEntry records we will build and write to the log
    protected static class LogEntryAndResult /* implements Cloneable*/ {
        private final ActivationResult.Builder activationResult;
        private final ActivationLogEntry.Builder logEntry;

        LogEntryAndResult(ActivationLogEntry.Builder logEntry,
                          ActivationResult.Builder activationResult) {
            this.logEntry = logEntry;
            this.activationResult = activationResult;
        }

        ActivationResult.Builder activationResult() {
            return activationResult;
        }

        ActivationLogEntry.Builder logEntry() {
            return logEntry;
        }
    }

}
