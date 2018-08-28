/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.config.GenericConfigMapperUtils.PropertyWrapper;

/**
 * Implements generic mapping support based on public no-parameter constructor and public setters or public fields.
 *
 * @see ConfigMapperManager
 */
class GenericConfigMapper<T> implements ConfigMapper<T> {

    private final Class<T> type;
    private final MethodHandle constructorHandle;
    private final Collection<PropertyAccessor> propertyAccessors;

    GenericConfigMapper(Class<T> type, MethodHandle constructorHandle, ConfigMapperManager mapperManager) {
        this.type = type;
        this.constructorHandle = constructorHandle;

        propertyAccessors = GenericConfigMapperUtils.getBeanProperties(mapperManager, type);
    }

    @Override
    public T apply(Config config) throws ConfigMappingException, MissingValueException {
        try {
            T instance = type.cast(constructorHandle.invoke());

            for (PropertyAccessor propertyAccessor : propertyAccessors) {
                propertyAccessor.set(instance, config.get(propertyAccessor.getName()));
            }
            return instance;
        } catch (ConfigMappingException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new ConfigMappingException(
                    config.key(),
                    type,
                    "Generic java bean initialization has failed with an exception.",
                    ex);
        }
    }

    /**
     * Single JavaBean property accessor used to set new value.
     */
    static class PropertyAccessor<T> {
        private final String name;
        private final MethodHandle handle;
        private final boolean hasValueAnnotation;
        private final PropertyWrapper<T> propertyWrapper;

        PropertyAccessor(ConfigMapperManager mapperManager,
                         String name, Class<T> propertyType, Class<?> configAsType, boolean list, MethodHandle handle,
                         Config.Value value) {
            this.name = name;
            this.handle = handle;

            hasValueAnnotation = value != null;
            propertyWrapper = new PropertyWrapper<>(mapperManager,
                                                    name,
                                                    propertyType,
                                                    configAsType,
                                                    list,
                                                    GenericConfigMapperUtils.createDefaultSupplier(name, value));
        }

        public String getName() {
            return name;
        }

        void set(T instance, Config configNode) {
            propertyWrapper.get(configNode)
                    .ifPresent(value -> setImpl(instance, value));
        }

        private void setImpl(T instance, Object value) {
            try {
                handle.invoke(instance, value);
            } catch (ConfigException ex) {
                throw ex;
            } catch (Throwable throwable) {
                throw new ConfigException("Unable to set '" + name + "' property.", throwable);
            }
        }

        MethodHandle getHandle() {
            return handle;
        }

        boolean hasValueAnnotation() {
            return hasValueAnnotation;
        }

        void setValueAnnotation(Config.Value value) {
            propertyWrapper.setDefaultSupplier(GenericConfigMapperUtils.createDefaultSupplier(name, value));
        }
    }

    /**
     * This simplified implementation of Config VALUE node to be used to represent
     * {@link Value#withDefault()} value as a {@link Config} to be used by
     * {@link ConfigMapperManager#map(Class, Config) config mapper manager}.
     */
    static class SingleValueConfigImpl implements Config {

        private final ConfigMapperManager mapperManager;
        private final Key key;
        private final String value;
        private final Instant timestamp;

        SingleValueConfigImpl(ConfigMapperManager mapperManager, String key, String value) {
            this.mapperManager = mapperManager;
            this.key = Key.of(key);
            this.value = value;

            this.timestamp = Instant.now();
        }

        @Override
        public boolean hasValue() {
            return null != value;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public Optional<String> value() throws ConfigMappingException {
            return Optional.of(value);
        }

        @Override
        public Type type() {
            return Type.VALUE;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        @Override
        public Config get(Key key) {
            if (key.isRoot()) {
                return this;
            } else {
                return Config.empty().get(this.key).get(key);
            }
        }

        @Override
        public Config detach() {
            if (key.isRoot()) {
                return this;
            } else {
                return new SingleValueConfigImpl(mapperManager, "", value);
            }
        }

        @Override
        public Stream<Config> traverse(Predicate<Config> predicate) {
            return Stream.empty();
        }

        @Override
        public Optional<Map<String, String>> asOptionalMap() {
            Map map = mapperManager.map(Map.class, this);
            if (map instanceof ConfigMappers.StringMap) {
                return Optional.of((ConfigMappers.StringMap) map);
            }
            return Optional.of(new ConfigMappers.StringMap(map));
        }

        @Override
        public <T> Optional<T> asOptional(Class<? extends T> type) throws ConfigMappingException {
            return Optional.ofNullable(mapperManager.map(type, this));
        }

        @Override
        public Optional<List<Config>> nodeList() throws ConfigMappingException {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

        @Override
        public <T> Optional<List<T>> asOptionalList(Class<? extends T> type) throws ConfigMappingException {
            throw new ConfigMappingException(key(), "The Config node represents single value.");
        }

    }

}
