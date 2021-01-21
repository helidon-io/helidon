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

package io.helidon.config.mp;

import java.util.NoSuchElementException;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MpConfigReferenceTest {
    private static final String VALUE_1 = "value";
    private static final String VALUE_2 = "hodnota";
    
    private static Config config;

    @BeforeAll
    static void initClass() {
        System.setProperty("value2", VALUE_2);

        config = ConfigProviderResolver.instance()
                .getBuilder()
                .addDefaultSources()
                .build();
    }

    @Test
    void testValue1() {
        test("1", VALUE_1);
    }

    @Test
    void testValue2() {
        test("2", VALUE_2);
    }

    @Test
    void testBoth() {
        test("3", "1", VALUE_1 + "-" + VALUE_2);
    }

    @Test
    void testMissingRefs() {
        String key1 = "referencing4-1";
        assertThrows(NoSuchElementException.class, () -> config.getValue(key1, String.class));

        String key2 = "referencing4-2";
        assertThrows(NoSuchElementException.class, () -> config.getValue(key2, String.class));
    }

    private void test(String prefix, String value) {
        test(prefix, "1", value);
        test(prefix, "2", value + "-ref");
        test(prefix, "3", "ref-" + value);
        test(prefix, "4", "ref-" + value + "-ref");
    }

    private void test(String prefix, String suffix, String value) {
        String key = "referencing" + prefix + "-" + suffix;
        String configured = config.getValue(key, String.class);

        assertThat("Value for key " + key, configured, notNullValue());
        assertThat("Value for key " + key, configured, is(value));
    }
}
