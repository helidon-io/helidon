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

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;
import io.helidon.config.etcd.EtcdConfigSource;
import io.helidon.config.etcd.EtcdConfigSourceBuilder;
import io.helidon.config.etcd.EtcdWatcher;
import io.helidon.config.git.GitConfigSource;
import io.helidon.config.hocon.HoconConfigParser;
import io.helidon.config.yaml.YamlConfigParser;

import static io.helidon.config.ConfigSources.classpath;

@SuppressWarnings("ALL")
class SupportedFormatsSnippets {

    // tag::snippet_1[]
    void snippet_1() {
        Config config = Config.create(classpath("application.yaml")); // <1>
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Config config = Config.create(classpath("my-config") // <1>
                                              .parser(YamlConfigParser.create())); // <2>
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        Config config = Config.create(classpath("my-config") // <1>
                                              .mediaType(MediaTypes.APPLICATION_X_YAML)); // <2>
        // end::snippet_3[]
    }

    void snippet_4() {
        // tag::snippet_4[]
        Config config = Config.builder(classpath("application.yaml"))
                .disableParserServices() // <1>
                .addParser(YamlConfigParser.create()) // <2>
                .build();
        // end::snippet_4[]
    }

    void snippet_5() {
        // tag::snippet_5[]
        Config config = Config.create(classpath("application.conf")); // <1>
        // end::snippet_5[]
    }

    void snippet_6() {
        // tag::snippet_6[]
        Config config = Config.create(classpath("my-config") // <1>
                                              .parser(HoconConfigParser.create())); // <2>
        // end::snippet_6[]
    }

    void snippet_7() {
        // tag::snippet_7[]
        Config config = Config.create(classpath("my-config") // <1>
                                              .mediaType(MediaTypes.APPLICATION_HOCON)); // <2>
        // end::snippet_7[]
    }

    void snippet_8() {
        // tag::snippet_8[]
        Config config = Config.builder(classpath("application.conf"))
                .disableParserServices() // <1>
                .addParser(HoconConfigParser.create()) // <2>
                .build();
        // end::snippet_8[]
    }

    void snippet_9() {
        // tag::snippet_9[]
        Config config = Config.builder(classpath("application.conf"))
                .disableParserServices()
                .addParser(HoconConfigParser.builder() // <1>
                                   .resolvingEnabled(false) // <2>
                                   .build()) // <3>
                .build();
        // end::snippet_9[]
    }

    void snippet_10() {
        // tag::snippet_10[]
        Config config = Config.create(
                EtcdConfigSource // <1>
                        .create(URI.create("http://my-etcd:2379"), // <2>
                                "/config.yaml", // <3>
                                EtcdConfigSourceBuilder.EtcdApi.v3)); // <4>
        // end::snippet_10[]
    }

    void snippet_11() {
        // tag::snippet_11[]
        Config config = Config.create(
                EtcdConfigSource
                        .builder()
                        .uri(URI.create("http://my-etcd:2379"))
                        .key("/config.yaml")
                        .api(EtcdConfigSourceBuilder.EtcdApi.v3)
                        .changeWatcher(EtcdWatcher.create())); // <1>
        // end::snippet_11[]
    }

    void snippet_12() {
        // tag::snippet_12[]
        Config config = Config.create(classpath("config-meta-etcd.yaml"));
        // end::snippet_12[]
    }

    void snippet_13() {
        // tag::snippet_13[]
        Config config = Config.create(
                GitConfigSource
                        .builder() // <1>
                        .uri(URI.create("https://github.com/okosatka/test-config.git")) // <2>
                        .directory(Paths.get("/config")) // <3>
                        .branch("dev")); // <4>
        // end::snippet_13[]
    }

    void snippet_14() {
        // tag::snippet_14[]
        Config config = Config.create(
                GitConfigSource.builder()
                        .uri(URI.create("https://github.com/okosatka/test-config.git"))
                        .pollingStrategy(PollingStrategies.regular(Duration.ofMinutes(
                                5)))); // <1>
        // end::snippet_14[]
    }

    void snippet_15() {
        // tag::snippet_15[]
        Config config = Config.create(classpath("config-meta-git.yaml"));
        // end::snippet_15[]
    }
}
