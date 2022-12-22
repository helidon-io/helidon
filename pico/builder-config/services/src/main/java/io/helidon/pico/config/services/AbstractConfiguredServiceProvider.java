/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.config.services;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
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

import io.helidon.config.Config;
import io.helidon.pico.config.api.ConfigBean;
import io.helidon.pico.config.api.ConfiguredBy;
import io.helidon.pico.config.services.impl.DefaultConfigBeanRegistry;
import io.helidon.pico.config.services.impl.UnconfiguredServiceProvider;
import io.helidon.pico.config.spi.ConfigBeanInfo;
import io.helidon.pico.config.spi.ConfigResolver;
import io.helidon.pico.config.spi.ConfigResolverProvider;
import io.helidon.pico.config.spi.MetaConfigBeanInfo;
import io.helidon.pico.ActivationPhase;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionException;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.ServiceProviderProvider;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.spi.ext.InjectionResolver;
import io.helidon.pico.types.AnnotationAndValue;

/**
 * Abstract base for any config-driven-service.
 *
 * @param <T> the type of the service this provider manages
 * @param <CB> the type of config beans that this service is configured by
 */
public abstract class AbstractConfiguredServiceProvider<T, CB> extends AbstractServiceProvider<T>
        implements ConfiguredServiceProvider<T, CB>,
                   ServiceProviderProvider,
                   InjectionPointProvider<T>,
                   InjectionResolver {
    private static final System.Logger LOGGER = System.getLogger(AbstractConfiguredServiceProvider.class.getName());
    private static final QualifierAndValue EMPTY_CONFIGURED_BY = DefaultQualifierAndValue.create(ConfiguredBy.class);
    private static final CBInstanceComparator BEAN_INSTANCE_ID_COMPARATOR = new CBInstanceComparator();

    private final AtomicReference<Boolean> isRootProvider = new AtomicReference<>();    // this one indicates intention
    private final AtomicReference<ConfiguredServiceProvider<T, CB>> rootProvider = new AtomicReference<>();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final Map<String, Object> configBeanMap
            = new TreeMap<>(BEAN_INSTANCE_ID_COMPARATOR);
    private final Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> managedConfiguredServicesMap
            = new ConcurrentHashMap<>();

    /**
     * @return map of bean instance id -> config bean instance
     */
    public Map<String, Object> getConfigBeanMap() {
        return Collections.unmodifiableMap(configBeanMap);
    }

    /**
     * @return map of bean instance id -> managed configured service provider (that this instance manages directly)
     */
    public Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> getConfiguredServicesMap() {
        return Collections.unmodifiableMap(managedConfiguredServicesMap);
    }

    /**
     * Called during initialization to register a loaded config bean.
     *
     * @param instanceId the config bean instance id
     * @param configBean the config bean
     */
    public void registerConfigBean(String instanceId, Object configBean) {
        assertIsInitializing();

        Object prev = configBeanMap.put(Objects.requireNonNull(instanceId), Objects.requireNonNull(configBean));
        assert (Objects.isNull(prev));

        prev = managedConfiguredServicesMap.put(instanceId, Optional.empty());
        assert (Objects.isNull(prev));
    }

    @Override
    public void reset() {
        super.reset();
        configBeanMap.clear();
        managedConfiguredServicesMap.clear();
        isRootProvider.set(null);
        rootProvider.set(null);
        initialized.set(false);
    }

    protected void assertIsInitializing() {
        if (initialized.get()) {
            throw new PicoServiceProviderException(description() + " was already initialized", null, this);
        }
    }

    protected void assertIsInitialized() {
        if (!initialized.get()) {
            throw new PicoServiceProviderException(description() + " was expected to be initialized", null, this);
        }
    }

    /**
     * Transition into an initialized state.
     */
    private void assertInitialized(boolean initialized) {
        assertIsInitializing();
        assert (!isAutoActivationEnabled() || isActive() || managedConfiguredServicesMap.isEmpty());
        this.initialized.set(initialized);
    }

    /**
     * Maybe transition into being a root provider if we are the first to claim it. Otherwise we are a slave being managed.
     *
     * @param isRootProvider    true if an asserting is being made to claim root or claim managed slave
     * @param expectSet         true if this is a strong assertion, and if not claimed an exception will be thrown
     */
    public void assertIsRootProvider(boolean isRootProvider, boolean expectSet) {
        boolean set = this.isRootProvider.compareAndSet(null, isRootProvider);
        if (!set && expectSet) {
            throw new PicoServiceProviderException(description() + " was already initialized", null, this);
        }
        assert (!isRootProvider || Objects.isNull(rootProvider.get()));
    }

    /**
     * @return returns true if this instance is the root provider for managed/slaved instances
     */
    @Override
    public boolean isRootProvider() {
        Boolean root = isRootProvider.get();
        return Objects.nonNull(root) && root && Objects.isNull(rootProvider.get());
    }

    @Override
    public ConfiguredServiceProvider<T, CB> rootProvider() {
        return rootProvider.get();
    }

    @Override
    public void rootProvider(ServiceProvider<T> root) {
        assertIsRootProvider(false, false);
        assert (!isRootProvider() && Objects.isNull(rootProvider.get()) && this != root);
        boolean set = rootProvider.compareAndSet(null,
                                   (AbstractConfiguredServiceProvider<T, CB>) Objects.requireNonNull(root));
        assert (set);
    }

    @Override
    protected String identitySuffix() {
        if (isRootProvider()) {
            return "{root}";
        } else if (Objects.nonNull(getConfigBean())) {
            String instanceId = getConfigBeanInstanceId(getConfigBean());
            if (Objects.nonNull(instanceId)) {
                return "{" + instanceId + "}";
            }
        }
        return "{null}";
    }

    @Override
    protected void setServiceInfo(DefaultServiceInfo serviceInfo) {
        // this might appear strange, but since activators can inherit from one another this is in place to trigger
        // only when the most derived activator ctor is setting its serviceInfo.
        boolean isThisOurServiceInfo = getServiceType().getName().equals(serviceInfo.serviceTypeName());
        if (isThisOurServiceInfo) {
            assertIsInitializing();
            assertIsRootProvider(true, false);

            // override our service info to account for any named lookups...
            if (isRootProvider() && !serviceInfo.qualifiers().contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
                serviceInfo = serviceInfo.toBuilder()
                        .qualifier(DefaultQualifierAndValue.WILDCARD_NAMED)
                        .build();
            }
        }

        super.setServiceInfo(serviceInfo);
    }

    @Override
    public void picoServices(PicoServices picoServices) {
        assertIsInitializing();
        assertIsRootProvider(true, false);

        super.picoServices(picoServices);

        if (isRootProvider()) {
            // override out service info to account for any named lookup...
            DefaultServiceInfo serviceInfo = Objects.requireNonNull(serviceInfo());
            if (!serviceInfo.qualifiers().contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
                serviceInfo = serviceInfo.toBuilder()
                        .qualifier(DefaultQualifierAndValue.WILDCARD_NAMED)
                        .build();
            }

            // bind to the config bean registry ...  but, don't yet resolve!
            ConfigBeanRegistry cbr = Objects.requireNonNull(ConfigBeanRegistryProvider.getInstance());
            Optional<QualifierAndValue> configuredByQualifier = serviceInfo.qualifiers().stream()
                    .filter(q -> q.typeName().name().equals(ConfiguredBy.class.getName()))
                    .findFirst();
            assert (configuredByQualifier.isPresent());
            ((InternalConfigBeanRegistry) cbr).bind(this, configuredByQualifier.get(), getConfigBeanInfo());
        }
    }

    @Override
    public void onEvent(Event event) {
        if (event == Event.POST_BIND_ALL_MODULES) {
            assertIsInitializing();
            PicoServices picoServices = picoServices();
            assert (Objects.nonNull(picoServices));

            if (ActivationPhase.INIT == currentActivationPhase()) {
                setPhase(null, ActivationPhase.PENDING);
            }

            // one of the configured services need to "tickle" the bean registry to initialize...
            ConfigBeanRegistry cbr = Objects.requireNonNull(ConfigBeanRegistryProvider.getInstance());
            ((InternalConfigBeanRegistry) cbr).initialize(picoServices);

            // pre-initialize ourselves...
            if (isRootProvider()) {
                // pre-activate our managed services ...
                configBeanMap.forEach(this::preActivateManagedService);
            }
        } else if (event == Event.FINAL_RESOLVE) {
            // post-initialize ourselves...
            if (isRootProvider()) {
                if (isAutoActivationEnabled()) {
                    maybeActivate(null, false);
                }
            }

            assertInitialized(true);
            resolveConfigDrivenServices();
        } else if (event == Event.SERVICES_READY) {
            assertIsInitialized();
            activateConfigDrivenServices();
        }
    }

    @Override
    public Class<?> getConfigBeanType() {
        Class<?> serviceType = getServiceType();
        ConfiguredBy configuredBy =
                Objects.requireNonNull(serviceType.getAnnotation(ConfiguredBy.class), String.valueOf(serviceType));
        return Objects.requireNonNull(configuredBy, String.valueOf(serviceType)).value();
    }

    @Override
    public MetaConfigBeanInfo<?> getConfigBeanInfo() {
        Class<?> configBeanType = getConfigBeanType();
        ConfigBean configBean =
                Objects.requireNonNull(configBeanType.getAnnotation(ConfigBean.class), String.valueOf(getServiceType()));
        return Objects.requireNonNull(ConfigBeanInfo.toMetaConfigBeanInfo(configBean, configBeanType));
    }

    @Override
    public Map<String, Map<String, Object>> getConfigBeanAttributes() {
        return Collections.emptyMap();
    }

    /**
     * @return The backing config of this configured service instance.
     */
    public abstract Optional<Config> getRawConfig();

    @Override
    public CB toConfigBean(Config cfg) {
        return toConfigBean(cfg, ConfigResolverProvider.getInstance());
    }

    @Override
    public abstract CB toConfigBean(io.helidon.config.Config cfg, ConfigResolver resolver);

    /**
     * Resolves this configured service's configuration bean from the provided config & resolver.
     * Typically, for internal use only.
     *
     * @param config    the config
     * @param resolver  the resolver
     */
    public abstract void resolveFrom(io.helidon.config.Config config, ConfigResolver resolver);

    @Override
    public abstract String getConfigBeanInstanceId(CB configBean);

    /**
     * Brokers the set of the instance id for the given config bean.
     *
     * @param configBean the config bean to set
     * @param val the instance id to associate it with
     */
    public abstract void setConfigBeanInstanceId(CB configBean, String val);

    /**
     * Creates a new instance of this type of configured service provider, along with the configuration bean
     * associated with the service.
     *
     * @param configBean the config bean
     * @return the created instance injected with the provided config bean
     */
    protected abstract AbstractConfiguredServiceProvider<T, CB> createInstance(Object configBean);

    /**
     * After the gathering dependency phase, we will short circuit directly to the finish line.
     */
    @Override
    protected void doConstructing(ActivationResult activationResult, ActivationPhase phase) {
        if (isRootProvider()) {
            boolean shouldBeActive = (isAutoActivationEnabled() && !managedConfiguredServicesMap.isEmpty());
            ActivationPhase setPhase = (shouldBeActive) ? ActivationPhase.ACTIVE : ActivationPhase.PENDING;
            onFinished(activationResult, setPhase);
            return;
        }

        super.doConstructing(activationResult, phase);
    }

    // note that all responsibilities to resolve is delegated to the root provider
    @Override
    public Object resolve(InjectionPointInfo ipInfo,
                          PicoServices picoServices,
                          ServiceProvider<?> serviceProvider,
                          boolean resolveIps) {
        if (resolveIps) {
            assert (isRootProvider());
            // too early to resolve...
            return NOT_RESOLVABLE;
        }

        ServiceInfo dep = ipInfo.dependencyToServiceInfo();
        if (!dep.matchesContract(getConfigBeanType())) {
            return NOT_RESOLVABLE;
        }

        // if we are here then we are asking for a config bean for ourselves, or a slave/managed instance...

        if (!dep.qualifiers().isEmpty()) {
            throw new InjectionException("cannot use qualifiers while injecting config beans for self", null, this);
        }

        if (isRootProvider()) {
            return Objects.requireNonNull(getConfigBeanType());
        }

        return Objects.requireNonNull(getConfigBean());
    }

    /**
     * Here we are only looking for service providers, not service instances. What we need to do here is to determine
     * whether to (a) include root providers, (b) include slave providers, or (c) include both.
     *
     * The result depends on the type of this provider instance.
     *
     * Here is the heuristic:
     * <li> if this is a slave then simply use the standard matching behavior.
     *
     * If, however, we are the root provider then the additional heuristic is applied:
     * <li> if the request mentions the {@link io.helidon.pico.config.api.ConfiguredBy} qualifier w/ no value specified
     * then the caller is only interested in the root provider.
     * <li> if the request mentions the {@link io.helidon.pico.config.api.ConfiguredBy} qualifier w/ a value specified
     * then the caller is only interested in the slave providers.
     * <li> if the request is completely empty then they are interested in everything - the root
     * provider as well as the slave providers.
     * <li> if there is no slaves under management then they must be interested in the root provider.
     * <li> the fallback is to use standard matching using the criteria provided and only include the slaves.
     *
     * @param criteria              the injection point criteria that must match
     * @param wantThis              if this instance matches criteria, do we want to return this instance as part of the result
     * @param thisAlreadyMatches    an optimization that signals to the implementation that this instance has already
     *                              matched using the standard service info matching checks
     * @return the set of matching service providers based upon the context and criteria provided
     */
    @Override
    public List<ServiceProvider<?>> serviceProviders(ServiceInfo criteria, boolean wantThis, boolean thisAlreadyMatches) {
        if (isRootProvider()) {
            Set<QualifierAndValue> qualifiers = (Objects.isNull(criteria))
                                                         ? Collections.emptySet() : criteria.qualifiers();
            Optional<? extends AnnotationAndValue> configuredByQualifier = DefaultQualifierAndValue
                    .findFirst(EMPTY_CONFIGURED_BY.typeName(), qualifiers);
            boolean hasValue = configuredByQualifier.isPresent()
                    && AnnotationAndValue.hasNonBlankValue(configuredByQualifier.get().value());
            boolean blankCriteria = qualifiers.isEmpty() && ServiceInfoBasics.isBlank(criteria);
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
                    List<ServiceProvider<?>> result = new LinkedList<>();
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
                    return Collections.singletonList(new UnconfiguredServiceProvider<>(this));
                }
                return Collections.singletonList(this);
            }
        } else {    // this is a slave instance ...
            if (thisAlreadyMatches || serviceInfo().matches(criteria)) {
                return Collections.singletonList(this);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Map<String, AbstractConfiguredServiceProvider<?, CB>> managedServiceProviders(ServiceInfo criteria) {
        if (!isRootProvider()) {
            assert (managedConfiguredServicesMap.isEmpty());
            return Collections.emptyMap();
        }

        Map<String, AbstractConfiguredServiceProvider<?, CB>> map = managedConfiguredServicesMap.entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .filter(e -> e.getValue().get().serviceInfo().matches(criteria))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        if (map.size() <= 1) {
            return map;
        }

        Map<String, AbstractConfiguredServiceProvider<?, CB>> result = new TreeMap<>(getConfigBeanComparator());
        result.putAll(map);
        return result;
    }

    @Override
    public T get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        if (!isRootProvider()) {
            T serviceOrProvider = maybeActivate(ipInfoCtx, true);
            return Objects.requireNonNull(serviceOrProvider);
        }

        // we are root provider
        if (ActivationPhase.ACTIVE != currentActivationPhase()) {
            setPhase(null, ActivationPhase.ACTIVE);
        }
        List<ServiceProvider<?>> qualifiedProviders = serviceProviders(criteria, false, true);
        for (ServiceProvider<?> qualifiedProvider : qualifiedProviders) {
            assert (this != qualifiedProvider);
            Object serviceOrProvider = qualifiedProvider.get(ipInfoCtx, criteria, false);
            if (Objects.nonNull(serviceOrProvider)) {
                return (T) serviceOrProvider;
            }
        }

        if (expected) {
            throw expectedQualifiedServiceError(ipInfoCtx, criteria);
        }

        return null;
    }

    @Override
    public List<T> getList(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        if (!isRootProvider()) {
            T serviceOrProvider = maybeActivate(ipInfoCtx, expected);
            return (expected)
                    ? Collections.singletonList(Objects.requireNonNull(serviceOrProvider)) : Collections.emptyList();
        }

        // we are root
        Map<String, AbstractConfiguredServiceProvider<?, CB>> matching = managedServiceProviders(criteria);
        if (!matching.isEmpty()) {
            List<?> result = matching.values().stream()
                    .map(it -> it.get(ipInfoCtx, criteria, false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!result.isEmpty()) {
                return (List<T>) result;
            }
        }

        if (!expected) {
            return Collections.emptyList();
        }

        throw expectedQualifiedServiceError(ipInfoCtx, criteria);
    }

    protected void resolveConfigDrivenServices() {
        assertIsInitialized();
        assert (isRootProvider());
        assert (managedConfiguredServicesMap.size() == configBeanMap.size()) : description();

        if (managedConfiguredServicesMap.isEmpty()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "no configured services for: " + description());
            }
            return;
        }

        // resolve config ...
        final ConfigResolver resolver = Objects.requireNonNull(ConfigResolverProvider.getInstance());
        managedConfiguredServicesMap.values().forEach(opt -> {
            assert (opt.isPresent());

            AbstractConfiguredServiceProvider<?, CB> csp = opt.get();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Resolving config for "
                        + ServiceProvider.toDescription(csp.get()) + "...");
            }

            try {
                csp.setPhase(null, ActivationPhase.PENDING);
                csp.resolveFrom(null, resolver);
            } catch (Throwable t) {
                csp.failedFinish(null, t, true);
            }
        });
    }

    protected void activateConfigDrivenServices() {
        assertIsInitialized();
        assert (isRootProvider());
        assert (managedConfiguredServicesMap.size() == configBeanMap.size()) : description();

        if (configBeanMap.isEmpty()) {
            return;
        }

        if (!isAutoActivationEnabled()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "drivesActivation disabled for: " + description());
            }
            return;
        }

        configBeanMap.forEach(this::activateManagedService);
    }

    protected AbstractConfiguredServiceProvider<T, CB> activateManagedService(String instanceId, Object configBean) {
        return managedConfiguredServicesMap.compute(instanceId, (id, existing) -> {
            if (Objects.isNull(existing) || existing.isEmpty()) {
                existing = innerPreActivateManagedService(instanceId, configBean);
            }

            AbstractConfiguredServiceProvider<T, CB> sp = existing.get();
            if (!sp.isActive()) {
                sp.innerActivate();
            }
            return existing;
        }).get();
    }

    private void innerActivate() {
        // this may go into a wait state if other threads are trying to also initialize at the same time -
        // expected behavior
        T service = maybeActivate(null, true);

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "finished activating: " + service);
        }
    }

    protected AbstractConfiguredServiceProvider<T, CB> preActivateManagedService(String instanceId, Object configBean) {
        return managedConfiguredServicesMap.compute(instanceId, (id, existing) -> {
            if (Objects.nonNull(existing) && existing.isPresent()) {
                return existing;
            }
            return innerPreActivateManagedService(instanceId, configBean);
        }).get();
    }

    private Optional<AbstractConfiguredServiceProvider<T, CB>> innerPreActivateManagedService(String instanceId, Object configBean) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "creating: " + className()
                    + " with config instance id: " + instanceId);
        }

        AbstractConfiguredServiceProvider<T, CB> instance = Objects.requireNonNull(createInstance(configBean));
        assert (instance != this);

        DefaultServiceInfo newServiceInfo = instance.serviceInfo().toBuilder()
                .named(Objects.requireNonNull(Objects.requireNonNull(instanceId)))
                .build();

        instance.overrideServiceInfo(newServiceInfo);
        instance.picoServices(Objects.requireNonNull(picoServices()));
        instance.rootProvider(this);

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "config instance successfully initialized: "
                    + id() + ":" + newServiceInfo.qualifiers());
        }

        return Optional.of(instance);
    }

    /**
     * @return true if this service is driven to activation during startup.
     */
    public boolean isAutoActivationEnabled() {
        return getConfigBeanInfo().drivesActivation();
    }

    /**
     * Configurable services by their very nature are not compile-time bindable during application creation.
     *
     * @return {@link #NOT_BINDABLE}
     */
    @Override
    public ServiceProviderBindable serviceProviderBindable() {
        return NOT_BINDABLE;
    }

    /**
     * @return the special comparator for ordering config bean instance ids.
     */
    public static Comparator<String> getConfigBeanComparator() {
        return BEAN_INSTANCE_ID_COMPARATOR;
    }

//    protected Optional<AbstractConfiguredServiceProvider<T, CB>> selectDefaultProvider(
//            Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> map,
//            boolean isMapSorted) {
//        if (map.isEmpty()) {
//            return Optional.empty();
//        }
//
//        Optional<AbstractConfiguredServiceProvider<T, CB>> match = map.get(DefaultConfigBeanRegistry.DEFAULT_INSTANCE_ID);
//        if (Objects.isNull(match)) {
//            if (!isMapSorted) {
//                Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> sortedMap
//                        = new TreeMap<>(BEAN_INSTANCE_ID_COMPARATOR);
//                sortedMap.putAll(map);
//                map = sortedMap;
//            }
//            match = map.entrySet().iterator().next().getValue();
//        }
//
//        return match;
//    }
//
//    protected Optional<AbstractConfiguredServiceProvider<T, CB>> selectNamedProvider(
//            String name,
//            Map<String, Optional<AbstractConfiguredServiceProvider<T, CB>>> map) {
//        if (map.isEmpty()) {
//            return Optional.empty();
//        }
//
//        Optional<AbstractConfiguredServiceProvider<T, CB>> match = map.get(Objects.requireNonNull(name));
//        return Objects.isNull(match) ? Optional.empty() : match;
//    }

    /**
     * See {@link #getConfigBeanComparator()} ()}
     */
    static class CBInstanceComparator implements Comparator<String> {
        @Override
        public int compare(String str1, String str2) {
            if (DefaultConfigBeanRegistry.DEFAULT_INSTANCE_ID.equals(str1)) {
                return -1 * Integer.MAX_VALUE;
            } else if (DefaultConfigBeanRegistry.DEFAULT_INSTANCE_ID.equals(str2)) {
                return Integer.MAX_VALUE;
            }
            return str1.compareTo(str2);
        }
    }

}
