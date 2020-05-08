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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Abstract common implementation of {@link Config} extended by appropriate Config node types:
 * {@link ConfigListImpl}, {@link ConfigMissingImpl}, {@link ConfigObjectImpl}, {@link ConfigLeafImpl}.
 */
abstract class AbstractConfigImpl implements Config {

    public static final Logger LOGGER = Logger.getLogger(AbstractConfigImpl.class.getName());

    private final ConfigKeyImpl prefix;
    private final ConfigKeyImpl key;
    private final ConfigKeyImpl realKey;
    private final ConfigFactory factory;
    private final Type type;
    private final Context context;
    private final ConfigMapperManager mapperManager;

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
