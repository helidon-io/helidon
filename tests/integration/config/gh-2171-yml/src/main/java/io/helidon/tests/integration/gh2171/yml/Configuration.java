/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh2171.yml;

import java.util.Optional;

import io.helidon.config.Config;

/**
 * Test that yaml is loaded and not yml when both on classpath.
 */
public class Configuration {
    private final Config config;

    private Configuration(Config config) {
        this.config = config;
    }

    static Configuration create() {
        Config config = Config.create();
        return new Configuration(config);
    }

    Optional<String> value(String key) {
        return config.get(key).asString().asOptional();
    }
}
