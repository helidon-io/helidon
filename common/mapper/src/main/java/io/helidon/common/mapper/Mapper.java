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

import java.util.function.Function;

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
     * A mapper that returns the same instance.
     *
     * @param <SOURCE> type of source
     * @param <TARGET> type of target
     * @return mapper that returns the source without any mapping
     */
    static <SOURCE extends TARGET, TARGET> Mapper<SOURCE, TARGET> noop() {
        return MapperManagerImpl.noopMapper();
    }

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
}
