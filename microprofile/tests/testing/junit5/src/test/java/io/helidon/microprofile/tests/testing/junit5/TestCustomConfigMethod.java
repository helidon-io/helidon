/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.junit5;

import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
public class TestCustomConfigMethod {
    @Inject
    private BeanManager beanManager;

    @Inject
    @ConfigProperty(name = "second-key", defaultValue = "default")
    private String anotherValue;

    @Test
    @AddConfig(key = "second-key", value = "second-value")
    void testAddConfigOnMethod() {
        assertThat(beanManager, notNullValue());
        assertThat(anotherValue, is("second-value"));
    }

    @Test
    @AddConfigBlock("""
            second-key=second-value
            config_ordinal=1001
            """)
    void testAddConfigBlockOnMethod() {
        assertThat(beanManager, notNullValue());
        assertThat(anotherValue, is("second-value"));
    }
}
