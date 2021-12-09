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
package io.helidon.config.objectmapping;

import io.helidon.config.Config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * A testing configurable.
 */
public class Configurables {
    public static final class WithCreateConfig {
        static final String CONFIG_KEY = "WithCreateConfig";
        private String message;

        WithCreateConfig(String message) {
            this.message = message;
        }

        public static WithCreateConfig create(Config config) {
            assertThat(config, notNullValue());
            assertThat("Provided config should be located on a known value", config.name(), is(CONFIG_KEY));

            return new WithCreateConfig(config.asString().orElse(null));
        }

        String message() {
            return message;
        }
    }
}
