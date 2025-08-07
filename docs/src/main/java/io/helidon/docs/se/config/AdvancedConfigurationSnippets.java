/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.Config.Key;
import io.helidon.config.Config.Type;
import io.helidon.config.ConfigParsers;
import io.helidon.config.ConfigSources;
import io.helidon.config.FileSystemWatcher;
import io.helidon.config.OverrideSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.RetryPolicies;
import io.helidon.config.hocon.HoconConfigParser;
import io.helidon.config.overrides.OverrideConfigFilter;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.MergingStrategy;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

@SuppressWarnings("ALL")
class AdvancedConfigurationSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        Config secrets = Config.builder(
                        ConfigSources.directory("conf/secrets")) // <1>
                .disableEnvironmentVariablesSource() // <2>
                .disableSystemPropertiesSource() // <2>
                .build();

        assert secrets.get("username") // <3>
                .asString()
                .get()
                .equals("jose");
        assert secrets.get("password") // <4>
                .asString()
                .get()
                .equals("^ery$ecretP&ssword");
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Config anotherConfig = Config.create(classpath("application.conf"));

        Config config = Config.create(
                ConfigSources.create(anotherConfig.get("data")));
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        Config config = Config.create(
                ConfigSources.create(System.getProperties()).build()); // <1>
        // end::snippet_3[]
    }

    void snippet_4() {
        // tag::snippet_4[]
        Config config = Config.create();
        ConfigSources.create("app.greeting = Hi", MediaTypes.create("text", "x-java-properties"));
        // end::snippet_4[]
    }

    void snippet_5() {
        // tag::snippet_5[]
        Config config = Config.create(
                ConfigSources.create(Map.of("app.page-size", "20"))
                        .build()); // <1>
        // end::snippet_5[]
    }

    void snippet_6() {
        // tag::snippet_6[]
        Config config = Config.create(
                ConfigSources.create(ObjectNode.builder()
                                             .addList("app.basic-range", ListNode.builder()
                                                     .addValue("-20")
                                                     .addValue("20")
                                                     .build())
                                             .build()));
        // end::snippet_6[]
    }

    void snippet_7() {
        // tag::snippet_7[]
        Config config = Config.create(
                ConfigSources.prefixed("app", // <1>
                                       classpath("app.conf")), // <2>
                ConfigSources.prefixed("data", // <3>
                                       classpath("data.conf"))); // <4>

        assert config.get("app.greeting") // <5>
                .asString()
                .get()
                .equals("Hello");

        assert config.get("data.providers.0.name") // <6>
                .asString()
                .get()
                .equals("Provider1");
        // end::snippet_7[]
    }

    void snippet_8() {
        // tag::snippet_8[]
        Config config = Config.builder()
                .addSource(file("config-file.properties"))
                .addSource(classpath("application.yaml"))
                .mergingStrategy(MergingStrategy.fallback()) // <1>
                .build();
        // end::snippet_8[]
    }

    void snippet_9() {
        // tag::snippet_9[]
        Config config = Config.create(classpath("props") // <1>
                                              .mediaType(MediaTypes.create("text/x-java-properties"))); // <2>
        // end::snippet_9[]
    }

    void snippet_10() {
        // tag::snippet_10[]
        Config config = Config.create(classpath("props") // <1>
                                              .parser(ConfigParsers.properties())); // <2>
        // end::snippet_10[]
    }

    void snippet_11() {
        // tag::snippet_11[]
        Config config = Config.create(
                classpath("application.yaml")
                        .mediaTypeMapping(key -> { // <1>
                            return "app".equals(key.toString()) // <2>
                                    ? Optional.of(MediaTypes.APPLICATION_JSON)
                                    : Optional.empty();
                        }));

        assert config.get("secrets.username").asString() // <3>
                .get().equals("jose");
        assert config.get("secrets.password").asString() // <3>
                .get().equals("^ery$ecretP&ssword");

        assert config.get("app").type() == Type.OBJECT; // <4>

        assert config.get("app.greeting") // <3>
                .asString().get().equals("Hello");
        assert config.get("app.page-size") // <3>
                       .asInt().get() == 20;
        assert config.get("app.basic-range.0") // <3>
                       .asInt().get() == -20;
        assert config.get("app.basic-range.1") // <3>
                       .asInt().get() == 20;
        // end::snippet_11[]
    }

    void snippet_12() {
        // tag::snippet_12[]
        Config config = Config.create(
                classpath("application.yaml")
                        .parserMapping(key -> { // <1>
                            return "app".equals(key.toString()) ? // <2>
                                    Optional.of(HoconConfigParser.create())
                                    : Optional.empty();
                        }));
        // end::snippet_12[]
    }

    void snippet_13() {
        // tag::snippet_13[]
        Config config = Config.create(classpath("application.json"));

        // node `oracle`
        assert config.get("oracle.com").asBoolean().get() == true; // <1>
        assert config.get("oracle").get("com").asBoolean().get() == true; // <1>
        assert config.get("oracle.com").type() == Type.VALUE; // <2>
        assert config.get("oracle.com").name().equals("com"); // <3>
        // node `oracle.com`
        assert config.get("oracle~1com.secured").asBoolean().get() == true; // <4>
        assert config.get(Key.escapeName("oracle.com")) // <5>
                       .get("secured").asBoolean().get() == true;
        assert config.get(Key.escapeName("oracle.com")).type() == Type.OBJECT; // <6>
        assert config.get(Key.escapeName("oracle.com")).name().equals("oracle.com"); // <7>
        // end::snippet_13[]
    }

    void snippet_14() {
        // tag::snippet_14[]
        Config config = Config.builder()
                .addFilter(OverrideConfigFilter.builder()  // <1>
                                   .addConfigSource(ConfigSources.file("conf/overrides.properties").build()) // <2>
                                   .build())
                .addSource(ConfigSources.classpath("application.yaml")) // <3>
                .build();
        // end::snippet_14[]
    }

    void snippet_15() {
        // tag::snippet_15[]
        Config config = Config.builder()
                .disableKeyResolving()
                .disableValueResolving()
                // other Config builder settings
                .build();
        // end::snippet_15[]
    }

    void snippet_16() {
        // tag::snippet_16[]
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2); // <1>

        Config config = Config.create(
                file("conf/dev.properties")
                        .pollingStrategy(
                                PollingStrategies.regular(Duration.ofSeconds(2)) // <2>
                                        .executor(executor)), // <3>
                file("conf/config.properties")
                        .pollingStrategy(
                                PollingStrategies.regular(Duration.ofSeconds(5)) // <2>
                                        .executor(executor))); // <4>
        // end::snippet_16[]
    }

    void snippet_17() {
        // tag::snippet_17[]
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2); // <1>

        Config config = Config.builder()
                .overrides(OverrideSources
                                   .file("conf/overrides.properties")
                                   .changeWatcher(FileSystemWatcher.builder()
                                                          .executor(executor) // <2>
                                                          .build()))
                .sources(file("conf/env.yaml")
                                 .changeWatcher(FileSystemWatcher.builder()
                                                        .executor(executor) // <3>
                                                        .build()))
                .build();
        // end::snippet_17[]
    }

    void snippet_18(ThreadFactory myThreadFactory) {
        // tag::snippet_18[]
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, myThreadFactory); // <1>

        Config config = Config.create(
                file("conf/dev.properties")
                        .optional() // <2>
                        .retryPolicy(RetryPolicies.repeat(2) // <3>
                                             .executorService(executor))); // <4>
        // end::snippet_18[]
    }

}
