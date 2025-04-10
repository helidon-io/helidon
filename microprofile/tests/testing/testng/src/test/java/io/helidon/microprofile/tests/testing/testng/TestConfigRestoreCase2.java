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
package io.helidon.microprofile.tests.testing.testng;

import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@EnabledIfParameter(key = "TestConfigRestore", value = "true")
@HelidonTest
@AddConfigBlock(value = """
        foo=configBlock
        config_ordinal=1000
        """)
public class TestConfigRestoreCase2 {

    @Inject
    @ConfigProperty(name = "foo")
    String value;

    @Test
    void testSynthetic() {
        assertThat(value, is("configBlock"));
    }
}
