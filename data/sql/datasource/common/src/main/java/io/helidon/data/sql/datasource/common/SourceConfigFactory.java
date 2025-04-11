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
package io.helidon.data.sql.datasource.common;

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

// FIXME: Temporary workaround until it's fixed in Helidon

/**
 * {@link Config} factory.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(110)
class SourceConfigFactory implements Service.ServicesFactory<Config> {

    private final Config config;

    SourceConfigFactory() {
        config = Config.create();
    }

    @Override
    public List<Service.QualifiedInstance<Config>> services() {
        return List.of(Service.QualifiedInstance.create(config));
    }

}
