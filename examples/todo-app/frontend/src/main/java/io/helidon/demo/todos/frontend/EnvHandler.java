/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.demo.todos.frontend;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

/**
 * Handles response to current environment name.
 */
public final class EnvHandler implements Service {

    /**
     * The environment name.
     */
    private volatile String env;

    /**
     * Create a new {@code EnvHandler} instance.
     * @param config the configuration root
     */
    public EnvHandler(final Config config) {
        Config envConfig = config.get("env");
        this.env = envConfig.asString().orElse("unknown");

        envConfig.onChange(config1 -> {
            EnvHandler.this.env = config1.asString().orElse("unknown");
        });
    }

    @Override
    public void update(final Routing.Rules rules) {
        rules.get((req, res) -> res.send(env));
    }
}
