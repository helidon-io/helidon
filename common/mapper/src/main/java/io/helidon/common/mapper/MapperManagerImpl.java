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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.spi.MapperProvider;

/**
 * Implementation of {@link io.helidon.common.mapper.MapperManager}.
 */
final class MapperManagerImpl implements MapperManager {
    @SuppressWarnings("rawtypes") private static final Mapper NOOP = o -> o;
    private static final Map<Class<?>, Class<?>> REPLACED_TYPES = new HashMap<>();

    static {
        REPLACED_TYPES.put(Byte.TYPE, Byte.class);
        REPLACED_TYPES.put(Short.TYPE, Short.class);
        REPLACED_TYPES.put(Integer.TYPE, Integer.class);
        REPLACED_TYPES.put(Long.TYPE, Long.class);
        REPLACED_TYPES.put(Float.TYPE, Float.class);
        REPLACED_TYPES.put(Double.TYPE, Double.class);
        REPLACED_TYPES.put(Boolean.TYPE, Boolean.class);
        REPLACED_TYPES.put(Character.TYPE, Character.class);
    }

    private final List<MapperProvider> providers;
    private final Map<ClassCacheKey, Mapper<?, ?>> classCache = new ConcurrentHashMap<>();
    private final Map<GenericCacheKey, Mapper<?, ?>> typeCache = new ConcurrentHashMap<>();

    MapperManagerImpl(Builder builder) {
        this.providers = builder.mapperProviders();
    }

    @SuppressWarnings("unchecked")
    static <SOURCE extends TARGET, TARGET> Mapper<SOURCE, TARGET> noopMapper() {
        return NOOP;
    }

    private static <SOURCE, TARGET> Mapper<SOURCE, TARGET> notFoundMapper(GenericType<SOURCE> sourceType,
                                                                          GenericType<TARGET> targetType) {
        return source -> {
            throw new MapperException(sourceType, targetType, "Failed to find mapper.");
        };
    }

    @Override
    public <SOURCE, TARGET> TARGET map(SOURCE source, GenericType<SOURCE> sourceType, GenericType<TARGET> targetType) {
        try {
            return findMapper(sourceType, targetType, false)
                    .map(source);
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, sourceType, targetType, e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType) {
        Class<SOURCE> sourceTypeToUse = sourceType;
        Class<TARGET> targetTypeToUse = targetType;

        if (sourceType.isPrimitive()) {
            sourceTypeToUse = (Class<SOURCE>) REPLACED_TYPES.get(sourceTypeToUse);
            if (sourceTypeToUse == null) {
                throw new MapperException(GenericType.create(source),
                                          GenericType.create(targetType),
                                          "Cannot find boxed type for source primitive type.");
            }
        }
        if (targetType.isPrimitive()) {
            targetTypeToUse = (Class<TARGET>) REPLACED_TYPES.get(targetTypeToUse);
            if (targetTypeToUse == null) {
                throw new MapperException(GenericType.create(source),
                                          GenericType.create(targetType),
                                          "Cannot find boxed type for target primitive type.");
            }
        }

        try {
            return findMapper(sourceTypeToUse, targetTypeToUse, false)
                    .map(source);
        } catch (MapperException e) {
            throw e;
        } catch (Exception e) {
            throw createMapperException(source, GenericType.create(sourceType), GenericType.create(targetType), e);
        }
    }

    private RuntimeException createMapperException(Object source,
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
                                                               boolean fromTypes) {
        Mapper<?, ?> mapper = classCache.computeIfAbsent(new ClassCacheKey(sourceType, targetType), key -> {
            // first attempt to find by classes
            return fromProviders(sourceType, targetType)
                    .orElseGet(() -> {
                        GenericType<SOURCE> sourceGenericType = GenericType.create(sourceType);
                        GenericType<TARGET> targetGenericType = GenericType.create(targetType);
                        if (targetType.isAssignableFrom(sourceType)) {
                            // the same type
                            return noopMapper();
                        }
                        if (fromTypes) {
                            return notFoundMapper(sourceGenericType, targetGenericType);
                        }
                        return findMapper(sourceGenericType, targetGenericType, true);
                    });
        });
        return (Mapper<SOURCE, TARGET>) mapper;
    }

    @SuppressWarnings("unchecked")
    private <SOURCE, TARGET> Mapper<SOURCE, TARGET> findMapper(GenericType<SOURCE> sourceType,
                                                               GenericType<TARGET> targetType,
                                                               boolean fromClasses) {
        Mapper<?, ?> mapper = typeCache.computeIfAbsent(new GenericCacheKey(sourceType, targetType), key -> {
            // first attempt to find by types
            return fromProviders(sourceType, targetType)
                    .orElseGet(() -> {
                        // and then by classes (unless we are already called from findMapper(Class, Class)
                        if (!fromClasses && (sourceType.isClass() && targetType.isClass())) {
                            return findMapper((Class<SOURCE>) sourceType.rawType(), (Class<TARGET>) targetType.rawType(), true);
                        }
                        if (sourceType.equals(targetType)) {
                            return it -> it;
                        }
                        return notFoundMapper(sourceType, targetType);
                    });
        });
        return (Mapper<SOURCE, TARGET>) mapper;
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(Class<SOURCE> sourceType,
                                                                  Class<TARGET> targetType) {
        return providers.stream()
                .flatMap(provider -> provider.mapper(sourceType, targetType).stream())
                .findFirst();
    }

    private <SOURCE, TARGET> Optional<Mapper<?, ?>> fromProviders(GenericType<SOURCE> sourceType,
                                                                  GenericType<TARGET> targetType) {
        return providers.stream()
                .flatMap(provider -> provider.mapper(sourceType, targetType).stream())
                .findFirst();
    }

    private static final class GenericCacheKey {
        private final GenericType<?> sourceType;
        private final GenericType<?> targetType;

        private GenericCacheKey(GenericType<?> sourceType, GenericType<?> targetType) {
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GenericCacheKey)) {
                return false;
            }
            GenericCacheKey that = (GenericCacheKey) o;
            return sourceType.equals(that.sourceType)
                    && targetType.equals(that.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceType, targetType);
        }
    }

    private static final class ClassCacheKey {
        private final Class<?> sourceType;
        private final Class<?> targetType;

        private ClassCacheKey(Class<?> sourceType, Class<?> targetType) {
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ClassCacheKey)) {
                return false;
            }
            ClassCacheKey that = (ClassCacheKey) o;
            return sourceType.equals(that.sourceType)
                    && targetType.equals(that.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceType, targetType);
        }
    }
}
