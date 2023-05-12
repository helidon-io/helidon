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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.GeneratedConfigBean;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServiceProviderException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.runtime.ServiceProviderComparator;

import static io.helidon.pico.configdriven.runtime.ConfigDrivenUtils.hasValue;
import static io.helidon.pico.configdriven.runtime.ConfigDrivenUtils.safeDowncastOf;
import static io.helidon.pico.configdriven.runtime.ConfigDrivenUtils.validatedConfigKey;

/**
 * The default implementation for {@link ConfigBeanRegistry}.
 */
@SuppressWarnings("unchecked")
class DefaultPicoConfigBeanRegistry implements BindableConfigBeanRegistry {
    /**
     * The default config bean instance id.
     */
    static final String DEFAULT_INSTANCE_ID = "@default";

    private static final System.Logger LOGGER = System.getLogger(DefaultPicoConfigBeanRegistry.class.getName());

    private static final boolean FORCE_VALIDATE_USING_BEAN_ATTRIBUTES = false;
    private static final boolean FORCE_VALIDATE_USING_CONFIG_ATTRIBUTES = true;

    private final AtomicBoolean initializing = new AtomicBoolean();
    private final Map<ConfiguredServiceProvider<?, GeneratedConfigBean>, ConfigBeanInfo>
            configuredServiceProviderMetaConfigBeanMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfiguredServiceProvider<?, GeneratedConfigBean>>>
            configuredServiceProvidersByConfigKey = new ConcurrentHashMap<>();
    private CountDownLatch initialized = new CountDownLatch(1);

    DefaultPicoConfigBeanRegistry() {
    }

    static boolean validateUsingConfigAttributes(String instanceId,
                                                 String attrName,
                                                 String attrConfigKey,
                                                 Config config,
                                                 Supplier<Object> beanBasedValueSupplier,
                                                 Set<String> problems) {
        if (config == null) {
            if (!DEFAULT_INSTANCE_ID.equals(instanceId)) {
                problems.add("Unable to obtain backing config for service provider for " + attrConfigKey);
            }

            return false;
        } else {
            Config attrConfig = config.get(attrConfigKey);
            if (attrConfig.exists()) {
                return true;
            }

            // if we have a default value from our bean, then that is the fallback verification
            Object val = beanBasedValueSupplier.get();
            if (val == null) {
                problems.add("'" + attrConfigKey + "' is a required configuration for attribute '" + attrName + "'");
                return true;
            }

            // full through to bean validation next, just for any added checks we might do there
            return false;
        }
    }

    static void validateUsingBeanAttributes(Supplier<Object> valueSupplier,
                                            String attrName,
                                            Set<String> problems) {
        Object val = valueSupplier.get();
        if (val == null) {
            problems.add("'" + attrName + "' is a required attribute and cannot be null");
        } else {
            if (!(val instanceof String)) {
                val = val.toString();
            }
            if (!hasValue((String) val)) {
                problems.add("'" + attrName + "' is a required attribute and cannot be blank");
            }
        }
    }

    @Override
    public boolean reset(boolean deep) {
        System.Logger.Level level = (isInitialized() && PicoServices.isDebugEnabled())
        ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
        LOGGER.log(level, "Resetting");
        configuredServiceProviderMetaConfigBeanMap.clear();
        configuredServiceProvidersByConfigKey.clear();
        initializing.set(false);
        initialized = new CountDownLatch(1);
        return true;
    }

    @Override
    public void bind(ConfiguredServiceProvider<?, ?> configuredServiceProvider,
                     QualifierAndValue configuredByQualifier,
                     MetaConfigBeanInfo metaConfigBeanInfo) {
        Objects.requireNonNull(configuredServiceProvider);
        Objects.requireNonNull(configuredByQualifier);
        Objects.requireNonNull(metaConfigBeanInfo);

        if (initializing.get()) {
            throw new ConfigException("Unable to bind config post " + PicoServicesConfig.NAME + " initialization: "
                                              + configuredServiceProvider.description());
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Binding " + configuredServiceProvider.serviceType()
                    + " with " + configuredByQualifier.value());
        }

        Object prev = configuredServiceProviderMetaConfigBeanMap
                .put((ConfiguredServiceProvider<?, GeneratedConfigBean>) configuredServiceProvider, metaConfigBeanInfo);
        assert (prev == null) : "duplicate service provider initialization occurred";

        String configKey = validatedConfigKey(metaConfigBeanInfo);
        Class<?> cspType = Objects.requireNonNull(configuredServiceProvider.serviceType());
        configuredServiceProvidersByConfigKey.compute(configKey, (k, cspList) -> {
            if (cspList == null) {
                cspList = new ArrayList<>();
            }
            Optional<ConfiguredServiceProvider<?, GeneratedConfigBean>> prevCsp = cspList.stream()
                    .filter(it -> cspType.equals(it.configBeanType()))
                    .findAny();
            assert (prevCsp.isEmpty()) : "duplicate service provider initialization occurred";

            boolean added = cspList.add((ConfiguredServiceProvider<?, GeneratedConfigBean>) configuredServiceProvider);
            assert (added);

            return cspList;
        });
    }

    @Override
    public void initialize(PicoServices ignoredPicoServices) {
        try {
            if (initializing.getAndSet(true)) {
                // all threads should wait for the leader (and the config bean registry) to have been fully initialized
                initialized.await();
                return;
            }

            Config config = PicoServices.realizedGlobalBootStrap().config().orElse(null);
            if (config == null) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Unable to initialize - there is no config to read - be sure to initialize "
                                   + Bootstrap.class.getName() + " config prior to service activation.");
                reset(true);
                return;
            }

            LOGGER.log(System.Logger.Level.DEBUG, "Initializing");
            initialize(config);
            // we are now ready and initialized
            initialized.countDown();
        } catch (Throwable t) {
            PicoException e = new PicoServiceProviderException("Error while initializing config bean registry", t);
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage(), e);
            reset(true);
            throw e;
        }
    }

    @Override
    public boolean ready() {
        return isInitialized();
    }

    @Override
    public List<ConfiguredServiceProvider<?, ?>> configuredServiceProvidersConfiguredBy(String key) {
        List<ConfiguredServiceProvider<?, ?>> result = new ArrayList<>();

        configuredServiceProvidersByConfigKey.forEach((k, csps) -> {
            if (k.equals(key)) {
                result.addAll(csps);
            }
        });

        if (result.size() > 1) {
            result.sort(ServiceProviderComparator.create());
        }

        return result;
    }

    @Override
    public Map<ConfiguredServiceProvider<?, ?>, ConfigBeanInfo> configurableServiceProviders() {
        return Map.copyOf(configuredServiceProviderMetaConfigBeanMap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ConfiguredServiceProvider<?, ?>> configuredServiceProviders() {
        List<ConfiguredServiceProvider<?, ?>> result = new ArrayList<>();

        configuredServiceProvidersByConfigKey.values()
                .forEach(cspList ->
                                 cspList.stream()
                                         .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                                         .map(AbstractConfiguredServiceProvider.class::cast)
                                         .forEach(rootCsp -> {
                                             rootCsp.assertIsRootProvider(true, false);
                                             Map<String, Optional<AbstractConfiguredServiceProvider<?, ?>>> cfgBeanMap =
                                                     rootCsp.configuredServicesMap();
                                             cfgBeanMap.values().forEach(slaveCsp -> slaveCsp.ifPresent(result::add));
                                         }));

        if (result.size() > 1) {
            result.sort(ServiceProviderComparator.create());
        }

        return result;
    }

    @Override
    public Set<?> configBeansByConfigKey(String key) {
        return configBeansByConfigKey(key, Optional.empty());
    }

    @Override
    public Set<?> configBeansByConfigKey(String key,
                                         String fullConfigKey) {
        return configBeansByConfigKey(key, Optional.of(fullConfigKey));
    }

    @Override
    public Map<String, ?> configBeanMapByConfigKey(String key,
                                                   String fullConfigKey) {
        List<ConfiguredServiceProvider<?, GeneratedConfigBean>> cspsUsingSameKey =
                configuredServiceProvidersByConfigKey.get(Objects.requireNonNull(key));
        if (cspsUsingSameKey == null) {
            return Map.of();
        }

        Map<String, Object> result = new TreeMap<>(AbstractConfiguredServiceProvider.configBeanComparator());
        cspsUsingSameKey.stream()
                .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                .map(AbstractConfiguredServiceProvider.class::cast)
                .forEach(csp -> {
                    Map<String, ?> configBeans = csp.configBeanMap();
                    configBeans.forEach((k, v) -> {
                        if (fullConfigKey.isEmpty() || fullConfigKey.equals(k)) {
                            Object prev = result.put(k, v);
                            if (prev != null && prev != v) {
                                throw new IllegalStateException("Two entries with the same key detected: " + prev + " and " + v);
                            }
                        }
                    });
                });
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <GCB extends GeneratedConfigBean> Map<String, Collection<GCB>> allConfigBeans() {
        Map<String, Collection<GCB>> result = new TreeMap<>(AbstractConfiguredServiceProvider.configBeanComparator());

        configuredServiceProvidersByConfigKey.forEach((key, value) -> value.stream()
                .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                .map(AbstractConfiguredServiceProvider.class::cast)
                .forEach(csp -> {
                    Map<String, ?> configBeans = csp.configBeanMap();
                    configBeans.forEach((key1, value1) -> {
                        result.compute(key1, (k, v) -> {
                            if (v == null) {
                                v = new ArrayList<>();
                            }
                            v.add((GCB) value1);
                            return v;
                        });
                    });
                }));

        return result;
    }

    protected boolean isInitialized() {
        return (0 == initialized.getCount());
    }

    <T, GCB extends GeneratedConfigBean> void loadConfigBeans(io.helidon.config.Config config,
                                                              ConfiguredServiceProvider<T, GCB> configuredServiceProvider,
                                                              ConfigBeanInfo metaConfigBeanInfo,
                                                              Map<String, Map<String, Object>> metaAttributes) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Loading config bean(s) for "
                    + configuredServiceProvider.serviceType() + " with config: "
                    + config.key().toString());
        }

        if (metaConfigBeanInfo.repeatable()) {
            // this config bean may be more than once - if list, use children, otherwise use child nodes
            registerChildConfigBeans(config, configuredServiceProvider, metaAttributes);
        } else {
            // not a repeatable one, use this node directly, if list, this is a problem
            if (config.isList()) {
                throw new ConfigException("Expected to only have a single base, non-repeatable configuration for "
                                                  + configuredServiceProvider.serviceType() + " with config: "
                                                  + config.key().toString()
                                                  + ", you can set repeatable=\"true\" when expecting a list.");
            }
            GCB baseConfigBean = toConfigBean(config, configuredServiceProvider);
            registerConfigBean(baseConfigBean, null, config, configuredServiceProvider, metaAttributes);
        }
    }

    <T, CB extends GeneratedConfigBean> void registerChildConfigBeans(io.helidon.config.Config config,
                                          ConfiguredServiceProvider<T, CB> configuredServiceProvider,
                                          Map<String, Map<String, Object>> metaAttributes) {
        config.asNodeList()
                .ifPresent(list -> {
                    list.forEach(beanCfg -> {
                        CB configBean = toConfigBean(beanCfg, configuredServiceProvider);
                        String instanceId = toInstanceId(beanCfg);
                        registerConfigBean(configBean, instanceId, beanCfg, configuredServiceProvider, metaAttributes);
                    });
                });
    }

    static <T, GCB extends GeneratedConfigBean> GCB toConfigBean(io.helidon.config.Config config,
                                                                 ConfiguredServiceProvider<T, ?> configuredServiceProvider) {
        GCB configBean = (GCB) Objects.requireNonNull(configuredServiceProvider.toConfigBean(config),
                                               "Unable to create default config bean for " + configuredServiceProvider);
        if (configuredServiceProvider instanceof AbstractConfiguredServiceProvider) {
            setConfigBeanInstanceId(config, configBean, (AbstractConfiguredServiceProvider<T, GCB>) configuredServiceProvider);
        }

        return configBean;
    }

    @SuppressWarnings("rawtypes")
    static void setConfigBeanInstanceId(io.helidon.config.Config config,
                                        GeneratedConfigBean configBean,
                                        AbstractConfiguredServiceProvider csp) {
        String instanceId = toInstanceId(config);
        csp.configBeanInstanceId(configBean, instanceId);
    }

    /**
     * Validates the config bean against the declared policy, coming by way of annotations on the
     * {@code ConfiguredOption}'s.
     *
     * @param csp            the configured service provider
     * @param key            the config key being validated (aka instance id)
     * @param configBean     the config bean itself
     * @param metaAttributes the meta-attributes that captures the policy in a map like structure by attribute name
     * @throws PicoServiceProviderException if the provided config bean is not validated according to policy
     */
    <GCB extends GeneratedConfigBean> void validate(GCB configBean,
                                                    String key,
                                                    Config config,
                                                    AbstractConfiguredServiceProvider<Object, GCB> csp,
                                                    Map<String, Map<String, Object>> metaAttributes) {
        Set<String> problems = new LinkedHashSet<>();
        String instanceId = csp.toConfigBeanInstanceId(configBean);
        assert (hasValue(key));

        AttributeVisitor<Object> visitor = new AttributeVisitor<>() {
            @Override
            public void visit(String attrName,
                              Supplier<Object> valueSupplier,
                              Map<String, Object> meta,
                              Object userDefinedCtx,
                              Class<?> type,
                              Class<?>... typeArgument) {
                Map<String, Object> metaAttrPolicy = metaAttributes.get(attrName);
                if (metaAttrPolicy == null) {
                    problems.add("Unable to query policy for config key '" + key + "'");
                    return;
                }

                Object required = metaAttrPolicy.get("required");
                String attrConfigKey = (String) Objects.requireNonNull(metaAttrPolicy.get("key"));

                if (required == null) {
                    required = ConfiguredOption.DEFAULT_REQUIRED;
                } else if (!(required instanceof Boolean)) {
                    required = Boolean.parseBoolean((String) required);
                }

                if ((boolean) required) {
                    boolean validated = false;

                    if (FORCE_VALIDATE_USING_CONFIG_ATTRIBUTES) {
                        validated = validateUsingConfigAttributes(
                                instanceId, attrName, attrConfigKey, config, valueSupplier, problems);
                    }

                    if (!validated || FORCE_VALIDATE_USING_BEAN_ATTRIBUTES) {
                        validateUsingBeanAttributes(valueSupplier, attrName, problems);
                    }
                }

                // https://github.com/helidon-io/helidon/issues/6403 : "allowed values" check needed here also!
            }
        };

        csp.visitAttributes(configBean, visitor, configBean);

        if (!problems.isEmpty()) {
            throw new PicoServiceProviderException("Validation rules violated for "
                                                           + csp.configBeanType()
                                                           + " with config key '" + key
                                                           + "':\n"
                                                           + String.join(", ", problems).trim(), null, csp);
        }
    }

    @SuppressWarnings("unchecked")
    <GCB extends GeneratedConfigBean> void registerConfigBean(GCB configBean,
                                                              String instanceId,
                                                              Config config,
                                                              ConfiguredServiceProvider<?, GCB> configuredServiceProvider,
                                                              Map<String, Map<String, Object>> metaAttributes) {
        assert (configuredServiceProvider instanceof AbstractConfiguredServiceProvider);
        AbstractConfiguredServiceProvider<Object, GCB> csp =
                (AbstractConfiguredServiceProvider<Object, GCB>) configuredServiceProvider;

        if (instanceId != null) {
            csp.configBeanInstanceId(configBean, instanceId);
        } else {
            instanceId = configuredServiceProvider.toConfigBeanInstanceId(configBean);
        }

        if (DEFAULT_INSTANCE_ID.equals(instanceId)) {
            // default config beans should not be validated against any config, even if we have it available
            config = null;
        } else {
            Optional<Config> beanConfig = csp.rawConfig();
            if (beanConfig.isPresent()) {
                // prefer to use the bean's config over ours if it has it
                config = beanConfig.get();
            }
        }

        // will throw if not valid
        validate(configBean, instanceId, config, csp, metaAttributes);

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                       "Registering config bean '" + instanceId + "' with " + configuredServiceProvider.serviceType());
        }

        csp.registerConfigBean(instanceId, configBean);
    }

    private Set<?> configBeansByConfigKey(String key,
                                          Optional<String> optFullConfigKey) {
        List<ConfiguredServiceProvider<?, GeneratedConfigBean>> cspsUsingSameKey =
                configuredServiceProvidersByConfigKey.get(Objects.requireNonNull(key));
        if (cspsUsingSameKey == null) {
            return Set.of();
        }

        Set<Object> result = new LinkedHashSet<>();
        cspsUsingSameKey.stream()
                .filter(csp -> csp instanceof AbstractConfiguredServiceProvider)
                .map(AbstractConfiguredServiceProvider.class::cast)
                .forEach(csp -> {
                    Map<String, ?> configBeans = csp.configBeanMap();
                    if (optFullConfigKey.isEmpty()) {
                        result.addAll(configBeans.values());
                    } else {
                        configBeans.forEach((k, v) -> {
                            if (optFullConfigKey.get().equals(k)) {
                                result.add(v);
                            }
                        });
                    }
                });
        return result;
    }

    private void initialize(Config commonCfg) {
        if (configuredServiceProvidersByConfigKey.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No config driven services found");
            return;
        }

        io.helidon.config.Config cfg = safeDowncastOf(commonCfg);

        // first load all the root config beans... but defer resolve until later phase
        configuredServiceProviderMetaConfigBeanMap.forEach((configuredServiceProvider, cbi) -> {
            MetaConfigBeanInfo metaConfigBeanInfo = (MetaConfigBeanInfo) cbi;
            processRootLevelConfigBean(cfg, configuredServiceProvider, metaConfigBeanInfo);
        });

        if (!cfg.exists() || cfg.isLeaf()) {
            return;
        }

        // now find all the sub root level config beans also... still deferring resolution until a later phase
        visitAndInitialize(cfg.asNodeList().get(), 0);
        LOGGER.log(System.Logger.Level.DEBUG, "finishing walking config tree");
    }

    private void processRootLevelConfigBean(
            io.helidon.config.Config cfg,
            ConfiguredServiceProvider<?, GeneratedConfigBean> configuredServiceProvider,
            MetaConfigBeanInfo metaConfigBeanInfo) {
        if (metaConfigBeanInfo.levelType() != ConfigBean.LevelType.ROOT) {
            return;
        }

        String key = validatedConfigKey(metaConfigBeanInfo);
        io.helidon.config.Config config = cfg.get(key);
        Map<String, Map<String, Object>> metaAttributes = configuredServiceProvider.configBeanAttributes();
        if (config.exists()) {
            loadConfigBeans(config, configuredServiceProvider, metaConfigBeanInfo, metaAttributes);
        } else if (metaConfigBeanInfo.wantDefaultConfigBean()) {
            GeneratedConfigBean cfgBean = Objects.requireNonNull(configuredServiceProvider.toConfigBean(cfg),
                                                    "unable to create default config bean for " + configuredServiceProvider);
            registerConfigBean(cfgBean, DEFAULT_INSTANCE_ID, config, configuredServiceProvider, metaAttributes);
        }
    }

    private void processNestedLevelConfigBean(io.helidon.config.Config config,
                                              ConfiguredServiceProvider<?, GeneratedConfigBean> configuredServiceProvider,
                                              ConfigBeanInfo metaConfigBeanInfo) {
        if (metaConfigBeanInfo.levelType() != ConfigBean.LevelType.NESTED) {
            return;
        }

        Map<String, Map<String, Object>> metaAttributes = configuredServiceProvider.configBeanAttributes();
        loadConfigBeans(config, configuredServiceProvider, metaConfigBeanInfo, metaAttributes);
    }

    private void visitAndInitialize(List<io.helidon.config.Config> configs,
                                    int depth) {
        configs.forEach(config -> {
            // we start nested, since we've already processed the root level config beans previously
            if (depth > 0) {
                String key = config.name();
                List<ConfiguredServiceProvider<?, GeneratedConfigBean>> csps = configuredServiceProvidersByConfigKey.get(key);
                if (csps != null && !csps.isEmpty()) {
                    csps.forEach(configuredServiceProvider -> {
                        ConfigBeanInfo metaConfigBeanInfo =
                                Objects.requireNonNull(configuredServiceProviderMetaConfigBeanMap.get(configuredServiceProvider));
                        processNestedLevelConfigBean(config, configuredServiceProvider, metaConfigBeanInfo);
                    });
                }
            }

            if (!config.isLeaf()) {
                visitAndInitialize(config.asNodeList().get(), depth + 1);
            }
        });
    }

    static String toInstanceId(io.helidon.config.Config config) {
        // use explicit name, otherwise index or fq id of the config node (for lists, the name is 0 bases index)
        String id = config.get("name").asString().orElseGet(config::name);
        return hasValue(id) ? id : DEFAULT_INSTANCE_ID;
    }

}
