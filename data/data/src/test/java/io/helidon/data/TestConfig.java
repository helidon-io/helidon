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
package io.helidon.data;

import io.helidon.common.config.Config;

class TestConfig implements ProviderConfig {
    private final Config config;
    private final String name;

    TestConfig(Config config, String name) {
        this.config = config;
        this.name = name;
    }

    static ProviderConfig create(Config config, String name) {
        return new TestConfig(config, name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "test";
    }

    Config config() {
        return config;
    }
}
