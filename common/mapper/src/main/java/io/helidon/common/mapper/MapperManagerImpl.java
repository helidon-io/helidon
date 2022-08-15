/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Implementation of {@link io.helidon.common.mapper.MapperManager}.
 */
final class MapperManagerImpl implements MapperManager {
    private final List<MapperProvider> providers;
    private final Map<ClassCacheKey, Mapper<?, ?>> classCache = new ConcurrentHashMap<>();
    private final Map<GenericCacheKey, Mapper<?, ?>> typeCache = new ConcurrentHashMap<>();

    MapperManagerImpl(Builder builder) {
        this.providers = builder.mapperProviders();
    }

    @Override
    public <SOURCE, TARGET> TARGET map(SOURCE source,
                                       GenericType<SOURCE> sourceType,
                                       GenericType<TARGET> targetType,
                                       String... qualifiers) {
        try {
            return findMapper(sourceType, targetType, false, qualifiers)
                    .map(source);
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, sourceType, targetType, e);
        }
    }

    @Override
    public <SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType, String... qualifiers) {
        try {
            return findMapper(sourceType, targetType, false, qualifiers)
                    .map(source);
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, GenericType.create(sourceType), GenericType.create(targetType), e);
        }
    }

    private static <SOURCE, TARGET> Mapper<SOURCE, TARGET> notFoundMapper(GenericType<SOURCE> sourceType,
                                                                          GenericType<TARGET> targetType,
                                                                          String... qualifier) {
        String qualifierString = Arrays.toString(qualifier);
        return source -> {
            throw createMapperException(source,
                                        sourceType,
                                        targetType,
                                        "Failed to find mapper. Qualifiers: " + qualifierString + ".");
        };
    }

    private static RuntimeException createMapperException(Object source,
                                                          GenericType<?> sourceType,
                                                          GenericType<?> targetType,
                                                          Throwable throwable) {

        throw new MapperException(sourceType,
                                  targetType,
                                  "Failed to map source of class '" + source.getClass().getName() + "'",
                                  throwable);
    }

    private static RuntimeException createMapperException(Object source,
                                                          GenericType<?> sourceType,
                                                          GenericType<?> targetType,
                                                          String message) {

        throw new MapperException(sourceType,
                                  targetType,
                                  message + ", source of class '" + source.getClass().getName() + "'");
    }

    @SuppressWarnings("unchecked")
    private <SOURCE, TARGET> Mapper<SOURCE, TARGET> findMapper(Class<SOURCE> sourceType,
                                                               Class<TARGET> targetType,
                                                               boolean fromTypes,
                                                               String... qualifiers) {
        Mapper<?, ?> mapper = classCache.computeIfAbsent(new ClassCacheKey(sourceType, targetType, qualifiers), key -> {
            // first attempt to find by classes
            return fromProviders(sourceType, targetType, qualifiers)
                    .orElseGet(() -> {
                        GenericType<SOURCE> sourceGenericType = GenericType.create(sourceType);
                        GenericType<TARGET> targetGenericType = GenericType.create(targetType);
                        if (fromTypes) {
                            return notFoundMapper(sourceGenericType, targetGenericType, qualifiers);
                        }
                        return findMapper(sourceGenericType, targetGenericType, true, qualifiers);
                    });
        });
        return (Mapper<SOURCE, TARGET>) mapper;
    }

    @SuppressWarnings("unchecked")
    private <SOURCE, TARGET> Mapper<SOURCE, TARGET> findMapper(GenericType<SOURCE> sourceType,
                                                               GenericType<TARGET> targetType,
                                                               boolean fromClasses,
                                                               String... qualifiers) {
        Mapper<?, ?> mapper = typeCache.computeIfAbsent(new GenericCacheKey(sourceType, targetType, qualifiers), key -> {
            // first attempt to find by types
            return fromProviders(sourceType, targetType, qualifiers)
                    .orElseGet(() -> {
                        // and then by classes (unless we are already called from findMapper(Class, Class)
                        if (!fromClasses && (sourceType.isClass() && targetType.isClass())) {
                            return findMapper((Class<SOURCE>) sourceType.rawType(),
                                              (Class<TARGET>) targetType.rawType(),
                                              true,
                                              qualifiers);
                        }
                        return notFoundMapper(sourceType, targetType, qualifiers);
                    });
        });
        return (Mapper<SOURCE, TARGET>) mapper;
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(Class<SOURCE> sourceType,
                                                                  Class<TARGET> targetType,
                                                                  String... qualifiers) {
        Mapper<?, ?> compatible = null;

        for (String qualifier : qualifiers) {
            for (MapperProvider provider : providers) {
                MapperProvider.ProviderResponse response = provider.mapper(sourceType, targetType, qualifier);
                switch (response.support()) {
                case SUPPORTED -> {
                    return Optional.of(response.mapper());
                }
                case COMPATIBLE -> {
                    compatible = (compatible == null) ? response.mapper() : compatible;
                }
                default -> {
                }
                }
            }
        }

        return Optional.ofNullable(compatible);
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(GenericType<SOURCE> sourceType,
                                                                  GenericType<TARGET> targetType,
                                                                  String... qualifiers) {

        Mapper<?, ?> compatible = null;

        for (String qualifier : qualifiers) {
            for (MapperProvider provider : providers) {
                MapperProvider.ProviderResponse response = provider.mapper(sourceType, targetType, qualifier);
                switch (response.support()) {
                case SUPPORTED -> {
                    return Optional.of(response.mapper());
                }
                case COMPATIBLE -> {
                    compatible = (compatible == null) ? response.mapper() : compatible;
                }
                default -> {
                }
                }
            }
        }

        return Optional.ofNullable(compatible);
    }

    private record GenericCacheKey(GenericType<?> sourceType, GenericType<?> targetType, String... qualifiers) {
    }

    private record ClassCacheKey(Class<?> sourceType, Class<?> targetType, String... qualifiers) {
    }
}
