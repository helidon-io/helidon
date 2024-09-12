/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.packaging.inject;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;

/**
 * We must provide a main class when using modularized jar file with main class attribute,
 * as we cannot use a main class from another module (at least not easily).
 */
public class Main {
    public static void main(String[] args) {
        // makes sure global config is initialized
        Config config = GlobalConfig.config();
        System.out.println("Configured port: " + config.get("server.port").asInt().orElse(-1));
        io.helidon.Main.main(args);
    }
}
