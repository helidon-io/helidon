/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.mapper.spi.MapperProvider;

class BuiltInMappers implements MapperProvider {
    private static final Map<ClassPair, Mapper<?, ?>> MAPPERS;

    static {
        Map<ClassPair, Mapper<?, ?>> mappers = new HashMap<>();
        addStringMapper(mappers, URI.class, BuiltInMappers::asUri);
        addStringMapper(mappers, Byte.class, BuiltInMappers::asByte);

        MAPPERS = Map.copyOf(mappers);
    }

    private static <T> void addStringMapper(Map<ClassPair, Mapper<?, ?>> mappers,
                                            Class<T> targetType,
                                            Function<String, T> mapperFx) {
        Mapper<String, T> mapper = mapperFx::apply;
        mappers.put(new ClassPair(String.class, targetType), mapper);
    }

    private static Byte asByte(String value) {
        return Byte.parseByte(value);
    }

    private static URI asUri(String value) {
        return URI.create(value);
    }

    @Override
    public <SOURCE, TARGET> Optional<Mapper<?, ?>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass) {
        return Optional.empty();
    }

    private static final class ClassPair {
        private final Class<?> source;
        private final Class<?> target;

        ClassPair(Class<?> source, Class<?> target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassPair classPair = (ClassPair) o;
            return source.equals(classPair.source) && target.equals(classPair.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target);
        }
    }
}
