/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.Set;
import java.util.function.Function;

import io.helidon.common.GenericType;

/**
 * A generic and general approach to mapping two types.
 * A mapper is unidirectional - from {@code SOURCE} to {@code TARGET}.
 *
 * @param <SOURCE> type of the supported source
 * @param <TARGET> type of the supported target
 */
@FunctionalInterface
public interface Mapper<SOURCE, TARGET> extends Function<SOURCE, TARGET> {
    /**
     * Map an instance of source type to an instance of target type.
     *
     * @param source object to map
     * @return result of the mapping
     */
    TARGET map(SOURCE source);

    @Override
    default TARGET apply(SOURCE source){
        return map(source);
    }

    /**
     * Source type of the mapper. This does not need to be implemented when registered using
     * {@link io.helidon.common.mapper.MappersConfig.Builder} {@code addMapper} methods.
     * <p>
     * It MUST be implemented when implementing a service (i.e. {@link io.helidon.service.registry.Service.Singleton}).
     *
     * @return type of the source this mapper support
     */
    default GenericType<SOURCE> sourceType() {
        throw new IllegalStateException("If you create a mapper service, you need to implement the sourceType() and targetType() "
                                                + "methods");
    }

    /**
     * Target type of the mapper. This does not need to be implemented when registered using
     * {@link io.helidon.common.mapper.MappersConfig.Builder} {@code addMapper} methods.
     * <p>
     * It MUST be implemented when implementing a service (i.e. {@link io.helidon.service.registry.Service.Singleton}).
     *
     * @return type of the target this mapper support
     */
    default GenericType<TARGET> targetType() {
        throw new IllegalStateException("If you create a mapper service, you need to implement the sourceType() and targetType() "
                                                + "methods");
    }

    /**
     * Qualifiers of the mapper. This is only used when the mapper is provided as
     * {@link io.helidon.service.registry.ServiceRegistry} service, otherwise qualifiers provided when registering this
     * mapper are used.
     *
     * @return qualifiers of this mapper, defaults to empty set
     */
    default Set<String> qualifiers() {
        return Set.of();
    }
}
