/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Abstract common implementation of {@link Config} extended by appropriate Config node types:
 * {@link ConfigListImpl}, {@link ConfigMissingImpl}, {@link ConfigObjectImpl}, {@link ConfigLeafImpl}.
 */
abstract class AbstractConfigImpl implements Config, org.eclipse.microprofile.config.Config {

    public static final Logger LOGGER = Logger.getLogger(AbstractConfigImpl.class.getName());

    private final ConfigKeyImpl prefix;
    private final ConfigKeyImpl key;
    private final ConfigKeyImpl realKey;
    private final ConfigFactory factory;
    private final Type type;
    private final Context context;
    private final ConfigMapperManager mapperManager;
    private final boolean useSystemProperties;
    private final boolean useEnvironmentVariables;

    /**
     * Initializes Config implementation.
     *  @param type    a type of config node.
     * @param prefix  prefix key for the new config node.
     * @param key     a key to this config.
     * @param factory a config factory.
     * @param mapperManager mapper manager
     */
    AbstractConfigImpl(Type type,
                       ConfigKeyImpl prefix,
                       ConfigKeyImpl key,
                       ConfigFactory factory,
                       ConfigMapperManager mapperManager) {
        this.mapperManager = mapperManager;
        Objects.requireNonNull(prefix, "prefix argument is null.");
        Objects.requireNonNull(key, "key argument is null.");
        Objects.requireNonNull(factory, "factory argument is null.");

        this.prefix = prefix;
        this.key = key;
        this.realKey = prefix.child(key);
        this.factory = factory;
        this.type = type;

        context = new NodeContextImpl();

        boolean sysProps = false;
        boolean envVars = false;
        int index = 0;
        for (ConfigSourceRuntimeBase configSource : factory.configSources()) {
            if (index == 0 && configSource.isSystemProperties()) {
                sysProps = true;
            }
            if (configSource.isEnvironmentVariables()) {
                envVars = true;
            }

            if (sysProps && envVars) {
                break;
            }
            index++;
        }

        this.useEnvironmentVariables = envVars;
        this.useSystemProperties = sysProps;
    }

    /**
     * Returns a {@code String} value as {@link Optional} of configuration node if the node a leaf or "hybrid" node.
     * Returns a {@link Optional#empty() empty} if the node is {@link Type#MISSING} type or if the node does not contain a direct
     * value.
     * This is "raw" accessor method for String value of this config node. To have nicer variety of value accessors,
     * see {@link #asString()} and in general {@link #as(Class)}.
     *
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} in case the node does not have a value
     *
     * use {@link #asString()} instead
     */
    Optional<String> value() {
        return Optional.empty();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public final Instant timestamp() {
        return factory.timestamp();
    }

    @Override
    public final ConfigKeyImpl key() {
        return key;
    }

    protected final ConfigKeyImpl realKey() {
        return realKey;
    }

    @Override
    public final Type type() {
        return type;
    }

    @Override
    public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
        return mapperManager.map(value, type, key().toString());
    }

    @Override
    public final Config get(Config.Key subKey) {
        Objects.requireNonNull(subKey, "Key argument is null.");

        if (subKey.isRoot()) {
            return this;
        } else {
            return factory.config(prefix, this.key.child(subKey));
        }
    }

    @Override
    public final Config detach() {
        if (key.isRoot()) {
            return this;
        } else {
            return factory.config(realKey(), ConfigKeyImpl.of());
        }
    }

    @Override
    public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
        return asList(Config.class);
    }

    /*
     * MicroProfile Config methods
     */
    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        Config config = factory.context().last();
        try {
            return mpFindValue(config, propertyName, propertyType);
        } catch (MissingValueException e) {
            throw new NoSuchElementException(e.getMessage());
        } catch (ConfigMappingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        try {
            return Optional.of(getValue(propertyName, propertyType));
        } catch (NoSuchElementException e) {
            return Optional.empty();
        } catch (ConfigMappingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Iterable<String> getPropertyNames() {
        Set<String> keys = new HashSet<>(factory.context().last()
                                                 .asMap()
                                                 .orElseGet(Collections::emptyMap)
                                                 .keySet());

        if (useSystemProperties) {
            keys.addAll(System.getProperties().stringPropertyNames());
        }

        return keys;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        Config config = factory.context().last();
        if (null == config) {
            // maybe we are in progress of initializing this config (e.g. filter processing)
            config = this;
        }

        if (config instanceof AbstractConfigImpl) {
            return ((AbstractConfigImpl) config).mpConfigSources();
        }
        return Collections.emptyList();
    }

    private Iterable<ConfigSource> mpConfigSources() {
        return new LinkedList<>(factory.mpConfigSources());
    }

    private <T> T mpFindValue(Config config, String propertyName, Class<T> propertyType) {
        // this is a workaround TCK tests that expect system properties to be mutable
        //  Helidon config does the same, yet with a slight delay (polling reasons)
        //  we need to check if system properties are enabled and first - if so, do this

        String property = null;
        if (useSystemProperties) {
            property = System.getProperty(propertyName);
        }

        if (null == property) {
            ConfigValue<T> value = config
                    .get(propertyName)
                    .as(propertyType);

            if (value.isPresent()) {
                return value.get();
            }

            // try to find in env vars
            if (useEnvironmentVariables) {
                T envVar = mpFindEnvVar(config, propertyName, propertyType);
                if (null != envVar) {
                    return envVar;
                }
            }

            return value.get();
        } else {
            return config.get(propertyName).convert(propertyType, property);
        }
    }

    private <T> T mpFindEnvVar(Config config, String propertyName, Class<T> propertyType) {
        String result = System.getenv(propertyName);

        // now let's resolve all variants required by the specification
        if (null == result) {
            for (String alias : EnvironmentVariableAliases.aliasesOf(propertyName)) {
                result = System.getenv(alias);
                if (null != result) {
                    break;
                }
            }
        }

        if (null != result) {
            return config.convert(propertyType, result);
        }

        return null;
    }

    private Config contextConfig(Config rootConfig) {
        return rootConfig
                .get(AbstractConfigImpl.this.prefix)
                .detach()
                .get(AbstractConfigImpl.this.key);
    }

    @Override
    public void onChange(Consumer<Config> onChangeConsumer) {
        factory.provider()
                .onChange(event -> {
                    // check if change contains this node
                    if (event.changedKeys().contains(realKey)) {
                        onChangeConsumer.accept(contextConfig(event.config()));
                    }
                });
    }

    /**
     * Implementation of node specific context.
     */
    private class NodeContextImpl implements Context {

        @Override
        public Instant timestamp() {
            return AbstractConfigImpl.this.factory.context().timestamp();
        }

        @Override
        public Config last() {
            return AbstractConfigImpl.this.contextConfig(AbstractConfigImpl.this.factory.context().last());
        }

        @Override
        public Config reload() {
            return AbstractConfigImpl.this.contextConfig(AbstractConfigImpl.this.factory.context().reload());
        }

    }

}
