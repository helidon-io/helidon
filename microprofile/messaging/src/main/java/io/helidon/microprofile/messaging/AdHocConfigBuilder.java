/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.config.MpConfig;

import java.util.Properties;

public class AdHocConfigBuilder {
    private Config config;
    private Properties properties = new Properties();

    private AdHocConfigBuilder(Config config) {
        this.config = config.detach();
    }

    public static AdHocConfigBuilder from(Config config) {
        return new AdHocConfigBuilder(config);
    }

    public AdHocConfigBuilder put(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    public AdHocConfigBuilder putAll(Config configToPut) {
        properties.putAll(configToPut.detach().asMap().orElse(CollectionsHelper.mapOf()));
        return this;
    }

    public org.eclipse.microprofile.config.Config build() {
        //TODO: There has to be some better way
//        return MpConfig.builder().config(((MpConfig) mpConfigBuilder
//                .config(Config.builder().sources(ConfigSources.create(config), ConfigSources.create(properties)).build())
//                .build()).helidonConfig().get(currentContext)).build();
//
        Config newConfig = Config.builder(ConfigSources.create(properties), ConfigSources.create(config))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        return MpConfig.builder()
                .config(newConfig)
                .build();
    }
}
