/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh4375;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

class YamlMpConfigTest {
    private Config config;

    @BeforeEach
    void initialize() {
        this.config = ConfigProvider.getConfig();
        Assertions.assertNotNull(this.config);
    }

    @Test
    void TestYamlMpConfigExistsInMicroprofileConfig() {
        Assertions.assertTrue(this.config.getValue("yamlMpConfigExists", Boolean.class));
    }
}
