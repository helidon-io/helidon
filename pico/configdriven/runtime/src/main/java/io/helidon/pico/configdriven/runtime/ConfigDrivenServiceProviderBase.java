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

package io.helidon.pico.configdriven.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.config.Config;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.pico.api.CallingContext;
import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.Event;
import io.helidon.pico.api.InjectionException;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.InjectionPointProvider;
import io.helidon.pico.api.Phase;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Qualifier;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.ServiceProviderBindable;
import io.helidon.pico.api.ServiceProviderProvider;
import io.helidon.pico.configdriven.api.ConfigDriven;
import io.helidon.pico.configdriven.api.NamedInstance;
import io.helidon.pico.runtime.AbstractServiceProvider;
import io.helidon.pico.spi.InjectionResolver;

import static io.helidon.pico.api.CommonQualifiers.WILDCARD_NAMED;
import static io.helidon.pico.configdriven.runtime.ConfigDrivenUtils.hasValue;
import static io.helidon.pico.configdriven.runtime.ConfigDrivenUtils.isBlank;
import static io.helidon.pico.runtime.PicoExceptions.toErrorMessage;

/**
 * Abstract base for any config-driven-service.
 *
 * @param <T>  the type of the service this provider manages
 * @param <CB> the type of config beans that this service is configured by
 */
// special note: many of these methods are referenced in code generated code!
public abstract class ConfigDrivenServiceProviderBase<T, CB> extends AbstractServiceProvider<T>
        implements ConfiguredServiceProvider<T, CB>,
                   ServiceProviderProvider,
                   InjectionPointProvider<T>,
                   InjectionResolver {
    private static final System.Logger LOGGER = System.getLogger(ConfigDrivenServiceProviderBase.class.getName());
    private static final Qualifier EMPTY_CONFIGURED_BY = Qualifier.create(ConfigDriven.class);

    private final AtomicReference<Boolean> isRootProvider = new AtomicReference<>();
    private final AtomicReference<ConfiguredServiceProvider<T, CB>> rootProvider = new AtomicReference<>();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicReference<CallingContext> initializationCallingContext
            = new AtomicReference<>();  // used only when we are in pico.debug mode
    // map of name to config driven service provider (non-root)
    private final Map<String, ConfigDrivenServiceProviderBase<T, CB>> managedConfiguredServicesMap
            = new ConcurrentHashMap<>();
    private final String instanceId;

    /**
     * The default constructor.
     *
     * @param instanceId of this provider, root provider is hardcoded to {@code root}, managed instances are named based on
     *                  the config bean name
     */
    protected ConfigDrivenServiceProviderBase(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Called during initialization to register a loaded config bean.
     *
     * @param configBean the config bean
     */
    @Override
    public final void registerConfigBean(NamedInstance<CB> configBean) {
        Objects.requireNonNull(configBean);
        assertIsInitializing();

        Object prev = managedConfiguredServicesMap.put(configBean.name(), createInstance(configBean));
        assert (prev == null);
    }

    @Override
    public boolean reset(boolean deep) {
        super.reset(deep);
        managedConfiguredServicesMap.clear();
        isRootProvider.set(null);
        rootProvider.set(null);
        initialized.set(false);
        initializationCallingContext.set(null);
        return true;
    }

    @Override
    public boolean isRootProvider() {
        Boolean root = isRootProvider.get();
        return (root != null && root && rootProvider.get() == null);
    }

    @Override
    public Optional<ServiceProvider<?>> rootProvider() {
        return Optional.ofNullable(rootProvider.get());
    }

    @Override
    public void rootProvider(ServiceProvider<T> root) {
        assertIsRootProvider(false, false);
        assert (!isRootProvider() && rootProvider.get() == null && this != root);
        boolean set = rootProvider.compareAndSet(null,
                                                 (ConfigDrivenServiceProviderBase<T, CB>) Objects.requireNonNull(root));
        assert (set);
    }

    @Override
    public void picoServices(Optional<PicoServices> picoServices) {
        assertIsInitializing();
        assertIsRootProvider(true, false);

        if (isRootProvider()) {
            // override out service info to account for any named lookup
            ServiceInfo serviceInfo = Objects.requireNonNull(serviceInfo());
            if (!serviceInfo.qualifiers().contains(WILDCARD_NAMED)) {
                serviceInfo = ServiceInfo.builder(serviceInfo)
                        .addQualifier(WILDCARD_NAMED)
                        .build();
                serviceInfo(serviceInfo);
            }

            // bind to the config bean registry ...  but, don't yet resolve!
            ConfigBeanRegistry cbr = ConfigBeanRegistry.instance();
            if (cbr != null) {
                Optional<Qualifier> configuredByQualifier = serviceInfo.qualifiers().stream()
                        .filter(q -> q.typeName().name().equals(ConfigDriven.class.getName()))
                        .findFirst();
                assert (configuredByQualifier.isPresent());
                cbr.bind(this, configuredByQualifier.get());
            }
        }
        // do this as the last thing, so our ancestor does not think we are already initialized when overwriting service info
        super.picoServices(picoServices);
    }

    @Override
    public void onPhaseEvent(Event event,
                             Phase phase) {
        if (phase == Phase.POST_BIND_ALL_MODULES) {
            assertIsInitializing();
            PicoServices picoServices = picoServices().orElseThrow();

            if (Phase.INIT == currentActivationPhase()) {
                LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.PENDING);
                startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PENDING);
            }

            // one of the configured services need to "tickle" the bean registry to initialize
            ConfigBeanRegistry cbr = ConfigBeanRegistry.instance();
            if (cbr != null) {
                cbr.initialize(picoServices);

                // pre-initialize ourselves
                if (isRootProvider()) {
                    // pre-activate our managed services
                    managedConfiguredServicesMap.values()
                            .forEach(this::innerPreActivateManagedService);
                }
            }
        } else if (phase == Phase.FINAL_RESOLVE) {
            // post-initialize ourselves
            if (isRootProvider()
                    && drivesActivation()) {
                ContextualServiceQuery query = ContextualServiceQuery
                        .builder().serviceInfoCriteria(PicoServices.EMPTY_CRITERIA)
                        .build();
                maybeActivate(query);
            }

            assertInitialized();
            resolveConfigDrivenServices();
        } else if (phase == Phase.SERVICES_READY) {
            assertIsInitialized();
            activateConfigDrivenServices();
        }
    }

    // note that all responsibilities to resolve is delegated to the root provider
    @Override
    public Optional<Object> resolve(InjectionPointInfo ipInfo,
                                    PicoServices picoServices,
                                    ServiceProvider<?> serviceProvider,
                                    boolean resolveIps) {
        if (resolveIps) {
            assert (isRootProvider());
            // too early to resolve...
            return Optional.empty();
        }

        ServiceInfoCriteria dep = ipInfo.dependencyToServiceInfo();
        ServiceInfoCriteria criteria = ServiceInfoCriteria.builder()
                .addContractImplemented(configBeanType())
                .build();
        if (!dep.matchesContracts(criteria)) {
            return Optional.empty();    // we are being injected with neither a config bean nor a service that matches ourselves
        }

        // if we are here then we are asking for a config bean for ourselves, or a slave/managed instance
        if (!dep.qualifiers().isEmpty()) {
            throw new InjectionException("cannot use qualifiers while injecting config beans for self", this);
        }

        if (isRootProvider()) {
            return Optional.of(configBeanType());
        }

        return Optional.of(configBean());
    }

    /**
     * Here we are only looking for service providers, not service instances. What we need to do here is to determine
     * whether to (a) include root providers, (b) include slave providers, or (c) include both.
     * <p>
     * The result depends on the type of this provider instance.
     * Here is the heuristic:
     * <ul>
     * <li> if this is a slave then simply use the standard matching behavior.
     * <p>
     * If, however, we are the root provider then the additional heuristic is applied:
     * <li> if the request mentions the {@link ConfigDriven} qualifier w/ no value specified
     * then the caller is only interested in the root provider.
     * <li> if the request mentions the {@link ConfigDriven} qualifier w/ a value specified
     * then the caller is only interested in the slave providers.
     * <li> if the request is completely empty then they are interested in everything - the root
     * provider as well as the slave providers.
     * <li> if there is no slaves under management then they must be interested in the root provider.
     * <li> the fallback is to use standard matching using the criteria provided and only include the slaves.
     * </ul>
     *
     * @param criteria           the injection point criteria that must match
     * @param wantThis           if this instance matches criteria, do we want to return this instance as part of the result
     * @param thisAlreadyMatches an optimization that signals to the implementation that this instance has already
     *                           matched using the standard service info matching checks
     * @return the list of matching service providers based upon the context and criteria provided
     */
    @Override
    public List<ServiceProvider<?>> serviceProviders(ServiceInfoCriteria criteria,
                                                     boolean wantThis,
                                                     boolean thisAlreadyMatches) {
        if (isRootProvider()) {
            Set<Qualifier> qualifiers = criteria.qualifiers();
            Optional<? extends Annotation> configuredByQualifier = Annotations
                    .findFirst(EMPTY_CONFIGURED_BY.typeName(), qualifiers);
            boolean hasValue = configuredByQualifier.isPresent()
                    && hasValue(configuredByQualifier.get().value().orElse(null));
            boolean blankCriteria = qualifiers.isEmpty() && isBlank(criteria);
            boolean slavesQualify = !managedConfiguredServicesMap.isEmpty()
                    && (blankCriteria || hasValue || configuredByQualifier.isEmpty());
            boolean rootQualifies = wantThis
                    && (
                    blankCriteria
                            || (
                            managedConfiguredServicesMap.isEmpty()
                                    && (
                                    qualifiers.isEmpty()
                                            || qualifiers.contains(WILDCARD_NAMED))
                                    || (!hasValue && configuredByQualifier.isPresent())));

            if (slavesQualify) {
                List<ServiceProvider<?>> slaves = new ArrayList<>(managedServiceProviders(criteria)
                                                                          .values());

                if (rootQualifies) {
                    List<ServiceProvider<?>> result = new ArrayList<>();
                    if (thisAlreadyMatches || serviceInfo().matches(criteria)) {
                        result.add(this);
                    }
                    result.addAll(slaves);
                    // no need to sort using the comparator here since we should already be in the proper order...
                    return result;
                } else {
                    return slaves;
                }
            } else if (rootQualifies
                    && (thisAlreadyMatches || serviceInfo().matches(criteria))) {
                if (!hasValue && managedConfiguredServicesMap.isEmpty()) {
                    return List.of(new UnconfiguredServiceProvider<>(this));
                }
                return List.of(this);
            }
        } else {    // this is a slave instance ...
            if (thisAlreadyMatches || serviceInfo().matches(criteria)) {
                return List.of(this);
            }
        }

        return List.of();
    }

    @Override
    public Map<String, ConfigDrivenServiceProviderBase<?, CB>> managedServiceProviders(ServiceInfoCriteria criteria) {
        if (!isRootProvider()) {
            assert (managedConfiguredServicesMap.isEmpty());
            return Map.of();
        }

        Map<String, ConfigDrivenServiceProviderBase<?, CB>> map = managedConfiguredServicesMap.entrySet()
                .stream()
                .filter(e -> e.getValue().serviceInfo().matches(criteria))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (map.size() <= 1) {
            return map;
        }

        Map<String, ConfigDrivenServiceProviderBase<?, CB>> result = new TreeMap<>(NameComparator.instance());
        result.putAll(map);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<T> first(ContextualServiceQuery query) {
        if (!isRootProvider()) {
            return maybeActivate(query);
        }

        // we are root provider
        if (Phase.ACTIVE != currentActivationPhase()) {
            LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.ACTIVE);
            startTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVE);
        }

        ServiceInfoCriteria criteria = query.serviceInfoCriteria();
        List<ServiceProvider<?>> qualifiedProviders = serviceProviders(criteria, false, true);
        for (ServiceProvider<?> qualifiedProvider : qualifiedProviders) {
            assert (this != qualifiedProvider);
            Optional<?> serviceOrProvider = qualifiedProvider.first(query);
            if (serviceOrProvider.isPresent()) {
                return (Optional<T>) serviceOrProvider;
            }
        }

        if (query.expected()) {
            throw expectedQualifiedServiceError(query);
        }

        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> list(ContextualServiceQuery query) {
        if (!isRootProvider()) {
            Optional<T> serviceOrProvider = maybeActivate(query);
            if (query.expected() && serviceOrProvider.isEmpty()) {
                throw expectedQualifiedServiceError(query);
            }
            return serviceOrProvider.map(List::of).orElseGet(List::of);
        }

        // we are root
        Map<String, ConfigDrivenServiceProviderBase<?, CB>> matching = managedServiceProviders(query.serviceInfoCriteria());
        if (!matching.isEmpty()) {
            List<?> result = matching.values().stream()
                    .map(it -> it.first(query))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!result.isEmpty()) {
                return (List<T>) result;
            }
        }

        if (!query.expected()) {
            return List.of();
        }

        throw expectedQualifiedServiceError(query);
    }

    /**
     * Configurable services by their very nature are not compile-time bindable during application creation.
     *
     * @return empty, signaling that we are not bindable
     */
    @Override
    public Optional<ServiceProviderBindable<T>> serviceProviderBindable() {
        return Optional.empty();
    }

    /**
     * Creates a new instance of this type of configured service provider, along with the configuration bean
     * associated with the service.
     *
     * @param configBean the config bean
     * @return the created instance injected with the provided config bean
     */
    protected abstract ConfigDrivenServiceProviderBase<T, CB> createInstance(NamedInstance<CB> configBean);

    /**
     * After the gathering dependency phase, we will short circuit directly to the finish line.
     */
    @Override
    protected void doConstructing(LogEntryAndResult logEntryAndResult) {
        if (isRootProvider()) {
            boolean shouldBeActive = (drivesActivation() && !managedConfiguredServicesMap.isEmpty());
            Phase setPhase = (shouldBeActive) ? Phase.ACTIVE : Phase.PENDING;
            startTransitionCurrentActivationPhase(logEntryAndResult, setPhase);
            onFinished(logEntryAndResult);
            return;
        }

        super.doConstructing(logEntryAndResult);
    }

    @Override
    protected String identitySuffix() {
        return "{" + instanceId + "}";
    }

    /**
     * Instance id associated with this instance.
     *
     * @return instance id
     */
    protected String instanceId() {
        return instanceId;
    }

    @Override
    protected void serviceInfo(ServiceInfo serviceInfo) {
        // this might appear strange, but since activators can inherit from one another this is in place to trigger
        // only when the most derived activator ctor is setting its serviceInfo.
        boolean isThisOurServiceInfo = TypeName.create(serviceType()).equals(serviceInfo.serviceTypeName());
        if (isThisOurServiceInfo) {
            assertIsInitializing();
            assertIsRootProvider(true, false);

            // override our service info to account for any named lookup
            if (isRootProvider() && !serviceInfo.qualifiers().contains(WILDCARD_NAMED)) {
                serviceInfo = ServiceInfo.builder(serviceInfo)
                        .addQualifier(WILDCARD_NAMED)
                        .build();
            }
        }

        super.serviceInfo(serviceInfo);
    }

    @Override
    protected System.Logger logger() {
        return LOGGER;
    }

    @Override
    protected void doPreDestroying(LogEntryAndResult logEntryAndResult) {
        if (isRootProvider()) {
            managedConfiguredServicesMap.values()
                    .forEach(csp -> {
                        LogEntryAndResult cspLogEntryAndResult = csp.createLogEntryAndResult(Phase.DESTROYED);
                        csp.doPreDestroying(cspLogEntryAndResult);
                    });
        }
        super.doPreDestroying(logEntryAndResult);
    }

    @Override
    protected void doDestroying(LogEntryAndResult logEntryAndResult) {
        super.doDestroying(logEntryAndResult);
    }

    @Override
    protected void onFinalShutdown() {
        if (isRootProvider()) {
            managedConfiguredServicesMap.values()
                    .stream()
                    .filter(csp -> csp.currentActivationPhase().eligibleForDeactivation())
                    .forEach(ConfigDrivenServiceProviderBase::onFinalShutdown);
        }

        this.initialized.set(false);
        this.managedConfiguredServicesMap.clear();

        super.onFinalShutdown();
    }

    /**
     * Maybe transition into being a root provider if we are the first to claim it. Otherwise, we are a slave being managed.
     *
     * @param isRootProvider true if an asserting is being made to claim root or claim managed slave
     * @param expectSet      true if this is a strong assertion, and if not claimed an exception will be thrown
     */
    // special note: this is referred to in code generated code!
    protected void assertIsRootProvider(boolean isRootProvider,
                                        boolean expectSet) {
        boolean set = this.isRootProvider.compareAndSet(null, isRootProvider);
        if (!set && expectSet) {
            throw new PicoServiceProviderException(description() + " was already initialized", null, this);
        }
        assert (!isRootProvider || rootProvider.get() == null);
    }

    /**
     * Return true if this service is driven to activation during startup (and provided it has some config).
     * See {@link io.helidon.pico.configdriven.api.ConfigDriven#activateByDefault()}.
     *
     * @return true if this service is driven to activation during startup
     */
    protected abstract boolean drivesActivation();

    /**
     * Transition into an initialized state.
     */
    void assertInitialized() {
        assertIsInitializing();
        assert (
                !drivesActivation()
                        || isAlreadyAtTargetPhase(PicoServices.terminalActivationPhase())
                        || managedConfiguredServicesMap.isEmpty());
        this.initialized.set(true);
    }

    void assertIsInitializing() {
        if (initialized.get()) {
            CallingContext callingContext = initializationCallingContext.get();
            String desc = description() + " was previously initialized";
            String msg = (callingContext == null) ? toErrorMessage(desc) : toErrorMessage(callingContext, desc);
            throw new PicoServiceProviderException(msg, this);
        }
    }

    void assertIsInitialized() {
        if (!initialized.get()) {
            throw new PicoServiceProviderException(description() + " was expected to be initialized", this);
        }
    }

    void resolveConfigDrivenServices() {
        assertIsInitialized();
        assert (isRootProvider());

        if (managedConfiguredServicesMap.isEmpty()) {
            if (logger().isLoggable(System.Logger.Level.DEBUG)) {
                logger().log(System.Logger.Level.DEBUG, "no configured services for: " + description());
            }
            return;
        }

        // accept and resolve config
        managedConfiguredServicesMap.values().forEach(csp -> {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Resolving config for " + csp);
            }

            LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.PENDING);
            try {
                csp.startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PENDING);
            } catch (Throwable t) {
                csp.onFailedFinish(logEntryAndResult, t, true);
            }
        });
    }

    protected final List<NamedInstance<CB>> createRepeatableBeans(Config config,
                                                                  boolean wantDefault,
                                                                  Function<Config, CB> factory) {
        Map<String, NamedInstance<CB>> instances = new TreeMap<>(NameComparator.instance());

        List<Config> childNodes = config.asNodeList().orElseGet(List::of);
        boolean isList = config.isList();

        for (Config childNode : childNodes) {
            String name = childNode.name(); // by default use the current node name - for lists, this would be the index
            name = isList ? childNode.get("name").asString().orElse(name) : name; // use "name" node if list and present
            instances.put(name, new NamedInstance<>(factory.apply(childNode), name));
        }

        if (wantDefault && !instances.containsKey(NamedInstance.DEFAULT_NAME)) {
            instances.put(NamedInstance.DEFAULT_NAME,
                          new NamedInstance<>(factory.apply(Config.empty()), NamedInstance.DEFAULT_NAME));
        }

        return List.copyOf(instances.values());
    }

    private void activateConfigDrivenServices() {
        assertIsInitialized();
        assert (isRootProvider());

        if (managedConfiguredServicesMap.isEmpty()) {
            return;
        }

        if (!drivesActivation()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "drivesActivation disabled for: " + description());
            }
            return;
        }

        managedConfiguredServicesMap.values().forEach(ConfigDrivenServiceProviderBase::activateManagedService);
    }

    private void innerActivate() {
        // this may go into a wait state if other threads are trying to also initialize at the same time - expected behavior
        ContextualServiceQuery query = ContextualServiceQuery
                .builder().serviceInfoCriteria(PicoServices.EMPTY_CRITERIA)
                .build();
        Optional<T> service = maybeActivate(query); // triggers the post-construct
        if (service.isPresent() && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "finished activating: " + service);
        }
    }

    private void activateManagedService() {
        if (Phase.ACTIVE != currentActivationPhase()) {
            innerActivate();
        }
    }

    private void innerPreActivateManagedService(ConfigDrivenServiceProviderBase<T, CB> instance) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "creating: " + serviceType()
                    + " with config instance id: " + instance.instanceId());
        }

        // cannot pre-activate root itself, and this method is ONLY called on root provider
        assert (instance != this);

        ServiceInfo newServiceInfo = ServiceInfo.builder(instance.serviceInfo())
                .addQualifier(Qualifier.createNamed(instance.instanceId()))
                .build();

        // override our service info
        instance.serviceInfo(newServiceInfo);
        instance.picoServices(picoServices());
        instance.rootProvider(this);

        if (logger().isLoggable(System.Logger.Level.DEBUG)) {
            logger().log(System.Logger.Level.DEBUG, "config instance successfully initialized: "
                    + id() + ":" + newServiceInfo.qualifiers());
        }
    }

}
