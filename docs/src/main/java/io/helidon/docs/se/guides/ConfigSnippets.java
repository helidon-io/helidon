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

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.directory;
import static io.helidon.config.ConfigSources.file;

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
                            classpath("config.properties"), // <2>
                            classpath("application.yaml")) // <3>
                    .build();
        }
        // end::snippet_2[]
    }

    Config snippet_3() {
        // tag::snippet_3[]
        return Config.builder()
                .disableEnvironmentVariablesSource()
                .sources(
                        classpath("application.yaml"), // <1>
                        classpath("config.properties"))
                .build();
        // end::snippet_3[]
    }

    Config snippet_4() {
        // tag::snippet_4[]
        return Config.builder()
                .sources(
                        file("config-file.properties"), // <1>
                        classpath("application.yaml"))
                .build();
        // end::snippet_4[]
    }

    Config snippet_5() {
        // tag::snippet_5[]
        return Config.builder()
                .sources(
                        file("missing-file"), // <1>
                        classpath("application.yaml"))
                .build();
        // end::snippet_5[]
    }

    Config snippet_6() {
        return Config.builder()
                .sources(
                        // tag::snippet_6[]
                        file("missing-file").optional(), // <1>
                        // end::snippet_6[]
                        classpath("application.yaml"))
                .build();
    }

    Config snippet_7() {
        // tag::snippet_7[]
        return Config.builder()
                .sources(
                        directory("conf"), // <1>
                        classpath("config.properties").optional(),
                        classpath("application.yaml"))
                .build();
        // end::snippet_7[]
    }

    Config snippet_8() {
        // tag::snippet_8[]
        return Config.builder()
                .addSource(directory("conf"))  // <1>
                .addSource(file("config-file.properties"))
                .addSource(classpath("config.properties").optional())
                .addSource(classpath("application.yaml"))
                .build();
        // end::snippet_8[]
    }

    Config snippet_9() {
        // tag::snippet_9[]
        return Config.create(); // <1>
        // end::snippet_9[]
    }

    void snippet_10(AtomicReference<String> greeting) {
        // tag::snippet_10[]
        greeting.set(Config.global().get("app.greeting").asString().orElse("Ciao")); // <1>
        // end::snippet_10[]
    }

    void snippet_11(AtomicReference<String> greeting) {
        // tag::snippet_11[]
        greeting.set(Config.global().get("app").get("greeting").asString().orElse("Ciao")); // <1>
        // end::snippet_11[]
    }

    void snippet_12(AtomicReference<String> greeting) {
        // tag::snippet_12[]
        List<Config> appGreetings = Config.global()
                .get("app")
                .traverse()  // <1>
                .filter(node -> node.name().equals("greeting")) // <2>
                .toList(); // <3>

        greeting.set(appGreetings.get(0).asString().get());
        // end::snippet_12[]
    }

    void snippet_13(AtomicReference<String> greeting) {
        // tag::snippet_13[]
        Config greetingConfig = Config.global().get("app.greeting"); // <1>
        greeting.set(greetingConfig.asString().orElse("Ciao"));
        greetingConfig.onChange(cfg -> greeting.set(cfg.asString().orElse("Ciao"))); // <2>
        // end::snippet_13[]
    }

    Config snippet_14() {
        // tag::snippet_14[]
        return Config.builder()
                .sources(
                        file("/etc/config/config-file.properties").optional(), // <1>
                        classpath("application.yaml")) // <2>
                .build();
        // end::snippet_14[]
    }

    void snippet_15(AtomicReference<String> greeting) {
        // tag::snippet_15[]
        greeting.set(Config.global().get("app.greeting").asString().orElse("Ciao"));
        // end::snippet_15[]
    }

}
