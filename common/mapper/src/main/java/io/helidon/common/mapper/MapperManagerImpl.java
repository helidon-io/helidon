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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

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

    @Override
    public <SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(GenericType<SOURCE> sourceType,
                                                                    GenericType<TARGET> targetType,
                                                                    String... qualifiers) {
        Mapper<SOURCE, TARGET> mapper = findMapper(sourceType, targetType, false, qualifiers);
        if (mapper instanceof NotFoundMapper) {
            return Optional.empty();
        }
        return Optional.of(mapper);
    }

    int classCacheSize() {
        return classCache.size();
    }

    int typeCacheSize() {
        return typeCache.size();
    }

    @SuppressWarnings("unchecked")
    private static <SOURCE, TARGET> Mapper<SOURCE, TARGET> notFoundMapper(GenericType<SOURCE> sourceType,
                                                                          GenericType<TARGET> targetType,
                                                                          String... qualifier) {
        return new NotFoundMapper(sourceType, targetType, qualifier);
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

    @SuppressWarnings("unchecked")
    private <SOURCE, TARGET> Mapper<SOURCE, TARGET> findMapper(Class<SOURCE> sourceType,
                                                               Class<TARGET> targetType,
                                                               boolean fromTypes,
                                                               String... qualifiers) {
        Mapper<?, ?> mapper = classCache.computeIfAbsent(new ClassCacheKey(sourceType, targetType, List.of(qualifiers)), key -> {
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
        Mapper<?, ?> mapper = typeCache.computeIfAbsent(new GenericCacheKey(sourceType, targetType, List.of(qualifiers)), key -> {
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

    private Optional<Mapper<?, ?>>
    fromProviders(BiFunction<MapperProvider, String, MapperProvider.ProviderResponse> qualifiedMapper,
                  String... qualifiers) {

        Mapper<?, ?> compatible = null;
        for (int i = 0; i < qualifiers.length; i++) {
            String fullQ;
            if (i == 0) {
                fullQ = String.join("/", qualifiers);
            } else {
                fullQ = String.join("/", Arrays.copyOf(qualifiers, qualifiers.length - i));
            }
            for (MapperProvider provider : providers) {
                MapperProvider.ProviderResponse response = qualifiedMapper.apply(provider, fullQ);
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

        if (qualifiers.length == 0 || !qualifiers[0].isEmpty()) {
            for (MapperProvider provider : providers) {
                MapperProvider.ProviderResponse response = qualifiedMapper.apply(provider, "");
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

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(Class<SOURCE> sourceType,
                                                                  Class<TARGET> targetType,
                                                                  String... qualifiers) {

        return fromProviders((provider, qualifier) -> provider.mapper(sourceType, targetType, qualifier),
                             qualifiers);
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(GenericType<SOURCE> sourceType,
                                                                  GenericType<TARGET> targetType,
                                                                  String... qualifiers) {

        return fromProviders((provider, qualifier) -> provider.mapper(sourceType, targetType, qualifier),
                             qualifiers);
    }

    private record GenericCacheKey(GenericType<?> sourceType, GenericType<?> targetType, List<String> qualifiers) {
    }

    private record ClassCacheKey(Class<?> sourceType, Class<?> targetType, List<String> qualifiers) {
    }

    @SuppressWarnings("rawtypes")
    private static class NotFoundMapper implements Mapper {
        private final GenericType sourceType;
        private final GenericType targetType;
        private final String qualifierString;

        private NotFoundMapper(GenericType sourceType,
                               GenericType targetType,
                               String[] qualifier) {
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.qualifierString = String.join(",", qualifier);
        }

        @Override
        public Object map(Object source) {
            throw new MapperException(sourceType,
                                      targetType,
                                      "Failed to find mapper. Qualifiers: " + qualifierString
                                              + ", source of class '" + source.getClass().getName() + "'");
        }
    }
}
