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

import java.util.NoSuchElementException;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.common.mapper.spi.MapperProvider.ProviderResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link MapperManager}.
 */
@SuppressWarnings("removal")
class MapperManagerTestDeprecated {
    @Test
    void testUsingServiceLoader() {
        MapperManager mm = MapperManager.create();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class, "default");
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceRegistryMapper.STRING_TYPE, ServiceRegistryMapper.INTEGER_TYPE, "default");
        assertThat(result, is(11));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceRegistryMapper.STRING_TYPE, GenericType.create(Long.class), "default");
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class, "default");
        assertThat(longResult, is(10L));

        Short shortResult = mm.map(source, String.class, Short.class, "default");
        assertThat(shortResult, is((short) 10));
        // must be the same
        shortResult = mm.map(source, ServiceRegistryMapper.STRING_TYPE, ServiceRegistryMapper.SHORT_TYPE, "default");
        assertThat(shortResult, is((short) 10));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Object.class, "default"));
    }

    @Test
    void testUsingCustomProviders() {
        MapperManager mm = MapperManager.builder()
                .discoverServices(false)
                .addMapperProvider(new ServiceLoaderMapper())
                .build();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class, "default");
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceRegistryMapper.STRING_TYPE, ServiceRegistryMapper.INTEGER_TYPE, "default");
        assertThat(result, is(10));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceRegistryMapper.STRING_TYPE, GenericType.create(Long.class), "default");
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class, "default");
        assertThat(longResult, is(10L));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Short.class, "default"));
        assertThrows(MapperException.class, () -> mm.map(source, ServiceRegistryMapper.STRING_TYPE,
                                                         ServiceRegistryMapper.SHORT_TYPE, "default"));
        assertThrows(MapperException.class, () -> mm.map(source, String.class, Object.class, "default"));
    }

    @Test
    void testUsingServiceLoaderAndCustomMappers() {
        MapperManager mm = MapperManager.builder()
                .addMapper(String::valueOf, Integer.class, String.class)
                .addMapper(String::valueOf, ServiceRegistryMapper.SHORT_TYPE, ServiceRegistryMapper.STRING_TYPE)
                .build();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class, "default");
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceRegistryMapper.STRING_TYPE, ServiceRegistryMapper.INTEGER_TYPE, "default");
        assertThat(result, is(11));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceRegistryMapper.STRING_TYPE, GenericType.create(Long.class), "default");
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class, "default");
        assertThat(longResult, is(10L));

        Short shortResult = mm.map(source, String.class, Short.class, "default");
        assertThat(shortResult, is((short) 10));
        // must be the same
        shortResult = mm.map(source, ServiceRegistryMapper.STRING_TYPE, ServiceRegistryMapper.SHORT_TYPE, "default");
        assertThat(shortResult, is((short) 10));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Object.class, "default"));

        // and add tests for integer and short types
        String stringResult = mm.map(42, Integer.class, String.class, "default");
        assertThat(stringResult, is("42"));
        stringResult = mm.map(42, GenericType.create(Integer.class), ServiceRegistryMapper.STRING_TYPE, "default");
        assertThat(stringResult, is("42"));

        stringResult = mm.map((short) 42, Short.class, String.class, "default");
        assertThat(stringResult, is("42"));
        stringResult = mm.map((short) 42, ServiceRegistryMapper.SHORT_TYPE, ServiceRegistryMapper.STRING_TYPE, "default");
        assertThat(stringResult, is("42"));
    }

    @Test
    void testQualifiedMapping() {
        MapperManager mapperManager = MapperManager.builder()
                .addMapperProvider((t1, t2, qualifier) -> {
                    if (qualifier.equals("http")) {
                        return new ProviderResponse(MapperProvider.Support.SUPPORTED, req -> "http_" + req);
                    }
                    return ProviderResponse.unsupported();
                })
                .addMapperProvider((t1, t2, qualifier) -> {
                    if (qualifier.equals("http/header")) {
                        return new ProviderResponse(MapperProvider.Support.SUPPORTED, req -> "http_header_" + req);
                    }
                    return ProviderResponse.unsupported();
                })
                .addMapperProvider((t1, t2, qualifier) -> {
                    if (qualifier.equals("http/query")) {
                        return new ProviderResponse(MapperProvider.Support.SUPPORTED, req -> "http_query_" + req);
                    }
                    return ProviderResponse.unsupported();
                })
                .build();

        assertThrows(MapperException.class, () -> mapperManager.map("value", String.class, String.class, ""));

        // http qualifier exists
        String value = mapperManager.map("value", String.class, String.class, "http");
        assertThat(value, is("http_value"));

        // http/header qualifier exist
        value = mapperManager.map("value", String.class, String.class, "http", "header");
        assertThat(value, is("http_header_value"));

        // http/query qualifier exists
        value = mapperManager.map("value", String.class, String.class, "http", "query");
        assertThat(value, is("http_query_value"));

        // should fall back to http qualifier
        value = mapperManager.map("value", String.class, String.class, "http", "matrix");
        assertThat(value, is("http_value"));
    }

    @Test
    void testExistingValue() {
        // int -> double
        // not double to int

        MapperManager mapperManager = MapperManager.builder()
                .useBuiltIn(true)
                .discoverServices(false)
                .addMapperProvider((t1, t2, qualifier) -> {
                    if (t1.equals(Integer.class) && t2.equals(Double.class)) {
                        return new ProviderResponse(MapperProvider.Support.SUPPORTED, anInt -> ((Integer) anInt).doubleValue());
                    }
                    return ProviderResponse.unsupported();
                })
                .build();

        Value<String> value = Value.create(mapperManager, "name", "42");
        assertThat(value.get(), is("42"));

        Value<Integer> integerValue = value.as(Integer.class);
        assertThat(integerValue.get(), is(42));

        value = integerValue.asString();
        assertThat(value.get(), is("42"));

        Value<Double> doubleValue = integerValue.as(Double.class);
        assertThat(doubleValue.get(), is(42D));

        assertThrows(MapperException.class, () -> doubleValue.as(Integer.class));
    }

    @Test
    void testEmptyValue() {
        // int -> double
        // not double to int

        MapperManager mapperManager = MapperManager.builder()
                .useBuiltIn(true)
                .addMapperProvider((t1, t2, qualifier) -> {
                    if (t1.equals(Integer.class) && t2.equals(Double.class)) {
                        return new ProviderResponse(MapperProvider.Support.SUPPORTED, anInt -> ((Integer) anInt).doubleValue());
                    }
                    return ProviderResponse.unsupported();
                })
                .build();

        OptionalValue<String> value = OptionalValue.create(mapperManager, "name", String.class);
        assertThrows(NoSuchElementException.class, value::get);
        assertThat(value.isPresent(), is(false));
        assertThat(value.isEmpty(), is(true));

        OptionalValue<Integer> integerValue = value.as(Integer.class);
        assertThrows(NoSuchElementException.class, integerValue::get);
        assertThat(integerValue.isPresent(), is(false));
        assertThat(integerValue.isEmpty(), is(true));

        value = integerValue.asString();
        assertThrows(NoSuchElementException.class, value::get);
        assertThat(value.isPresent(), is(false));
        assertThat(value.isEmpty(), is(true));

        OptionalValue<Double> doubleValue = integerValue.as(Double.class);
        assertThrows(NoSuchElementException.class, doubleValue::get);
        assertThat(doubleValue.isPresent(), is(false));
        assertThat(doubleValue.isEmpty(), is(true));

        assertThrows(MapperException.class, () -> doubleValue.as(Integer.class));
    }

    @Test
    void testCacheWorks() {
        MappersImpl mm = new MappersImpl(MapperManager.builder()
                                                             .addMapperProvider(new TestProvider()));
        assertThat(mm.classCacheSize(), is(0));
        assertThat(mm.typeCacheSize(), is(0));

        mm.map("value", String.class, String.class, "value");
        mm.map("value", String.class, String.class, "value");
        mm.map("value", String.class, String.class, "value");
        assertThat(mm.classCacheSize(), is(1));

        mm.map("value", GenericType.STRING, GenericType.STRING, "value");
        mm.map("value", GenericType.STRING, GenericType.STRING, "value");
        mm.map("value", GenericType.STRING, GenericType.STRING, "value");
        assertThat(mm.typeCacheSize(), is(1));
    }

    private static class TestProvider implements MapperProvider {
        @Override
        public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
            return new ProviderResponse(Support.SUPPORTED, req -> req);
        }

        @Override
        public ProviderResponse mapper(GenericType<?> sourceType, GenericType<?> targetType, String qualifier) {
            return MapperProvider.super.mapper(sourceType, targetType, qualifier);
        }
    }
}