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
package io.helidon.docs.se.guides;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

@SuppressWarnings("ALL")
class ConfigSnippets {

    void snippet1() {
        // tag::snippet_1[]
        Config config = Config.create(); // <1>
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        private static Config buildConfig() {
            return Config.builder()
                    .disableEnvironmentVariablesSource() // <1>
                    .sources(
                            ConfigSources.classpath("config.properties"), // <2>
                            ConfigSources.classpath("application.yaml")) // <3>
                    .build();
        }
        // end::snippet_2[]
    }

    Config snippet_3() {
        // tag::snippet_3[]
        return Config.builder()
                .disableEnvironmentVariablesSource()
                .sources(
                        ConfigSources.classpath("application.yaml"), // <1>
                        ConfigSources.classpath("config.properties"))
                .build();
        // end::snippet_3[]
    }

    Config snippet_4() {
        // tag::snippet_4[]
        return Config.builder()
                .sources(
                        ConfigSources.file("config-file.properties"), // <1>
                        ConfigSources.classpath("application.yaml"))
                .build();
        // end::snippet_4[]
    }

    Config snippet_5() {
        // tag::snippet_5[]
        return Config.builder()
                .sources(
                        ConfigSources.file("missing-file"), // <1>
                        ConfigSources.classpath("application.yaml"))
                .build();
        // end::snippet_5[]
    }

    Config snippet_6() {
        // tag::snippet_6[]
        return Config.builder()
                .sources(
                        ConfigSources.directory("conf"), // <1>
                        ConfigSources.classpath("config.properties").optional(),
                        ConfigSources.classpath("application.yaml"))
                .build();
        // end::snippet_6[]
    }

    Config snippet_7() {
        // tag::snippet_7[]
        return Config.builder()
                .addSource(ConfigSources.directory("conf"))  // <1>
                .addSource(ConfigSources.file("config-file.properties"))
                .addSource(ConfigSources.classpath("config.properties").optional())
                .addSource(ConfigSources.classpath("application.yaml"))
                .build();
        // end::snippet_7[]
    }

    Config snippet_8() {
        // tag::snippet_8[]
        return Config.create(); // <1>
        // end::snippet_8[]
    }

    void snippet_9(AtomicReference<String> greeting) {
        // tag::snippet_9[]
        greeting.set(Config.global().get("app.greeting").asString().orElse("Ciao")); // <1>
        // end::snippet_9[]
    }

    void snippet_10(AtomicReference<String> greeting) {
        // tag::snippet_10[]
        greeting.set(Config.global().get("app").get("greeting").asString().orElse("Ciao")); // <1>
        // end::snippet_10[]
    }

    void snippet_11(AtomicReference<String> greeting) {
        // tag::snippet_11[]
        List<Config> appGreetings = Config.global()
                .get("app")
                .traverse()  // <1>
                .filter(node -> node.name().equals("greeting")) // <2>
                .toList(); // <3>

        greeting.set(appGreetings.get(0).asString().get());
        // end::snippet_11[]
    }

    void snippet_12(AtomicReference<String> greeting) {
        // tag::snippet_12[]
        Config greetingConfig = Config.global().get("app.greeting"); // <1>
        greeting.set(greetingConfig.asString().orElse("Ciao"));
        greetingConfig.onChange(cfg -> greeting.set(cfg.asString().orElse("Ciao"))); // <2>
        // end::snippet_12[]
    }

    Config snippet_13() {
        // tag::snippet_13[]
        return Config.builder()
                .sources(
                        ConfigSources.file("/etc/config/config-file.properties").optional(), // <1>
                        ConfigSources.classpath("application.yaml")) // <2>
                .build();
        // end::snippet_13[]
    }

    void snippet_14(AtomicReference<String> greeting) {
        // tag::snippet_14[]
        greeting.set(Config.global().get("app.greeting").asString().orElse("Ciao"));
        // end::snippet_14[]
    }

}
