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

import java.util.ServiceLoader;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.common.serviceloader.HelidonServiceLoader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link MapperManager}.
 */
class MapperManagerTest {
    @Test
    void testUsingServiceLoader() {
        MapperManager mm = MapperManager.create();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class);
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.INTEGER_TYPE);
        assertThat(result, is(11));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, GenericType.create(Long.class));
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class);
        assertThat(longResult, is(10L));

        Short shortResult = mm.map(source, String.class, Short.class);
        assertThat(shortResult, is((short) 10));
        // must be the same
        shortResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.SHORT_TYPE);
        assertThat(shortResult, is((short) 10));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Object.class));
    }

    @Test
    void testUsingCustomProviders() {
        MapperManager mm = MapperManager.builder(HelidonServiceLoader.builder(ServiceLoader.load(MapperProvider.class))
                                                         .useSystemServiceLoader(false)
                                                         .build())
                .addMapperProvider(new ServiceLoaderMapper1())
                .build();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class);
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.INTEGER_TYPE);
        assertThat(result, is(10));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, GenericType.create(Long.class));
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class);
        assertThat(longResult, is(10L));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Short.class));
        assertThrows(MapperException.class, () -> mm.map(source, ServiceLoaderMapper2.STRING_TYPE,
                                                         ServiceLoaderMapper2.SHORT_TYPE));
        assertThrows(MapperException.class, () -> mm.map(source, String.class, Object.class));
    }

    @Test
    void testUsingServiceLoaderAndCustomMappers() {
        MapperManager mm = MapperManager.builder()
                .addMapper(String::valueOf, Integer.class, String.class)
                .addMapper(String::valueOf, ServiceLoaderMapper2.SHORT_TYPE, ServiceLoaderMapper2.STRING_TYPE)
                .build();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class);
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.INTEGER_TYPE);
        assertThat(result, is(11));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, GenericType.create(Long.class));
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class);
        assertThat(longResult, is(10L));

        Short shortResult = mm.map(source, String.class, Short.class);
        assertThat(shortResult, is((short) 10));
        // must be the same
        shortResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.SHORT_TYPE);
        assertThat(shortResult, is((short) 10));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Object.class));

        // and add tests for integer and short types
        String stringResult = mm.map(42, Integer.class, String.class);
        assertThat(stringResult, is("42"));
        stringResult = mm.map(42, GenericType.create(Integer.class), ServiceLoaderMapper2.STRING_TYPE);
        assertThat(stringResult, is("42"));

        stringResult = mm.map((short)42, Short.class, String.class);
        assertThat(stringResult, is("42"));
        stringResult = mm.map((short)42, ServiceLoaderMapper2.SHORT_TYPE, ServiceLoaderMapper2.STRING_TYPE);
        assertThat(stringResult, is("42"));
    }
}