/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc.tests.support;

import java.util.List;
import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;

/**
 * Supplies a per-test Helidon config instance to service-registry based integration tests.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(1000)
public class TestConfigFactory implements Service.ServicesFactory<Config> {
    private static volatile Config config = Config.create();

    /**
     * Sets the config used by the next service registry.
     *
     * @param config config to expose
     */
    public static void config(Config config) {
        TestConfigFactory.config = Objects.requireNonNull(config);
    }

    /**
     * Restores an empty default config.
     */
    public static void reset() {
        config = Config.create();
    }

    @Override
    public List<Service.QualifiedInstance<Config>> services() {
        return List.of(Service.QualifiedInstance.create(config));
    }
}
