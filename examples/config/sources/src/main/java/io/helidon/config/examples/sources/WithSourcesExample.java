/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.examples.sources;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * This example shows how to merge the configuration from different sources.
 *
 * @see LoadSourcesExample
 */
public class WithSourcesExample {

    private WithSourcesExample() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        /*
           Creates a config source composed of following sources:
           - conf/dev.yaml - developer specific configuration, should not be placed in VCS;
           - conf/config.yaml - deployment dependent configuration, for example prod, stage, etc;
           - default.yaml - application default values, loaded form classpath;
           with a filter which convert values with keys ending with "level" to upper case
         */

        Config config = Config
                .builder(file("conf/dev.yaml").optional(),
                         file("conf/config.yaml").optional(),
                         classpath("default.yaml"))
                .addFilter((key, stringValue) -> key.name().equals("level") ? stringValue.toUpperCase() : stringValue)
                .build();

        // Environment type, from dev.yaml:
        ConfigValue<String> env = config.get("meta.env").asString();
        env.ifPresent(e -> System.out.println("Environment: " + e));
        assert env.get().equals("DEV");

        // Default value (default.yaml): Config Sources Example
        String appName = config.get("app.name").asString().get();
        System.out.println("Name: " + appName);
        assert appName.equals("Config Sources Example");

        // Page size, from config.yaml: 10
        int pageSize = config.get("app.page-size").asInt().get();
        System.out.println("Page size: " + pageSize);
        assert pageSize == 10;

        // Applied filter (uppercase logging level), from dev.yaml: finest -> FINEST
        String level = config.get("component.audit.logging.level").asString().get();
        System.out.println("Level: " + level);
        assert level.equals("FINE");
    }

}
