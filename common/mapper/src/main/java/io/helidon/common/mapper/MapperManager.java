/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
import java.util.Set;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weighted;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Mapper manager of all configured mappers.
 * <p>
 * To map a source to target, you can use either of the {@code map} methods defined in this interface,
 * as they make sure that the mapping exists in either space.
 * <ul>
 * <li>If you call {@link #map(Object, Class, Class, String...)} and no mapper is found for the class pair,
 * the implementation calls the {@link #map(Object, io.helidon.common.GenericType, io.helidon.common.GenericType, String...)}
 * with {@link io.helidon.common.GenericType}s created for each parameters</li>
 * <li>If you call {@link #map(Object, io.helidon.common.GenericType, io.helidon.common.GenericType, String...)} and no mapper is
 * found for the {@link io.helidon.common.GenericType} pair, an attempt is to locate a mapper for
 * the underlying class *IF* the generic type represents a simple class (e.g. not a generic type declaration)</li>
 * </ul>
 */
public interface MapperManager {
    /**
     * Get an instance of the configured global manager. If none is explicitly set, an instance is created using
     * discovered and built-in mappers.
     *
     * @return global mapper manager
     */
    static MapperManager global() {
        return GlobalManager.mapperManager();
    }

    /**
     * Configure a new global mapper manager.
     *
     * @param manager mapper manager to use
     */
    static void global(MapperManager manager) {
        GlobalManager.mapperManager(manager);
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
     * Map from source to target.
     *
     * @param source     object to map
     * @param sourceType type of the source object (to locate the mapper)
     * @param targetType type of the target object (to locate the mapper)
     * @param qualifiers qualifiers of the usage (such as {@code http-headers, http}, most specific one first)
     * @param <SOURCE>   type of the source
     * @param <TARGET>   type of the target
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    <SOURCE, TARGET> TARGET map(SOURCE source,
                                GenericType<SOURCE> sourceType,
                                GenericType<TARGET> targetType,
                                String... qualifiers)
            throws MapperException;

    /**
     * Map from source to target.
     *
     * @param source     object to map
     * @param sourceType class of the source object (to locate the mapper)
     * @param targetType class of the target object (to locate the mapper)
     * @param qualifiers qualifiers of the usage (such as {@code http-headers, http}, most specific one first)
     * @param <SOURCE>   type of the source
     * @param <TARGET>   type of the target
     * @return result of the mapping
     * @throws MapperException in case the mapper was not found or failed
     */
    <SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType, String... qualifiers)
            throws MapperException;

    /**
     * Obtain a mapper for the provided types and qualifiers.
     *
     * @param sourceType type to map from
     * @param targetType type to map to
     * @param qualifiers qualifiers of the mapper
     * @return mapper if found
     * @param <SOURCE> source type
     * @param <TARGET> target type
     */
    <SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(GenericType<SOURCE> sourceType,
                                                             GenericType<TARGET> targetType,
                                                             String... qualifiers);

    /**
     * Fluent API builder for {@link io.helidon.common.mapper.MapperManager}.
     */
    final class Builder implements io.helidon.common.Builder<Builder, MapperManager> {
        private HelidonServiceLoader.Builder<MapperProvider> providers = HelidonServiceLoader
                .builder(ServiceLoader.load(MapperProvider.class));
        private boolean useBuiltIn;
        private boolean builtInAdded;
        private boolean discoverServices = true;

        private Builder() {
        }

        @Override
        public MapperManager build() {
            if (useBuiltIn && !builtInAdded) {
                providers.addService(new BuiltInMappers(), 10);
                builtInAdded = true;
            }
            providers.addService(new DefaultMapperProvider(), 0);
            providers.useSystemServiceLoader(discoverServices);
            return new MapperManagerImpl(this);
        }

        /**
         * Add a new {@link io.helidon.common.mapper.spi.MapperProvider} to the list of providers loaded from
         * system service loader.
         * <p>
         * You can control whether discovered services are loaded through see {@link #discoverServices(boolean)}
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
         * @param priority priority of the provider (see {@link io.helidon.common.HelidonServiceLoader}
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
         * @param qualifiers supported qualifiers of this mapper (if none provided, will return a compatible response)
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper, Class<S> sourceType, Class<T> targetType, String... qualifiers) {
            return addMapper(mapper, sourceType, targetType, Weighted.DEFAULT_WEIGHT, qualifiers);
        }

        /**
         * Add a mapper to the list of mapper with a custom priority.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType class of the source instance
         * @param targetType class of the target instance
         * @param weight     weight of the mapper
         * @param qualifiers supported qualifiers of this mapper (if none provided, will return a compatible response)
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper,
                                        Class<S> sourceType,
                                        Class<T> targetType,
                                        double weight,
                                        String... qualifiers) {
            Set<String> qualifierSet = Set.of(qualifiers);

            this.providers.addService((sourceClass, targetClass, qualifier) -> {
                if ((sourceType == sourceClass) && (targetType == targetClass)) {
                    if (qualifierSet.contains(qualifier)) {
                        return new MapperProvider.ProviderResponse(MapperProvider.Support.SUPPORTED, mapper);
                    } else {
                        return new MapperProvider.ProviderResponse(MapperProvider.Support.COMPATIBLE, mapper);
                    }
                }
                return MapperProvider.ProviderResponse.unsupported();
            }, weight);
            return this;
        }

        /**
         * Add a mapper to the list of mapper.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType generic type of the source instance
         * @param targetType generic type of the target instance
         * @param qualifiers qualifiers of this mapper, if empty, will be a compatible mapper
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper,
                                        GenericType<S> sourceType,
                                        GenericType<T> targetType,
                                        String... qualifiers) {
            return addMapper(mapper, sourceType, targetType, Weighted.DEFAULT_WEIGHT, qualifiers);
        }

        /**
         * Add a mapper to the list of mapper with custom priority.
         *
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType generic type of the source instance
         * @param targetType generic type of the target instance
         * @param weight     weight of the mapper
         * @param qualifiers qualifiers of this mapper, if empty, will be a compatible mapper
         * @param <S>        type of source
         * @param <T>        type of target
         * @return updated builder instance
         */
        public <S, T> Builder addMapper(Mapper<S, T> mapper,
                                        GenericType<S> sourceType,
                                        GenericType<T> targetType,
                                        double weight,
                                        String... qualifiers) {
            Set<String> qualifierSet = Set.of(qualifiers);

            this.providers.addService(new MapperProvider() {
                @Override
                public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
                    return ProviderResponse.unsupported();
                }

                @Override
                public ProviderResponse mapper(GenericType<?> source, GenericType<?> target, String qualifier) {
                    if ((sourceType.equals(source)) && (targetType.equals(target))) {
                        if (qualifierSet.contains(qualifier)) {
                            return new ProviderResponse(Support.SUPPORTED, mapper);
                        } else {
                            return new ProviderResponse(Support.COMPATIBLE, mapper);
                        }

                    }
                    return ProviderResponse.unsupported();
                }
            }, weight);
            return this;
        }

        /**
         * Whether to use built-in mappers.
         *
         * @param useBuiltIn whether to use built in mappers (such as String to Integer)
         * @return updated builder
         */
        public Builder useBuiltIn(boolean useBuiltIn) {
            this.useBuiltIn = useBuiltIn;
            return this;
        }

        /**
         * Whether to use {@link java.util.ServiceLoader} to discover mappers, defaults to {@code true}.
         *
         * @param discoverServices whether to discover services
         * @return updated builder
         */
        public Builder discoverServices(boolean discoverServices) {
            this.discoverServices = discoverServices;
            return this;
        }

        // used by the implementation
        List<MapperProvider> mapperProviders() {
            return providers.build().asList();
        }
    }
}
