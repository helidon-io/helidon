/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.tests.yaml;

import io.helidon.config.Config;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.tests.AbstractComplexConfigTest;
import io.helidon.config.yaml.YamlConfigParser;

/**
 * Tests {@link YamlConfigParser} in context of whole {@link Config} instance.
 */
public class YamlComplexConfigTest extends AbstractComplexConfigTest {
    @Override
    protected String getClasspathResourceName() {
        return "io/helidon/config/tests/yaml/YamlComplexConfigTest.yaml";
    }

    @Override
    protected ConfigParser createConfigParser() {
        return YamlConfigParser.create();
    }
}
