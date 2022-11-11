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

package io.helidon.nima.observe.config;

import io.helidon.config.Config;
import io.helidon.nima.observe.spi.ObserveProvider;
import io.helidon.nima.webserver.http.HttpRouting;

/**
 * {@link java.util.ServiceLoader} provider implementation for config observe provider.
 */
public class ConfigObserveProvider implements ObserveProvider {
    @Override
    public String configKey() {
        return "config";
    }

    @Override
    public String defaultEndpoint() {
        return "config";
    }

    @Override
    public void register(Config config, String componentPath, HttpRouting.Builder routing) {
        routing.register(componentPath, ConfigService.create(config));
    }
}
