/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link ConfigValues}.
 */
class ConfigValuesTest {
    private static Config empty;
    private static Config full;

    @BeforeAll
    static void initClass() {
        empty = Config.empty();
        Map<String, String> theMap = Map.of(
                "first", "124"
        );
        full = Config.create(ConfigSources.create(theMap));
    }

    @Test
    void testEmptyNode() {
        ConfigValue<String> as = empty.as(String.class);

        assertEmpty(as, "defaultValue", "");

        ConfigValue<Integer> convert = as.as(Integer::parseInt);
        assertEmpty(convert, 1024, "");
    }

    @Test
    void testFullNode() {
        String key = "first";
        String expectedValue = "124";
        ConfigValue<String> as = full.get(key).asString();

        assertValue(as, "defaultValue", key, expectedValue);
        ConfigValue<Integer> convert = as.as(Integer::parseInt);
        assertValue(convert, 1024, key, 124);
    }

    private <T> void assertValue(ConfigValue<T> as, T defaultValue, String key, T expectedValue) {
        assertThat(as.get(), is(expectedValue));
        assertThat(as.orElse(defaultValue), is(expectedValue));
        assertThat(as.asOptional(), is(Optional.of(expectedValue)));
        assertThat(as.name(), is(key));
        assertThat(as.key(), is(Config.Key.create(key)));
        assertThat(as.optionalSupplier().get(), is(Optional.of(expectedValue)));
        Supplier<T> supplier = as.supplier();
        assertThat(supplier.get(), is(expectedValue));
        supplier = as.supplier(defaultValue);
        assertThat(supplier.get(), is(expectedValue));

        AtomicReference<T> found = new AtomicReference<>();
        as.ifPresent(found::set);
        assertThat(found.get(), is(expectedValue));
    }

    private <T> void assertEmpty(ConfigValue<T> as, T defaultValue, String key) {
        assertThrows(MissingValueException.class, as::get);
        assertThat(as.orElse(defaultValue), is(defaultValue));
        assertThat(as.asOptional(), is(Optional.empty()));
        assertThat(as.name(), is(key));
        assertThat(as.key(), is(Config.Key.create(key)));
        assertThat(as.optionalSupplier().get(), is(Optional.empty()));
        Supplier<T> supplier = as.supplier();
        assertThrows(MissingValueException.class, supplier::get);
        supplier = as.supplier(defaultValue);
        assertThat(supplier.get(), is(defaultValue));

        as.ifPresent(theValue -> fail("This config is empty"));
    }
}