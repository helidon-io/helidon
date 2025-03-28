/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import jakarta.inject.Inject;

import io.helidon.microprofile.testing.junit5.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddConfigBlock("""
    some.key1=some.value1
    some.key2=some.value2
    """)
class TestAddConfigBlockProperties {

    @Inject
    @ConfigProperty(name = "some.key1")
    private String value1;

    @Inject
    @ConfigProperty(name = "some.key2")
    private String value2;

    @Test
    void testValue() {
        assertThat(value1, is("some.value1"));
        assertThat(value2, is("some.value2"));
    }
}
