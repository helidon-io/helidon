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
package io.helidon.docs.se.config;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.FileSystemWatcher;
import io.helidon.config.PollingStrategies;

@SuppressWarnings("ALL")
class MutabilitySupportSnippets {

    void snippet_1(Config myConfig) {
        // tag::snippet_1[]
        Instant loadTime = myConfig.timestamp();
        // end::snippet_1[]
    }

    // tag::snippet_2[]
    void snippet_2() {
        Config config = Config.create(
                ConfigSources.file("conf/dev.properties")
                        .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(2))) // <1>
                        .optional(),
                ConfigSources.file("conf/config.properties")
                        .changeWatcher(FileSystemWatcher.create()) // <2>
                        .optional(),
                ConfigSources.file("my.properties")
                        .pollingStrategy(PollingStrategies::nop)); // <3>
        // end::snippet_2[]
    }

    // tag::snippet_3[]
    void snippet_3(Config config) {
        config.get("greeting") // <1>
                .onChange((changedNode) -> { // <2>
                    System.out.println("Node " + changedNode.key() + " has changed!");
                });
        // end::snippet_3[]
    }

    void snippet_4(Config config) {
        // tag::snippet_4[]
        // Construct a Config with the appropriate PollingStrategy on each config source.

        Supplier<String> greetingSupplier = config.get("greeting") // <1>
                .asString().supplier(); // <2>

        System.out.println("Always actual greeting value: " + greetingSupplier.get()); // <3>
        // end::snippet_4[]
    }

}
