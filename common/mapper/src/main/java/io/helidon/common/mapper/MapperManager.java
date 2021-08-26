/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.common.mapper;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.GenericType;
import io.helidon.common.Prioritized;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.common.serviceloader.HelidonServiceLoader;

/**
 * Mapper manager of all configured mappers.
 * <p>
 * To map a source to target, you can use either of the {@code map} methods defined in this interface,
 * as they make sure that the mapping exists in either space.
 * <ul>
 * <li>If you call {@link #map(Object, Class, Class)} and no mapper is found for the class pair,
 * the implementation calls the {@link #map(Object, io.helidon.common.GenericType, io.helidon.common.GenericType)} with
 * {@link io.helidon.common.GenericType}s created for each parameters</li>
 * <li>If you call {@link #map(Object, io.helidon.common.GenericType, io.helidon.common.GenericType)} and no mapper is
 * found for the {@link io.helidon.common.GenericType} pair, an attempt is to locate a mapper for
 * the underlying class *IF* the generic type represents a simple class (e.g. not a generic type declaration)</li>
 * </ul>
 */
public interface MapperManager {
    /**
     * Shared instance of a mapper manager.
     * This instance is used by components that do not have access to a specific mapper manager.
     *
     * @return shared instance of mapper manager
     */
    static MapperManager shared() {
        return SharedManagerHandler.get();
    }

    /**
     * Configure a shared mapper manager.
     * This should be done just once at application startup, to prevent issues at runtime.
     *
     * @param manager manager to use
     */
    static void shared(MapperManager manager) {
        SharedManagerHandler.set(manager);
    }
    /**
     * Create a fluent API builder to create a customized mapper manager.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a mapper manager using only Java Service loader
     * loaded {@link io.helidon.common.mapper.spi.MapperProvider MapperProviders}.
     *
     * @return create a new mapper manager from service loader
     */
    static MapperManager create() {
        return MapperManager.builder().build();
    }

    /**
     * Create a fluent API builder to create a customized mapper manager based on the provided Helidon Service loader.
     *
     * @param serviceLoader fully configured service loader
     * @return a new builder
     */
    static Builder builder(HelidonServiceLoader<MapperProvider> serviceLoader) {
        return MapperManager.builder()
                .mapperProviders(serviceLoader);
    }

    /**
     * Create a mapper manager using only the provided Helidon Service loader.
     * loaded {@link io.helidon.common.mapper.spi.MapperProvider MapperProviders}.
     *
     * @param serviceLoader fully configured service loader
     * @return create a new mapper manager from service loader
     */
    static MapperManager create(HelidonServiceLoader<MapperProvider> serviceLoader) {
        return MapperManager.builder()
                .mapperProviders(serviceLoader)
                .build();
    }

    /**
     * Map from source to target.
     *
     * @param source     object to map
     * @param sourceType type of the source object (to locate the mapper)
     * @param targetType type of the target object (to locate the mapper)
     * @param <SOURCE>   type of the source
     * @param <TARGET>   type of the target
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    <SOURCE, TARGET> TARGET map(SOURCE source, GenericType<SOURCE> sourceType, GenericType<TARGET> targetType)
            throws MapperException;

    /**
     * Map from source to target.
     *
     * @param source     object to map
     * @param sourceType class of the source object (to locate the mapper)
     * @param targetType class of the target object (to locate the mapper)
     * @param <SOURCE>   type of the source
     * @param <TARGET>   type of the target
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    <SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType) throws MapperException;

    /**
     * Fluent API builder for {@link io.helidon.common.mapper.MapperManager}.
     */
    final class Builder implements io.helidon.common.Builder<MapperManager> {
        private HelidonServiceLoader.Builder<MapperProvider> providers = HelidonServiceLoader
                .builder(ServiceLoader.load(MapperProvider.class));
        private boolean useBuiltIn;
        private boolean builtInsAdded;

        private Builder() {
        }

        @Override
        public MapperManager build() {
            if (useBuiltIn && !builtInsAdded) {
                // very low priority for builtins, so any custom mapper has higher priority
                providers.addService(new BuiltInMappers(), Prioritized.DEFAULT_PRIORITY + 10000);
                builtInsAdded = true;
            }
            return new MapperManagerImpl(this);
        }

        /**
         * Add a new {@link io.helidon.common.mapper.spi.MapperProvider} to the list of providers loaded from
         * system service loader.
         * <p>
         * You may add multiple instances of the same implementation class.
         * <p>
         * If the same provider implementation would be loaded by Java Service loader, the service loader instance is ignored.
         * If you need to add a new implementation of the same type, please use the full features
         * of the {@link io.helidon.common.serviceloader.HelidonServiceLoader} and invoke
         * {@link MapperManager#create(io.helidon.common.serviceloader.HelidonServiceLoader)}.
         *
         * @param provider prioritized mapper provider to use
         * @return updated builder instance
         */
        public Builder addMapperProvider(MapperProvider provider) {
            this.providers.addService(provider);
            return this;
        }

        /**
         * Add a new {@link io.helidon.common.mapper.spi.MapperProvider} to the list of providers loaded
         * from system service loader with a custom priority.
         *
         * @param provider a mapper provider instance
         * @param priority priority of the provider (see {@link io.helidon.common.serviceloader.HelidonServiceLoader}
         *                 documentation for details about priority handling)
         * @return updated builder instance
         * @see #addMapperProvider(io.helidon.common.mapper.spi.MapperProvider)
         */
        public Builder addMapperProvider(MapperProvider provider, int priority) {
            this.providers.addService(provider, priority);
            return this;
        }

        /**
         * Add a mapper to the list of mapper.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType class of the source instance
         * @param targetType class of the target instance
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper, Class<S> sourceType, Class<T> targetType) {
            return addMapper(mapper, sourceType, targetType, Prioritized.DEFAULT_PRIORITY);
        }

        /**
         * Add a mapper to the list of mapper with a custom priority.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType class of the source instance
         * @param targetType class of the target instance
         * @param priority   order of the mapper usage
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper, Class<S> sourceType, Class<T> targetType, int priority) {
            this.providers.addService(new MapperProvider() {
                @SuppressWarnings({"unchecked", "ObjectEquality"})
                @Override
                public <FROM, TO> Optional<Mapper<?, ?>> mapper(Class<FROM> sourceClass,
                                                                    Class<TO> targetClass) {
                    if ((sourceType == sourceClass) && (targetType == targetClass)) {
                        return Optional.of((Mapper<FROM, TO>) mapper);
                    }
                    return Optional.empty();
                }
            }, priority);
            return this;
        }

        /**
         * Add a mapper to the list of mapper.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType generic type of the source instance
         * @param targetType generic type of the target instance
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper, GenericType<S> sourceType, GenericType<T> targetType) {
            return addMapper(mapper, sourceType, targetType, Prioritized.DEFAULT_PRIORITY);
        }

        /**
         * Add a mapper to the list of mapper with custom priority.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType generic type of the source instance
         * @param targetType generic type of the target instance
         * @param priority   order of the mapper usage
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper, GenericType<S> sourceType, GenericType<T> targetType, int priority) {
            this.providers.addService(new MapperProvider() {
                @Override
                public <FROM, TO> Optional<Mapper<?, ?>> mapper(Class<FROM> sourceClass, Class<TO> targetClass) {
                    return Optional.empty();
                }

                @SuppressWarnings({"unchecked"})
                @Override
                public <FROM, TO> Optional<Mapper<?, ?>> mapper(GenericType<FROM> sourceClass,
                                                                    GenericType<TO> targetClass) {
                    if ((sourceType.equals(sourceClass)) && (targetType.equals(targetClass))) {
                        return Optional.of((Mapper<FROM, TO>) mapper);
                    }
                    return Optional.empty();
                }
            }, priority);
            return this;
        }

        /**
         * Whether to use built-in mappers.
         *
         * @return updated builder
         */
        public Builder useBuiltIn(boolean useBuiltIn) {
            this.useBuiltIn = true;
            return this;
        }

        // used by the implementation
        List<MapperProvider> mapperProviders() {
            return providers.build().asList();
        }

        private Builder mapperProviders(HelidonServiceLoader<MapperProvider> serviceLoader) {
            providers = HelidonServiceLoader.builder(ServiceLoader.load(MapperProvider.class))
                    .useSystemServiceLoader(false);

            serviceLoader.forEach(providers::addService);
            builtInsAdded = false;
            return this;
        }
    }
}
