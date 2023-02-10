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

package io.helidon.pico.configdriven.services;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.BasicConfigBeanRegistry;
import io.helidon.builder.config.spi.BasicConfigResolver;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.builder.types.AnnotationAndValue;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.pico.ContextualServiceQuery;
import io.helidon.pico.DefaultContextualServiceQuery;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.Event;
import io.helidon.pico.InjectionException;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.configdriven.ConfiguredBy;
import io.helidon.pico.services.AbstractServiceProvider;
import io.helidon.pico.spi.CallingContext;
import io.helidon.pico.spi.InjectionResolver;

import static io.helidon.pico.configdriven.services.Utils.hasValue;
import static io.helidon.pico.configdriven.services.Utils.isBlank;
import static io.helidon.pico.spi.CallingContext.maybeCreate;
import static io.helidon.pico.spi.CallingContext.toErrorMessage;

/**
 * Abstract base for any config-driven-service.
 *
 * @param <T> the type of the service this provider manages
 * @param <CB> the type of config beans that this service is configured by
 */
// special note: many of these methods are referenced in code generated code!
public abstract class AbstractConfiguredServiceProvider<T, CB> extends AbstractServiceProvider<T>
        implements ConfiguredServiceProvider<T, CB>,
                   ServiceProviderProvider,
                   InjectionPointProvider<T>,
                   InjectionResolver {
    private static final QualifierAndValue EMPTY_CONFIGURED_BY = DefaultQualifierAndValue.create(ConfiguredBy.class);
    private static final CBInstanceComparator BEAN_INSTANCE_ID_COMPARATOR = new CBInstanceComparator();
    private static final System.Logger LOGGER = System.getLogger(AbstractConfiguredServiceProvider.class.getName());

    private final LazyValue<InternalConfigBeanRegistry> configBeanRegistry = LazyValue.create(() -> resolveConfigBeanRegistry());

    private final AtomicReference<Boolean> isRootProvider = new AtomicReference<>();    // this one indicates intention
    private final AtomicReference<ConfiguredServiceProvider<T, CB>> rootProvider = new AtomicReference<>();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicReference<CallingContext> initializationCallingContext
            = new AtomicReference<>();  // used only when we are in pico.debug mode
    private final Map<String, Object> configBeanMap
            = new TreeMap<>(BEAN_INSTANCE_ID_COMPARATOR);
    private final Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> managedConfiguredServicesMap
            = new ConcurrentHashMap<>();

    /**
     * The default constructor.
     */
    protected AbstractConfiguredServiceProvider() {
    }

    @Override
    protected System.Logger logger() {
        return LOGGER;
    }

    /**
     * The map of bean instance id's to config bean instances.
     *
     * @return map of bean instance id's to config bean instances
     */
    public Map<String, Object> configBeanMap() {
        return Map.copyOf(configBeanMap);
    }

    /**
     * The map of bean instance id's to managed configured service providers that this instance managed directly.
     *
     * @return map of bean instance id to managed configured service providers
     */
    public Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> configuredServicesMap() {
        return Collections.unmodifiableMap(managedConfiguredServicesMap);
    }

    /**
     * Called during initialization to register a loaded config bean.
     *
     * @param instanceId the config bean instance id
     * @param configBean the config bean
     */
    public void registerConfigBean(
            String instanceId,
            Object configBean) {
        Objects.requireNonNull(instanceId);
        Objects.requireNonNull(configBean);
        assertIsInitializing();

        Object prev = configBeanMap.put(instanceId, configBean);
        assert (Objects.isNull(prev));

        prev = managedConfiguredServicesMap.put(instanceId, Optional.empty());
        assert (Objects.isNull(prev));
    }

    @Override
    public boolean reset(
            boolean deep) {
        super.reset(deep);
        configBeanMap.clear();
        managedConfiguredServicesMap.clear();
        isRootProvider.set(null);
        rootProvider.set(null);
        initialized.set(false);
        initializationCallingContext.set(null);
        return true;
    }

    void assertIsInitializing() {
        if (initialized.get()) {
            CallingContext callingContext = initializationCallingContext.get();
            throw new PicoServiceProviderException(
                    toErrorMessage(Optional.ofNullable(callingContext),
                                   description() + " was previously initialized"), this);
        }
    }

    void assertIsInitialized() {
        if (!initialized.get()) {
            throw new PicoServiceProviderException(description() + " was expected to be initialized", this);
        }
    }

    @Override
    protected void doPreDestroying(
            LogEntryAndResult logEntryAndResult) {
        if (isRootProvider()) {
            managedConfiguredServicesMap.values().stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(csp -> {
                        LogEntryAndResult cspLogEntryAndResult = csp.createLogEntryAndResult(Phase.DESTROYED);
                        csp.doPreDestroying(cspLogEntryAndResult);
                    });
        }
        super.doPreDestroying(logEntryAndResult);
    }

    @Override
    protected void doDestroying(
            LogEntryAndResult logEntryAndResult) {
        super.doDestroying(logEntryAndResult);
    }

    @Override
    protected void onFinalShutdown() {
        if (isRootProvider()) {
            managedConfiguredServicesMap.values().stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(csp -> csp.currentActivationPhase().eligibleForDeactivation())
                    .forEach(AbstractConfiguredServiceProvider::onFinalShutdown);
        }

        this.initialized.set(false);
        this.managedConfiguredServicesMap.clear();
        this.configBeanMap.clear();

        super.onFinalShutdown();
    }

    /**
     * Transition into an initialized state.
     */
    void assertInitialized(
            boolean initialized) {
        assertIsInitializing();
        assert (!drivesActivation()
                        || isAlreadyAtTargetPhase(PicoServices.terminalActivationPhase())
                        || managedConfiguredServicesMap.isEmpty());
        this.initialized.set(initialized);
    }

    /**
     * Maybe transition into being a root provider if we are the first to claim it. Otherwise, we are a slave being managed.
     *
     * @param isRootProvider    true if an asserting is being made to claim root or claim managed slave
     * @param expectSet         true if this is a strong assertion, and if not claimed an exception will be thrown
     */
    // special note: this is referred to in code generated code!
    protected void assertIsRootProvider(
            boolean isRootProvider,
            boolean expectSet) {
        boolean set = this.isRootProvider.compareAndSet(null, isRootProvider);
        if (!set && expectSet) {
            throw new PicoServiceProviderException(description() + " was already initialized", null, this);
        }
        assert (!isRootProvider || rootProvider.get() == null);
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
    @SuppressWarnings("unchecked")
    public void rootProvider(
            ServiceProvider<T> root) {
        assertIsRootProvider(false, false);
        assert (!isRootProvider() && rootProvider.get() == null && this != root);
        boolean set = rootProvider.compareAndSet(null,
                                   (AbstractConfiguredServiceProvider<T, CB>) Objects.requireNonNull(root));
        assert (set);
    }

    @Override
    protected String identitySuffix() {
        if (isRootProvider()) {
            return "{root}";
        }

        Optional<CB> configBean = configBean();
        String instanceId = toConfigBeanInstanceId(configBean.orElse(null));
        return "{" + instanceId + "}";
    }

    @Override
    protected void serviceInfo(
            ServiceInfo serviceInfo) {
        // this might appear strange, but since activators can inherit from one another this is in place to trigger
        // only when the most derived activator ctor is setting its serviceInfo.
        boolean isThisOurServiceInfo = serviceType().getName().equals(serviceInfo.serviceTypeName());
        if (isThisOurServiceInfo) {
            assertIsInitializing();
            assertIsRootProvider(true, false);

            // override our service info to account for any named lookup
            if (isRootProvider() && !serviceInfo.qualifiers().contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
                serviceInfo = DefaultServiceInfo.toBuilder(serviceInfo)
                        .addQualifier(DefaultQualifierAndValue.WILDCARD_NAMED)
                        .build();
            }
        }

        super.serviceInfo(serviceInfo);
    }

    @Override
    public void picoServices(
            Optional<PicoServices> picoServices) {
        assertIsInitializing();
        assertIsRootProvider(true, false);

        super.picoServices(picoServices);

        if (isRootProvider()) {
            // override out service info to account for any named lookup
            ServiceInfo serviceInfo = Objects.requireNonNull(serviceInfo());
            if (!serviceInfo.qualifiers().contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
                serviceInfo = DefaultServiceInfo.toBuilder(serviceInfo)
                        .addQualifier(DefaultQualifierAndValue.WILDCARD_NAMED)
                        .build();
                serviceInfo(serviceInfo);
            }

            // bind to the config bean registry ...  but, don't yet resolve!
            InternalConfigBeanRegistry cbr = configBeanRegistry.get();
            if (cbr != null) {
                Optional<QualifierAndValue> configuredByQualifier = serviceInfo.qualifiers().stream()
                        .filter(q -> q.typeName().name().equals(ConfiguredBy.class.getName()))
                        .findFirst();
                assert (configuredByQualifier.isPresent());
                cbr.bind(this, configuredByQualifier.get(), metaConfigBeanInfo());
            }
        }
    }

    @Override
    public void onPhaseEvent(
            Event event,
            Phase phase) {
        if (phase == Phase.POST_BIND_ALL_MODULES) {
            assertIsInitializing();
            PicoServices picoServices = picoServices();
            assert (Objects.nonNull(picoServices));

            if (Phase.INIT == currentActivationPhase()) {
                LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.PENDING);
                startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PENDING);
            }

            // one of the configured services need to "tickle" the bean registry to initialize
            InternalConfigBeanRegistry cbr = configBeanRegistry.get();
            if (cbr != null) {
                cbr.initialize(picoServices);

                // pre-initialize ourselves
                if (isRootProvider()) {
                    // pre-activate our managed services
                    configBeanMap.forEach(this::preActivateManagedService);
                }
            }
        } else if (phase == Phase.FINAL_RESOLVE) {
            // post-initialize ourselves
            if (isRootProvider()) {
                if (drivesActivation()) {
                    ContextualServiceQuery query = DefaultContextualServiceQuery
                            .builder().serviceInfoCriteria(PicoServices.EMPTY_CRITERIA)
                            .build();
                    maybeActivate(query);
                }
            }

            assertInitialized(true);
            resolveConfigDrivenServices();
        } else if (phase == Phase.SERVICES_READY) {
            assertIsInitialized();
            activateConfigDrivenServices();
        }
    }

    @Override
    // not that it is expected that the generated services override this method - which will override the getAnnotation() call.
    public Class<?> configBeanType() {
        Class<?> serviceType = serviceType();
        ConfiguredBy configuredBy =
                Objects.requireNonNull(serviceType.getAnnotation(ConfiguredBy.class), String.valueOf(serviceType));
        return Objects.requireNonNull(configuredBy, String.valueOf(serviceType)).value();
    }

    @Override
    public MetaConfigBeanInfo metaConfigBeanInfo() {
        Map<String, Object> meta = configBeanAttributes().get(BasicConfigResolver.TAG_META);
        if (meta != null) {
            ConfigBeanInfo cbi = (ConfigBeanInfo) meta.get(ConfigBeanInfo.class.getName());
            if (cbi != null) {
                // normal path
                return MetaConfigBeanInfo.toBuilder(cbi).build();
            }

            return ConfigBeanInfo.toMetaConfigBeanInfo(meta);
        }

        LOGGER.log(System.Logger.Level.WARNING, "Unusual to find config bean without meta attributes: " + this);
        Class<?> configBeanType = configBeanType();
        ConfigBean configBean =
                Objects.requireNonNull(configBeanType.getAnnotation(ConfigBean.class), String.valueOf(serviceType()));
        return ConfigBeanInfo.toMetaConfigBeanInfo(configBean, configBeanType);
    }

    @Override
    public Map<String, Map<String, Object>> configBeanAttributes() {
        return Map.of();
    }

    /**
     * The backing config of this configured service instance.
     *
     * @return the backing config of this configured service instance
     */
    protected abstract Optional<io.helidon.common.config.Config> rawConfig();

    @Override
    public abstract String toConfigBeanInstanceId(
            CB configBean);

    /**
     * Brokers the set of the instance id for the given config bean.
     *
     * @param configBean the config bean to set
     * @param val the instance id to associate it with
     */
    public abstract void configBeanInstanceId(
            CB configBean,
            String val);

    /**
     * Creates a new instance of this type of configured service provider, along with the configuration bean
     * associated with the service.
     *
     * @param configBean the config bean
     * @return the created instance injected with the provided config bean
     */
    protected abstract AbstractConfiguredServiceProvider<T, CB> createInstance(
            Object configBean);

    /**
     * After the gathering dependency phase, we will short circuit directly to the finish line.
     */
    @Override
    protected void doConstructing(
            LogEntryAndResult logEntryAndResult) {
        if (isRootProvider()) {
            boolean shouldBeActive = (drivesActivation() && !managedConfiguredServicesMap.isEmpty());
            Phase setPhase = (shouldBeActive) ? Phase.ACTIVE : Phase.PENDING;
            startTransitionCurrentActivationPhase(logEntryAndResult, setPhase);
            onFinished(logEntryAndResult);
            return;
        }

        super.doConstructing(logEntryAndResult);
    }

    // note that all responsibilities to resolve is delegated to the root provider
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Object> resolve(
            InjectionPointInfo ipInfo,
            PicoServices picoServices,
            ServiceProvider<?> serviceProvider,
            boolean resolveIps) {
        if (resolveIps) {
            assert (isRootProvider());
            // too early to resolve...
            return Optional.empty();
        }

        ServiceInfoCriteria dep = ipInfo.dependencyToServiceInfo();
        DefaultServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(configBeanType().getName())
                .build();
        if (!dep.matchesContracts(criteria)) {
            return Optional.empty();
        }

        // if we are here then we are asking for a config bean for ourselves, or a slave/managed instance
        if (!dep.qualifiers().isEmpty()) {
            throw new InjectionException("cannot use qualifiers while injecting config beans for self", this);
        }

        if (isRootProvider()) {
            return Optional.of(configBeanType());
        }

        return (Optional<Object>) configBean();
    }

    /**
     * Here we are only looking for service providers, not service instances. What we need to do here is to determine
     * whether to (a) include root providers, (b) include slave providers, or (c) include both.
     * <p>
     * The result depends on the type of this provider instance.
     * Here is the heuristic:
     * <ul>
     * <li> if this is a slave then simply use the standard matching behavior.
     *
     * If, however, we are the root provider then the additional heuristic is applied:
     * <li> if the request mentions the {@link ConfiguredBy} qualifier w/ no value specified
     * then the caller is only interested in the root provider.
     * <li> if the request mentions the {@link ConfiguredBy} qualifier w/ a value specified
     * then the caller is only interested in the slave providers.
     * <li> if the request is completely empty then they are interested in everything - the root
     * provider as well as the slave providers.
     * <li> if there is no slaves under management then they must be interested in the root provider.
     * <li> the fallback is to use standard matching using the criteria provided and only include the slaves.
     * </ul>
     *
     * @param criteria              the injection point criteria that must match
     * @param wantThis              if this instance matches criteria, do we want to return this instance as part of the result
     * @param thisAlreadyMatches    an optimization that signals to the implementation that this instance has already
     *                              matched using the standard service info matching checks
     * @return the set of matching service providers based upon the context and criteria provided
     */
    @Override
    public List<ServiceProvider<?>> serviceProviders(
            ServiceInfoCriteria criteria,
            boolean wantThis,
            boolean thisAlreadyMatches) {
        if (isRootProvider()) {
            Set<QualifierAndValue> qualifiers = criteria.qualifiers();
            Optional<? extends AnnotationAndValue> configuredByQualifier = DefaultQualifierAndValue
                    .findFirst(EMPTY_CONFIGURED_BY.typeName().name(), qualifiers);
            boolean hasValue = configuredByQualifier.isPresent()
                    && hasValue(configuredByQualifier.get().value().orElse(null));
            boolean blankCriteria = qualifiers.isEmpty() && isBlank(criteria);
            boolean slavesQualify = !managedConfiguredServicesMap.isEmpty()
                    && (blankCriteria || hasValue || configuredByQualifier.isEmpty());
            boolean rootQualifies = wantThis
                    && (blankCriteria
                                || managedConfiguredServicesMap.isEmpty()
                                || (!hasValue && configuredByQualifier.isPresent()));

            if (slavesQualify) {
                List<ServiceProvider<?>> slaves = managedServiceProviders(criteria)
                        .entrySet().stream()
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList());

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
    public Map<String, AbstractConfiguredServiceProvider<?, CB>> managedServiceProviders(
            ServiceInfoCriteria criteria) {
        if (!isRootProvider()) {
            assert (managedConfiguredServicesMap.isEmpty());
            return Map.of();
        }

        Map<String, AbstractConfiguredServiceProvider<?, CB>> map = managedConfiguredServicesMap.entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .filter(e -> e.getValue().get().serviceInfo().matches(criteria))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        if (map.size() <= 1) {
            return map;
        }

        Map<String, AbstractConfiguredServiceProvider<?, CB>> result = new TreeMap<>(configBeanComparator());
        result.putAll(map);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<T> first(
            ContextualServiceQuery query) {
        if (!isRootProvider()) {
            Optional<T> serviceOrProvider = maybeActivate(query);
            return serviceOrProvider;
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
    public List<T> list(
            ContextualServiceQuery query) {
        if (!isRootProvider()) {
            Optional<T> serviceOrProvider = maybeActivate(query);
            if (query.expected() && serviceOrProvider.isEmpty()) {
                throw expectedQualifiedServiceError(query);
            }
            return serviceOrProvider.map(List::of).orElseGet(List::of);
        }

        // we are root
        Map<String, AbstractConfiguredServiceProvider<?, CB>> matching = managedServiceProviders(query.serviceInfoCriteria());
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

    void resolveConfigDrivenServices() {
        assertIsInitialized();
        assert (isRootProvider());
        assert (managedConfiguredServicesMap.size() == configBeanMap.size()) : description();

        if (managedConfiguredServicesMap.isEmpty()) {
            if (logger().isLoggable(System.Logger.Level.DEBUG)) {
                logger().log(System.Logger.Level.DEBUG, "no configured services for: " + description());
            }
            return;
        }

        // accept and resolve config
        managedConfiguredServicesMap.values().forEach(opt -> {
            assert (opt.isPresent());

            AbstractConfiguredServiceProvider<?, CB> csp = opt.get();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Resolving config for " + csp);
            }

            LogEntryAndResult logEntryAndResult = createLogEntryAndResult(Phase.PENDING);
            try {
                csp.startTransitionCurrentActivationPhase(logEntryAndResult, Phase.PENDING);
                io.helidon.common.config.Config commonConfig = PicoServices.realizedGlobalBootStrap().config()
                        .orElseThrow(this::expectedConfigurationSetGlobally);
                csp.acceptConfig(commonConfig);
            } catch (Throwable t) {
                csp.onFailedFinish(logEntryAndResult, t, true);
            }
        });
    }

    /**
     * Called to accept the new config bean instance initialized from the appropriate configuration tree location.
     *
     * @param config the configuration
     * @return the new config bean
     */
    // expected that the generated configured service overrides this to set its new config bean value
    protected CB acceptConfig(
            Config config) {
        return Objects.requireNonNull(toConfigBean(config));
    }

    private PicoException expectedConfigurationSetGlobally() {
        return new PicoException("expected to have configuration set globally - see PicoServices.globalBootstrap()");
    }

    private void activateConfigDrivenServices() {
        assertIsInitialized();
        assert (isRootProvider());
        assert (managedConfiguredServicesMap.size() == configBeanMap.size()) : description();

        if (configBeanMap.isEmpty()) {
            return;
        }

        if (!drivesActivation()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "drivesActivation disabled for: " + description());
            }
            return;
        }

        configBeanMap.forEach(this::activateManagedService);
    }

    private AbstractConfiguredServiceProvider<T, CB> activateManagedService(
            String instanceId,
            Object configBean) {
        return managedConfiguredServicesMap.compute(instanceId, (id, existing) -> {
            if (existing == null || existing.isEmpty()) {
                existing = innerPreActivateManagedService(instanceId, configBean);
            }

            AbstractConfiguredServiceProvider<T, CB> csp = existing.get();
            if (Phase.ACTIVE != csp.currentActivationPhase()) {
                csp.innerActivate();
            }
            return existing;
        }).get();
    }

    private void innerActivate() {
        // this may go into a wait state if other threads are trying to also initialize at the same time - expected behavior
        ContextualServiceQuery query = DefaultContextualServiceQuery
                .builder().serviceInfoCriteria(PicoServices.EMPTY_CRITERIA)
                .build();
        Optional<T> service = maybeActivate(query); // triggers the post-construct
        if (service.isPresent() && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "finished activating: " + service);
        }
    }

    private AbstractConfiguredServiceProvider<T, CB> preActivateManagedService(
            String instanceId,
            Object configBean) {
        return managedConfiguredServicesMap.compute(instanceId, (id, existing) -> {
            if (existing != null && existing.isPresent()) {
                return existing;
            }
            return innerPreActivateManagedService(instanceId, configBean);
        }).get();
    }

    private Optional<AbstractConfiguredServiceProvider<T, CB>> innerPreActivateManagedService(
            String instanceId,
            Object configBean) {
        Objects.requireNonNull(instanceId);
        Objects.requireNonNull(configBean);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "creating: " + serviceType() + " with config instance id: " + instanceId);
        }

        AbstractConfiguredServiceProvider<T, CB> instance = createInstance(configBean);
        assert (instance != this);

        DefaultServiceInfo newServiceInfo = DefaultServiceInfo.toBuilder(instance.serviceInfo())
                .addQualifier(DefaultQualifierAndValue.createNamed(instanceId))
                .build();

        // override our service info
        instance.serviceInfo(newServiceInfo);
        instance.picoServices(Optional.of(picoServices()));
        instance.rootProvider(this);

        if (logger().isLoggable(System.Logger.Level.DEBUG)) {
            logger().log(System.Logger.Level.DEBUG, "config instance successfully initialized: "
                    + id() + ":" + newServiceInfo.qualifiers());
        }

        return Optional.of(instance);
    }

    /**
     * Return true if this service is driven to activation during startup (and provided it has some config).
     * See {@link io.helidon.pico.configdriven.ConfiguredBy#drivesActivation()} and
     * see {@link io.helidon.builder.config.ConfigBean#drivesActivation()} for more.
     * @return true if this service is driven to activation during startup
     */
    // note: overridden by the service if disabled at the ConfiguredBy service level
    protected boolean drivesActivation() {
        return metaConfigBeanInfo().drivesActivation();
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
     * The special comparator for ordering config bean instance ids.
     *
     * @return the special comparator for ordering config bean instance ids
     */
    static Comparator<String> configBeanComparator() {
        return BEAN_INSTANCE_ID_COMPARATOR;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Config, CB> Optional<CB> toConfigBean(
            C config,
            Class<CB> configBeanType) {
        CB configBean = (CB) toConfigBean(config);
        assert (configBeanType.isInstance(configBean)) : configBean;
        return Optional.of(configBean);
    }

    /**
     * See {@link #configBeanComparator()}.
     */
    static class CBInstanceComparator implements Comparator<String>, Serializable {
        @Override
        public int compare(
                String str1,
                String str2) {
            if (DefaultConfigBeanRegistry.DEFAULT_INSTANCE_ID.equals(str1)) {
                return -1 * Integer.MAX_VALUE;
            } else if (DefaultConfigBeanRegistry.DEFAULT_INSTANCE_ID.equals(str2)) {
                return Integer.MAX_VALUE;
            }
            return str1.compareTo(str2);
        }
    }

    InternalConfigBeanRegistry resolveConfigBeanRegistry() {
        BasicConfigBeanRegistry cbr = ConfigBeanRegistryHolder.configBeanRegistry().orElse(null);
        if (cbr == null) {
            LOGGER.log(System.Logger.Level.INFO, "Config-Driven Services disabled (config bean registry not found");
        } else if (!(cbr instanceof InternalConfigBeanRegistry)) {
            throw new PicoServiceProviderException(
                    toErrorMessage(maybeCreate(), "Config-Driven Services disabled (unsupported implementation): " + cbr), this);
        }

        return (InternalConfigBeanRegistry) cbr;
    }

}
