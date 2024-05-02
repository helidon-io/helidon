/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import jakarta.inject.Inject;

import io.helidon.microprofile.testing.junit5.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@HelidonTest
@AddConfigBlock(type = "Yaml", value = """
    another1:
      key: "another1.value"
    another2:
      key: "another2.value"
""")
class TestAddConfigBlockYaml {

    @Inject
    @ConfigProperty(name = "another1.key")
    private String another1;

    @Inject
    @ConfigProperty(name = "another2.key")
    private String another2;

    @Test
    void testValue() {
        assertThat(another1, is("another1.value"));
        assertThat(another2, is("another2.value"));
    }
}
