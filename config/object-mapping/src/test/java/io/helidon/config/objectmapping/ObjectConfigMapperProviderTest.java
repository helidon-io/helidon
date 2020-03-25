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

package io.helidon.config.objectmapping;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.config.MissingValueException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link ObjectConfigMapperProvider}.
 */
class ObjectConfigMapperProviderTest {
    public static final String TEST_MESSAGE = "testValue";
    private static ObjectConfigMapperProvider provider;
    private static Config empty;
    private static Config full;

    @BeforeAll
    static void initClass() {
        provider = new ObjectConfigMapperProvider();
        empty = Config.empty();
        Map<String, String> configMap = Map.of(
                Configurables.WithCreateConfig.CONFIG_KEY, TEST_MESSAGE
        );
        full = Config.builder(ConfigSources.create(configMap))
                .build();
    }

    @AfterEach
    void postChecks() {
        assertThat("Mappers must be empty", provider.mappers().size(), is(0));
    }


    @Test
    void testCreateMethod() {
        Optional<Function<Config, Configurables.WithCreateConfig>> mapper = provider.mapper(Configurables.WithCreateConfig.class);
        assertThat(mapper, not(Optional.empty()));

        Function<Config, Configurables.WithCreateConfig> mapperFunction = mapper.get();
        Configurables.WithCreateConfig instance = mapperFunction.apply(empty.get("WithCreateConfig"));

        assertThat(instance, notNullValue());
    }
    @Test
    void testCreateMethodFromEmptyConfig() {
        String expectedConfigKey = Configurables.WithCreateConfig.CONFIG_KEY;
        ConfigValue<Configurables.WithCreateConfig> instance = empty.get(expectedConfigKey)
                .as(Configurables.WithCreateConfig.class);

        assertThat(instance, notNullValue());

        assertThat(instance.asOptional(), is(Optional.empty()));
        Configurables.WithCreateConfig defaultValue = new Configurables.WithCreateConfig("default");
        assertThat(instance.orElse(defaultValue), sameInstance(defaultValue));
        assertThrows(MissingValueException.class, instance::get);

        Supplier<Optional<Configurables.WithCreateConfig>> optionalSupplier = instance.optionalSupplier();
        assertThat(optionalSupplier, notNullValue());
        assertThat(optionalSupplier.get(), is(Optional.empty()));

        Supplier<Configurables.WithCreateConfig> supplier = instance.supplier();
        assertThrows(MissingValueException.class, supplier::get);

        supplier = instance.supplier(defaultValue);
        assertThat(supplier.get(), sameInstance(defaultValue));

        assertThat(instance.key(), is(Config.Key.create(expectedConfigKey)));
        assertThat(instance.name(), is(expectedConfigKey));
        instance.ifPresent(wcc -> fail("Key is not present, should have failed."));
    }
    @Test
    void testCreateMethodFromConfig() {
        String expectedConfigKey = Configurables.WithCreateConfig.CONFIG_KEY;
        ConfigValue<Configurables.WithCreateConfig> instance = full.get(expectedConfigKey)
                .as(Configurables.WithCreateConfig.class);

        assertThat(instance, notNullValue());

        assertThat(instance.asOptional(), not(Optional.empty()));
        Configurables.WithCreateConfig defaultValue = new Configurables.WithCreateConfig("default");
        assertThat(instance.orElse(defaultValue), not(sameInstance(defaultValue)));
        assertThat(instance.get().message(), is(TEST_MESSAGE));

        Supplier<Optional<Configurables.WithCreateConfig>> optionalSupplier = instance.optionalSupplier();
        assertThat(optionalSupplier, notNullValue());
        assertThat(optionalSupplier.get(), not(Optional.empty()));
        assertThat(optionalSupplier.get().get().message(), is(TEST_MESSAGE));

        Supplier<Configurables.WithCreateConfig> supplier = instance.supplier();
        assertThat(supplier.get().message(), is(TEST_MESSAGE));

        supplier = instance.supplier(defaultValue);
        assertThat(supplier.get(), not(sameInstance(defaultValue)));
        assertThat(supplier.get().message(), is(TEST_MESSAGE));

        assertThat(instance.key(), is(Config.Key.create(expectedConfigKey)));
        assertThat(instance.name(), is(expectedConfigKey));

        instance.ifPresent(wcc -> assertThat(wcc.message(), is(TEST_MESSAGE)));
    }
}