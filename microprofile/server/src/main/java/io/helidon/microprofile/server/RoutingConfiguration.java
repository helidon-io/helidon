/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.microprofile.server;

import javax.enterprise.inject.spi.Annotated;

import io.helidon.config.Config;

class RoutingConfiguration {
    private String routingName = null;
    private String routingPath = null;
    private boolean required = false;
    private final String contextConfigKey;

    RoutingConfiguration(Annotated annotated, String contextConfigKey) {
        RoutingName rn = annotated.getAnnotation(RoutingName.class);
        RoutingPath rp = annotated.getAnnotation(RoutingPath.class);
        this.contextConfigKey = contextConfigKey;
        if (rn != null) {
            this.routingName = rn.value();
            this.required = rn.required();
        }
        if (rp != null) {
            this.routingPath = rp.value();
        }
    }

    public String routingName(Config config) {
        return config.get(contextConfigKey)
                .get(RoutingName.CONFIG_KEY_NAME)
                .asString()
                .orElse(routingName);
    }

    public String routingPath(Config config) {
        return config.get(contextConfigKey)
                .get(RoutingPath.CONFIG_KEY_PATH)
                .asString()
                .orElse(routingPath);
    }

    public boolean required(Config config) {
        return config.get(contextConfigKey)
                .get(RoutingName.CONFIG_KEY_REQUIRED)
                .asBoolean()
                .orElse(required);
    }
}
