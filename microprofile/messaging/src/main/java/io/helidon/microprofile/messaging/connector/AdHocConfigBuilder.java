/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.messaging.connector;

import java.util.Properties;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.config.MpConfig;

/**
 *
 */
class AdHocConfigBuilder {
    private Config config;
    private Properties properties = new Properties();

    private AdHocConfigBuilder(Config config) {
        this.config = config.detach();
    }

    static AdHocConfigBuilder from(Config config) {
        return new AdHocConfigBuilder(config);
    }

    AdHocConfigBuilder put(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    AdHocConfigBuilder putAll(Config configToPut) {
        properties.putAll(configToPut.detach().asMap().orElse(CollectionsHelper.mapOf()));
        return this;
    }

    org.eclipse.microprofile.config.Config build() {
        Config newConfig = Config.builder(ConfigSources.create(properties), ConfigSources.create(config))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        return MpConfig.builder()
                .config(newConfig)
                .build();
    }
}
