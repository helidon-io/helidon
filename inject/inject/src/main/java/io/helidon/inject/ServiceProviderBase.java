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

package io.helidon.inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;

/**
 * A base of service providers, taking care of the common responsibilities, such as activation, lookup etc.
 *
 * @param <T> type of the provided service
 */
public abstract class ServiceProviderBase<T>
        extends DescribedServiceProvider<T>
        implements ServiceProviderBindable<T>, ServiceInfo, Activator<T> {
    static final TypeName SUPPLIER_TYPE = TypeName.create(Supplier.class);
    private static final System.Logger LOGGER = System.getLogger(ServiceProviderBase.class.getName());
    private static final TypeName SERVICE_PROVIDER_TYPE = TypeName.create(ServiceProvider.class);
    private static final TypeName INJECTION_POINT_PROVIDER_TYPE = TypeName.create(InjectionPointProvider.class);

    private final Services services;
    private final InterceptionMetadata interceptionMetadata;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ServiceDescriptor<T> descriptor;
    private final ActivationRequest defaultActivationRequest;

    private volatile ServiceInstance<T> serviceInstance;
    private volatile Phase currentPhase = Phase.CONSTRUCTED;
    private volatile ServiceProvider<?> interceptor;
    private volatile InjectionContext injectionContext;

    /**
     * A new service provider base.
     *
     * @param services          services this provider belongs to
     * @param serviceDescriptor service descriptor
     */
    protected ServiceProviderBase(Services services,
                                  ServiceDescriptor<T> serviceDescriptor) {
        super(serviceDescriptor);

        this.services = services;
        this.interceptionMetadata = new InterceptionMetadataImpl(services);
        this.descriptor = serviceDescriptor;

        this.defaultActivationRequest = ActivationRequest.builder()
                .targetPhase(services.limitRuntimePhase())
                .build();
    }

    @Override
    public ActivationResult activate(ActivationRequest activationRequest) {
        // acquire write lock, as this is expected to activate
        Lock lock = rwLock.writeLock();
        try {
            lock.lock();
            if (currentPhase == activationRequest.targetPhase()) {
                return ActivationResult.builder()
                        .serviceProvider(this)
                        .startingActivationPhase(currentPhase)
                        .finishingActivationPhase(currentPhase)
                        .targetActivationPhase(currentPhase)
                        .finishingStatus(ActivationStatus.SUCCESS)
                        .build();
            }
            if (currentPhase.ordinal() > activationRequest.targetPhase().ordinal()) {
                return ActivationResult.builder()
                        .serviceProvider(this)
                        .startingActivationPhase(currentPhase)
                        .finishingActivationPhase(currentPhase)
                        .targetActivationPhase(activationRequest.targetPhase())
                        .finishingStatus(ActivationStatus.FAILURE)
                        .build();
            }
            return doActivate(activationRequest);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ActivationResult deactivate(DeActivationRequest req) {
        // acquire write lock, as this is expected to de-activate
        Lock lock = rwLock.writeLock();
        try {
            lock.lock();
            return doDeactivate(req);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ServiceProvider<T> serviceProvider() {
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public List<T> list(ContextualServiceQuery query) {
        T serviceOrProvider = get(query.expected());

        Object result;
        if (contracts().contains(SUPPLIER_TYPE)) {
            if (contracts().contains(INJECTION_POINT_PROVIDER_TYPE)) {
                InjectionPointProvider<T> provider = (InjectionPointProvider<T>) serviceOrProvider;
                result = provider.list(query);
            } else if (contracts().contains(SERVICE_PROVIDER_TYPE)) {
                ServiceProvider<T> provider = (ServiceProvider<T>) serviceOrProvider;
                result = provider.list(query);
            } else {
                Supplier<T> provider = (Supplier<T>) serviceOrProvider;
                result = provider.get();
            }
        } else {
            result = serviceOrProvider;
        }

        if (result == null) {
            if (query.expected()) {
                throw new InjectionServiceProviderException("This managed service instance expected to have been set",
                                                            this);
            }
            return List.of();
        }

        if (result instanceof List list) {
            return list;
        } else {
            return (List<T>) List.of(result);
        }
    }

    @Override
    public Optional<T> first(ContextualServiceQuery query) {
        T serviceOrProvider = get(query.expected());

        try {
            return first(query, serviceOrProvider);
        } catch (InjectionServiceProviderException ie) {
            throw ie;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Unable to activate: " + infoType().fqName(), e);
            throw new InjectionServiceProviderException("Unable to activate: " + infoType().fqName(), e, this);
        }
    }

    @Override
    public String id() {
        return id(true);
    }

    @Override
    public String description() {
        return id(false) + ":" + currentActivationPhase();
    }

    @Override
    public Phase currentActivationPhase() {
        return currentPhase;
    }

    @Override
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return Optional.of(this);
    }

    @Override
    public void interceptor(ServiceProvider<?> interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public boolean isIntercepted() {
        return interceptor != null;
    }

    @Override
    public Optional<ServiceProvider<?>> interceptor() {
        return Optional.ofNullable(interceptor);
    }

    @Override
    public Optional<ServiceInjectionPlanBinder.Binder> injectionPlanBinder() {
        if (injectionContext != null) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "this service provider already has an injection plan (which is unusual here): " + this);
        }
        return Optional.of(new ServiceInjectBinderImpl(services, this));
    }

    @Override
    public String toString() {
        return description();
    }

    @Override
    public boolean isProvider() {
        return contracts().contains(SUPPLIER_TYPE);
    }

    /**
     * Get the value from this service provider.
     *
     * @param expected whether the value is expected
     * @return value, or {@code null} if value is not available and not expected
     * @throws io.helidon.inject.InjectionServiceProviderException in case the value is not available and expected
     */
    protected T get(boolean expected) {
        Lock lock = rwLock.readLock();
        try {
            lock.lock();
            if (currentPhase == Phase.ACTIVE) {
                return serviceInstance.get();
            }
        } finally {
            lock.unlock();
        }
        ActivationResult res = activate(defaultActivationRequest);
        if (res.failure() && expected) {
            throw new InjectionServiceProviderException("Activation failed: " + res, this);
        }
        try {
            lock.lock();
            return serviceInstance.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set an explicit phase of activation, and possibly an instance of the service.
     *
     * @param phase    phase to set
     * @param instance instance to set (nullabe!)
     */
    protected void state(Phase phase, T instance) {
        Lock lock = rwLock.writeLock();
        try {
            lock.lock();
            this.currentPhase = phase;
            if (this.serviceInstance == null) {
                this.serviceInstance = ServiceInstance.create(descriptor, instance);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Configure current phase.
     *
     * @param phase phase to set
     */
    protected void phase(Phase phase) {
        Lock lock = rwLock.writeLock();
        try {
            this.currentPhase = phase;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Id of this service provider.
     *
     * @param fq use fully qualified name of the service
     * @return id of this provider
     */
    protected String id(boolean fq) {
        if (fq) {
            return serviceInfo().serviceType().fqName();
        }
        return serviceInfo().serviceType().classNameWithEnclosingNames();
    }

    /**
     * Service registry this provider belongs to.
     *
     * @return injection services
     */
    protected Services services() {
        return services;
    }

    /**
     * Activate based on an activation request.
     *
     * @param req activation request
     * @return activation result
     */
    protected ActivationResult doActivate(ActivationRequest req) {
        Phase initialPhase = this.currentPhase;
        Phase startingPhase = req.startingPhase().orElse(initialPhase);
        Phase targetPhase = req.targetPhase();
        this.currentPhase = startingPhase;
        Phase finishingPhase = startingPhase;

        ActivationResult.Builder res = ActivationResult.builder()
                .serviceProvider(this)
                .startingActivationPhase(initialPhase)
                .finishingActivationPhase(startingPhase)
                .targetActivationPhase(targetPhase)
                .finishingStatus(ActivationStatus.SUCCESS);

        if (targetPhase.ordinal() >= Phase.INIT.ordinal() && initialPhase == Phase.CONSTRUCTED) {
            init(req, res);
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);

        if (targetPhase.ordinal() > Phase.ACTIVATION_STARTING.ordinal()) {
            if (Phase.INIT == startingPhase
                    || Phase.PENDING == startingPhase
                    || Phase.ACTIVATION_STARTING == startingPhase
                    || Phase.DESTROYED == startingPhase) {
                startLifecycle(req, res);
            }
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);
        if (targetPhase.ordinal() > Phase.GATHERING_DEPENDENCIES.ordinal()
                && Phase.ACTIVATION_STARTING == finishingPhase) {
            gatherDependencies(req, res);
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);
        if (res.targetActivationPhase().ordinal() >= Phase.CONSTRUCTING.ordinal()
                && (Phase.GATHERING_DEPENDENCIES == finishingPhase)) {
            construct(req, res);
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);
        if (res.targetActivationPhase().ordinal() >= Phase.INJECTING.ordinal()
                && (Phase.CONSTRUCTING == finishingPhase)) {
            inject(req, res);
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);
        if (res.targetActivationPhase().ordinal() >= Phase.POST_CONSTRUCTING.ordinal()
                && (Phase.INJECTING == finishingPhase)) {
            postConstruct(req, res);
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);
        if (res.targetActivationPhase().ordinal() >= Phase.ACTIVATION_FINISHING.ordinal()
                && (Phase.POST_CONSTRUCTING == finishingPhase)) {
            finishActivation(req, res);
        }
        finishingPhase = res.finishingActivationPhase().orElse(finishingPhase);
        if (res.targetActivationPhase().ordinal() >= Phase.ACTIVE.ordinal()
                && (Phase.ACTIVATION_FINISHING == finishingPhase)) {
            setActive(req, res);
        }

        return res.build();
    }

    /**
     * Deactivate based on request.
     *
     * @param req request
     * @return activation result
     */
    protected ActivationResult doDeactivate(DeActivationRequest req) {
        ActivationResult.Builder res = ActivationResult.builder()
                .serviceProvider(this)
                .finishingStatus(ActivationStatus.SUCCESS);

        if (!currentPhase.eligibleForDeactivation()) {
            stateTransitionStart(res, Phase.DESTROYED);
            return ActivationResult.builder()
                    .serviceProvider(this)
                    .targetActivationPhase(Phase.DESTROYED)
                    .finishingActivationPhase(currentPhase)
                    .finishingStatus(ActivationStatus.SUCCESS)
                    .build();
        }

        res.startingActivationPhase(this.currentPhase);
        stateTransitionStart(res, Phase.PRE_DESTROYING);
        preDestroy(req, res);
        stateTransitionStart(res, Phase.DESTROYED);

        return res.build();
    }

    /**
     * Post construct the instance.
     *
     * @param req activation request
     * @param res activation response builder
     */
    protected void postConstruct(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.POST_CONSTRUCTING);

        if (serviceInstance != null) {
            serviceInstance.postConstruct();
        }
    }

    /**
     * Inject the instance.
     *
     * @param req activation request
     * @param res activation response builder
     */
    protected void inject(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.INJECTING);

        if (serviceInstance != null) {
            serviceInstance.inject();
        }
    }

    /**
     * Create a new instance of the service (in {@link io.helidon.inject.Phase#CONSTRUCTING}).
     * If you override this method, you need to override all methods handling instances, such as
     * {@link #inject(ActivationRequest, io.helidon.inject.ActivationResult.Builder)}.
     *
     * @param req activation request
     * @param res activation response builder
     * @see #inject(ActivationRequest, io.helidon.inject.ActivationResult.Builder)
     * @see #postConstruct(ActivationRequest, io.helidon.inject.ActivationResult.Builder)
     * @see #preDestroy(DeActivationRequest, io.helidon.inject.ActivationResult.Builder)
     */
    protected void construct(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.CONSTRUCTING);

        // descendant may set an explicit instance, in such a case, we will not re-create it
        if (serviceInstance == null) {
            serviceInstance = ServiceInstance.create(interceptionMetadata, injectionContext, descriptor);
            serviceInstance.construct();
        }
    }

    /**
     * Prepare a dependency.
     *
     * @param services      associated services instance
     * @param injectionPlan injection plan to put the new dependency to
     * @param dependency    injection point to satisfy
     */
    protected void prepareDependency(Services services, Map<Ip, Supplier<?>> injectionPlan, Ip dependency) {
        Lookup criteria = Lookup.create(dependency);
        List<ServiceProvider<Object>> discovered = services.allProviders(criteria)
                .stream()
                .filter(it -> it != this)
                .toList();

        TypeName ipType = dependency.typeName();

        // now there are a few options - optional, list, and single instance
        if (discovered.isEmpty()) {
            if (ipType.isOptional()) {
                injectionPlan.put(dependency, Optional::empty);
                return;
            }
            if (ipType.isList()) {
                injectionPlan.put(dependency, List::of);
                return;
            }
            throw new InjectionServiceProviderException("Expected to resolve a service matching "
                                                                + criteria
                                                                + " for dependency: " + dependency
                                                                + ", for service: " + serviceType().fqName(),
                                                        this);
        }

        // we have a response
        if (ipType.isList()) {
            // is a list needed?
            TypeName typeOfElements = ipType.typeArguments().getFirst();
            if (typeOfElements.equals(SUPPLIER_TYPE) || typeOfElements.equals(SERVICE_PROVIDER_TYPE)) {
                injectionPlan.put(dependency, () -> discovered);
                return;
            }

            if (discovered.size() == 1) {
                injectionPlan.put(dependency, () -> {
                    Object resolved = discovered.getFirst().get();
                    if (resolved instanceof List<?>) {
                        return resolved;
                    }
                    return List.of(resolved);
                });
                return;
            }

            injectionPlan.put(dependency, () -> discovered.stream()
                    .map(ServiceProvider::get)
                    .toList());
            return;
        }
        if (ipType.isOptional()) {
            // is an Optional needed?
            TypeName typeOfElement = ipType.typeArguments().getFirst();
            if (typeOfElement.equals(SUPPLIER_TYPE) || typeOfElement.equals(SERVICE_PROVIDER_TYPE)) {
                injectionPlan.put(dependency, () -> Optional.of(discovered.getFirst()));
                return;
            }

            injectionPlan.put(dependency, () -> {
                Optional<?> firstResult = discovered.getFirst().first(ContextualServiceQuery.EMPTY);
                if (firstResult.isEmpty()) {
                    return Optional.empty();
                }
                Object resolved = firstResult.get();
                if (resolved instanceof Optional<?>) {
                    return resolved;
                }
                return Optional.ofNullable(resolved);
            });
            return;
        }

        if (ipType.equals(SUPPLIER_TYPE)
                || ipType.equals(SERVICE_PROVIDER_TYPE)
                || ipType.equals(INJECTION_POINT_PROVIDER_TYPE)) {
            // is a provider needed?
            injectionPlan.put(dependency, discovered::getFirst);
            return;
        }
        // and finally just get the value of the first service
        injectionPlan.put(dependency, discovered.getFirst()::get);
    }

    /**
     * Start transitioning to a state.
     *
     * @param res   activation response builder
     * @param phase phase to transition to
     */
    protected void stateTransitionStart(ActivationResult.Builder res, Phase phase) {
        res.finishingActivationPhase(phase);
        this.currentPhase = phase;
    }

    /**
     * Access to the injection context, if already configured.
     *
     * @return injection context
     */
    protected Optional<InjectionContext> injectionContext() {
        return Optional.ofNullable(injectionContext);
    }

    /**
     * Configure an injection context.
     *
     * @param injectionContext injection context to set
     */
    protected void injectionContext(InjectionContext injectionContext) {
        this.injectionContext = injectionContext;
    }

    /**
     * Transition to {@link io.helidon.inject.Phase#INIT}.
     * Default implementation only sets the current phase and updates phase in response builder.
     *
     * @param req activation request
     * @param res activation response builder
     */
    protected void init(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.INIT);
    }

    /**
     * Pre destroy the service instance.
     *
     * @param req activation request
     * @param res activation response builder
     */
    protected void preDestroy(DeActivationRequest req, ActivationResult.Builder res) {
        if (serviceInstance != null) {
            serviceInstance.preDestroy();
            serviceInstance = null;
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<T> first(ContextualServiceQuery query, T serviceOrProvider) {
        T service;
        if (contracts().contains(SUPPLIER_TYPE)) {
            if (contracts().contains(INJECTION_POINT_PROVIDER_TYPE)) {
                InjectionPointProvider<T> provider = (InjectionPointProvider<T>) serviceOrProvider;
                service = provider.first(query).orElse(null);
            } else if (contracts().contains(SERVICE_PROVIDER_TYPE)) {
                ServiceProvider<T> provider = (ServiceProvider<T>) serviceOrProvider;
                service = provider.first(query).orElse(null);
            } else {
                Supplier<T> provider = (Supplier<T>) serviceOrProvider;
                service = provider.get();
            }
        } else {
            service = serviceOrProvider;
        }

        if (service == null) {
            if (query.expected()) {
                throw new InjectionServiceProviderException("This managed service instance expected to have been set",
                                                            this);
            }
            return Optional.empty();
        }
        return Optional.of(service);
    }

    private void setActive(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.ACTIVE);
    }

    private void finishActivation(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.ACTIVATION_FINISHING);
    }

    private void startLifecycle(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.ACTIVATION_STARTING);
    }

    private void gatherDependencies(ActivationRequest req, ActivationResult.Builder res) {
        stateTransitionStart(res, Phase.GATHERING_DEPENDENCIES);

        List<Ip> servicesDeps = dependencies();

        if (servicesDeps.isEmpty()) {
            return;
        }

        if (injectionContext != null) {
            // obtained from application
            return;
        }

        Map<Ip, Supplier<?>> injectionPlan = new HashMap<>();

        for (Ip ipInfo : servicesDeps) {
            prepareDependency(services, injectionPlan, ipInfo);
        }

        this.injectionContext = HelidonInjectionContext.create(injectionPlan);
    }

    /**
     * An implementation of a service binder.
     */
    protected static class ServiceInjectBinderImpl implements ServiceInjectionPlanBinder.Binder {
        private final ServiceProviderBase<?> self;
        private final Map<Ip, Supplier<?>> injectionPlan = new HashMap<>();
        private final Services services;

        /**
         * Create a new instance of a binder.
         *
         * @param services injection services we are bound to
         * @param self     service provider responsible for this binding
         */
        protected ServiceInjectBinderImpl(Services services, ServiceProviderBase<?> self) {
            this.self = self;
            this.services = services;
        }

        @Override
        public void commit() {
            self.injectionContext(HelidonInjectionContext.create(injectionPlan));
        }

        @Override
        public ServiceInjectionPlanBinder.Binder bind(Ip injectionPoint, boolean useProvider, ServiceInfo serviceInfo) {
            ServiceProvider<?> serviceProvider = BoundServiceProvider.create(services.serviceProvider(serviceInfo),
                                                                             injectionPoint);
            if (useProvider) {
                injectionPlan.put(injectionPoint, () -> serviceProvider);
            } else {
                ContextualServiceQuery query = ContextualServiceQuery.builder()
                        .from(Lookup.create(injectionPoint))
                        .expected(true)
                        .build();
                injectionPlan.put(injectionPoint, () -> mapFromProvider(query, serviceProvider));
            }

            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder bindOptional(Ip injectionPoint,
                                                              boolean useProvider,
                                                              ServiceInfo... serviceInfos) {

            if (serviceInfos.length == 0) {
                injectionPlan.put(injectionPoint, Optional::empty);
            } else {
                ServiceProvider<?> serviceProvider = BoundServiceProvider.create(services.serviceProvider(serviceInfos[0]),
                                                                                 injectionPoint);
                if (useProvider) {
                    injectionPlan.put(injectionPoint, () -> Optional.of(serviceProvider));
                } else {
                    ContextualServiceQuery query = ContextualServiceQuery.builder()
                            .from(Lookup.create(injectionPoint))
                            .injectionPoint(injectionPoint)
                            .build();
                    injectionPlan.put(injectionPoint, () -> Optional.ofNullable(mapFromProvider(query, serviceProvider)));
                }
            }

            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder bindMany(Ip injectionPoint,
                                                          boolean useProvider,
                                                          ServiceInfo... serviceInfos) {

            List<? extends ServiceProvider<?>> providers = Stream.of(serviceInfos)
                    .map(services::serviceProvider)
                    .map(it -> BoundServiceProvider.create(it, injectionPoint))
                    .toList();

            if (useProvider) {
                injectionPlan.put(injectionPoint, () -> providers);
            } else {
                ContextualServiceQuery query = ContextualServiceQuery.builder()
                        .from(Lookup.create(injectionPoint))
                        .injectionPoint(injectionPoint)
                        .expected(true)
                        .build();
                injectionPlan.put(injectionPoint, () -> providers.stream()
                        .flatMap(it -> mapStreamFromProvider(query, it))
                        .toList());
            }

            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder bindNull(Ip injectionPoint) {
            injectionPlan.put(injectionPoint, () -> null);
            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder runtimeBind(Ip injectionPoint, boolean useProvider, Class<?> serviceType) {
            if (self instanceof InjectionResolver ir) {
                Optional<Ip> foundIp = self.dependencies()
                        .stream()
                        .filter(it -> it == injectionPoint)
                        .findFirst();

                if (foundIp.isPresent()) {
                    injectionPlan.put(injectionPoint, () -> ir.resolve(foundIp.get(), services, self, true).get());
                    return this;
                }
            }
            Supplier<?> serviceProvider = services.get(serviceType);
            injectionPlan.put(injectionPoint, () -> useProvider ? serviceProvider : serviceProvider.get());
            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder runtimeBindOptional(Ip injectionPoint,
                                                                     boolean useProvider,
                                                                     Class<?> serviceType) {
            if (self instanceof InjectionResolver ir) {
                Optional<Ip> foundIp = self.dependencies()
                        .stream()
                        .filter(it -> it == injectionPoint)
                        .findFirst();

                if (foundIp.isPresent()) {
                    injectionPlan.put(injectionPoint, () -> ir.resolve(foundIp.get(), services, self, true));
                    return this;
                }
            }
            Optional<? extends Supplier<?>> serviceProvider = services.first(serviceType);
            if (serviceProvider.isEmpty()) {
                injectionPlan.put(injectionPoint, Optional::empty);
            } else {
                injectionPlan.put(injectionPoint, () -> useProvider
                        ? serviceProvider
                        : Optional.of(serviceProvider.get().get()));
            }

            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder runtimeBindMany(Ip injectionPoint, boolean useProvider, Class<?> serviceType) {
            List<? extends Supplier<?>> providers = services.all(serviceType);

            if (useProvider) {
                injectionPlan.put(injectionPoint, () -> providers);
            } else {
                injectionPlan.put(injectionPoint, () -> providers.stream()
                        .map(Supplier::get)
                        .toList());
            }

            return this;
        }

        @Override
        public ServiceInjectionPlanBinder.Binder runtimeBindNullable(Ip injectionPoint,
                                                                     boolean useProvider,
                                                                     Class<?> serviceType) {
            Optional<? extends Supplier<?>> serviceProvider = services.first(serviceType);

            if (serviceProvider.isEmpty()) {
                injectionPlan.put(injectionPoint, () -> null);
            } else {
                injectionPlan.put(injectionPoint, () -> useProvider ? serviceProvider : Optional.of(serviceProvider.get().get()));
            }

            return this;
        }

        /**
         * Current injection plan.
         *
         * @return map of injection point ids to a supplier of their values
         */
        protected Map<Ip, Supplier<?>> injectionPlan() {
            return injectionPlan;
        }

        private Object mapFromProvider(ContextualServiceQuery query, ServiceProvider<?> provider) {
            return provider.first(query).orElse(null);
        }

        private Stream<?> mapStreamFromProvider(ContextualServiceQuery query, ServiceProvider<?> provider) {
            return provider.list(query).stream();
        }
    }
}
