/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.todos.frontend;

import io.helidon.config.Config;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

/**
 * Handles response to current environment name.
 */
public final class EnvHandler implements HttpService {

    /**
     * The environment name.
     */
    private volatile String env;

    /**
     * Create a new {@code EnvHandler} instance.
     *
     * @param config the configuration root
     */
    public EnvHandler(Config config) {
        Config envConfig = config.get("env");
        this.env = envConfig.asString().orElse("unknown");
        envConfig.onChange(node -> EnvHandler.this.env = node.asString().orElse("unknown"));
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get((req, res) -> res.send(env));
    }
}
