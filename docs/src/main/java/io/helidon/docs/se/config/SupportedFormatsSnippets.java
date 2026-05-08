/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
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
}
