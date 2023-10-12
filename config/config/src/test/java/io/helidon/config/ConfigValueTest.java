/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for default methods of {@link ConfigValue}.
 */
class ConfigValueTest {
    private static final CustomValue EXISTING_VALUE = new CustomValue();
    private static final CustomValue DEFAULT_VALUE = new CustomValue();

    private static ConfigValue<CustomValue> emptyValue;
    private static ConfigValue<CustomValue> fullValue;

    @BeforeAll
    static void initClass() {
        emptyValue = new ConfigValue<CustomValue>() {
            @Override
            public Config.Key key() {
                return Config.Key.create("empty");
            }

            @Override
            public Optional<CustomValue> asOptional() {
                return Optional.empty();
            }

            @Override
            public <N> ConfigValue<N> as(Function<? super CustomValue, ? extends N> mapper) {
                return null;
            }

            @Override
            public <N> io.helidon.common.config.ConfigValue<N> as(Class<N> type) {
                throw new ConfigMappingException(key(), "test value cannot be mapped to other types.");
            }

            @Override
            public <N> io.helidon.common.config.ConfigValue<N> as(GenericType<N> type) {
                throw new ConfigMappingException(key(), "test value cannot be mapped to other types.");
            }

            @Override
            public Supplier<CustomValue> supplier() {
                return this::get;
            }

            @Override
            public Supplier<CustomValue> supplier(CustomValue defaultValue) {
                return () -> orElse(defaultValue);
            }

            @Override
            public Supplier<Optional<CustomValue>> optionalSupplier() {
                return this::asOptional;
            }
        };
        fullValue = new ConfigValue<CustomValue>() {
            @Override
            public Config.Key key() {
                return Config.Key.create("full");
            }

            @Override
            public Optional<CustomValue> asOptional() {
                return Optional.of(EXISTING_VALUE);
            }

            @Override
            public <N> ConfigValue<N> as(Function<? super CustomValue, ? extends N> mapper) {
                return null;
            }

            @Override
            public <N> io.helidon.common.config.ConfigValue<N> as(Class<N> type) {
                throw new ConfigMappingException(key(), "test value cannot be mapped to other types.");
            }

            @Override
            public <N> io.helidon.common.config.ConfigValue<N> as(GenericType<N> type) {
                throw new ConfigMappingException(key(), "test value cannot be mapped to other types.");
            }

            @Override
            public Supplier<CustomValue> supplier() {
                return this::get;
            }

            @Override
            public Supplier<CustomValue> supplier(CustomValue defaultValue) {
                return () -> orElse(defaultValue);
            }

            @Override
            public Supplier<Optional<CustomValue>> optionalSupplier() {
                return this::asOptional;
            }
        };
    }

    @Test
    void testNameFull() {
        assertThat(fullValue.name(), is("full"));
    }

    @Test
    void testNameEmpty() {
        assertThat(emptyValue.name(), is("empty"));
    }

    @Test
    void testGetFull() {
        assertThat(fullValue.get(), sameInstance(EXISTING_VALUE));
    }

    @Test
    void testGetEmpty() {
        assertThrows(MissingValueException.class, () -> emptyValue.get());
    }

    @Test
    void testOrFull() {
        assertThat(fullValue.or(() -> Optional.of(DEFAULT_VALUE)), is(Optional.of(EXISTING_VALUE)));
    }

    @Test
    void testOrEmpty() {
        assertThat(emptyValue.or(() -> Optional.of(DEFAULT_VALUE)), is(Optional.of(DEFAULT_VALUE)));
    }

    @Test
    void testIsPresentFull() {
        assertThat(fullValue.isPresent(), is(true));
    }

    @Test
    void testIsPresentEmpty() {
        assertThat(emptyValue.isPresent(), is(false));
    }

    @Test
    void testIfPresentOrElseFull() {
        AtomicBoolean called = new AtomicBoolean(false);
        fullValue.ifPresentOrElse(value -> {
                                      assertThat(value, is(EXISTING_VALUE));
                                      called.set(true);
                                  },
                                  () -> fail("Should be present"));
        assertThat(called.get(), is(true));
    }

    @Test
    void testIfPresentOrElseEmpty() {
        AtomicBoolean called = new AtomicBoolean(false);
        emptyValue.ifPresentOrElse(value -> fail("Empty should not have value, but had: " + value),
                                   () -> called.set(true));

        assertThat(called.get(), is(true));
    }

    @Test
    void testIfPresentFull() {
        AtomicBoolean called = new AtomicBoolean(false);
        fullValue.ifPresent(value -> {
            assertThat(value, is(EXISTING_VALUE));
            called.set(true);
        });
        assertThat(called.get(), is(true));
    }

    @Test
    void testIfPresentEmpty() {
        emptyValue.ifPresent(value -> fail("Empty should not have value: " + value));
    }

    @Test
    void testFilterFull() {
        assertThat(fullValue.filter(value -> value == EXISTING_VALUE), is(Optional.of(EXISTING_VALUE)));
        assertThat(fullValue.filter(value -> value != EXISTING_VALUE), is(Optional.empty()));
    }

    @Test
    void testFilterEmpty() {
        assertThat(emptyValue.filter(value -> value == EXISTING_VALUE), is(Optional.empty()));
        assertThat(emptyValue.filter(value -> value != EXISTING_VALUE), is(Optional.empty()));
    }

    @Test
    void testMapFull() {
        assertThat(fullValue.map(value -> value == EXISTING_VALUE), is(Optional.of(true)));
    }

    @Test
    void testMapEmpty() {
        assertThat(emptyValue.map(value -> value == EXISTING_VALUE), is(Optional.empty()));
    }

    @Test
    void testFlatMapFull() {
        assertThat(fullValue.flatMap(value -> Optional.of(DEFAULT_VALUE)), is(Optional.of(DEFAULT_VALUE)));
    }

    @Test
    void testFlatMapEmpty() {
        assertThat(emptyValue.flatMap(value -> Optional.of(DEFAULT_VALUE)), is(Optional.empty()));
    }

    @Test
    void testOrElseFull() {
        assertThat(fullValue.orElse(DEFAULT_VALUE), is(EXISTING_VALUE));
    }

    @Test
    void testOrElseEmpty() {
        assertThat(emptyValue.orElse(DEFAULT_VALUE), is(DEFAULT_VALUE));
    }

    @Test
    void testOrElseGetFull() {
        assertThat(fullValue.orElseGet(() -> DEFAULT_VALUE), is(EXISTING_VALUE));
    }

    @Test
    void testOrElseGetEmpty() {
        assertThat(emptyValue.orElseGet(() -> DEFAULT_VALUE), is(DEFAULT_VALUE));
    }

    @Test
    void testOrElseThrowFull() {
        assertThat(fullValue.orElseThrow(() -> new IllegalStateException("Not there")), is(EXISTING_VALUE));
    }

    @Test
    void testOrElseThrowEmpty() {
        assertThrows(IllegalStateException.class, () -> emptyValue.orElseThrow(() -> new IllegalStateException("Not there")));
    }

    @Test
    void testStreamFull() {
        List<CustomValue> collected = fullValue.stream()
                .collect(Collectors.toList());

        assertThat(collected, hasSize(1));
        assertThat(collected, contains(EXISTING_VALUE));
    }

    @Test
    void testStreamEmpty() {
        List<CustomValue> collected = emptyValue.stream()
                .collect(Collectors.toList());

        assertThat(collected, empty());
    }

    private static class CustomValue {
        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    }
}
