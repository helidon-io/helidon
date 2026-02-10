/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j;

import java.net.URI;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_YAML;
import static io.helidon.integrations.langchain4j.HelidonConstants.ConfigCategory.MODEL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class ModelConfigTest {
    @Test
    void testOverrides() {
        //language=YAML
        var cfg = """
                langchain4j:
                  providers:
                    foo-bar-provider:
                      url: http://localhost:8080
                      array-prop-1:
                        - test1
                        - test2
                        - test3
                      array-prop-2:
                        - test1
                        - test2
                        - test3
                
                  models:
                    foo-bar-model:
                      provider: foo-bar-provider
                      model: chatemini-3a
                      array-prop-2:
                        - test4
                        - test5
                        - test6
                """;
        var c = HelidonConstants.create(Config.create(ConfigSources.create(cfg, APPLICATION_YAML)),
                                   MODEL,
                                   "foo-bar-model");
        String model = c.get("model").asString().orElseThrow();
        assertThat(model, is("chatemini-3a"));
        URI url = c.get("url").as(URI.class).orElseThrow();
        assertThat(url, is(URI.create("http://localhost:8080")));
        assertThat(c.get("array-prop-1").asList(String.class).orElseThrow(), contains("test1", "test2", "test3"));
        assertThat(c.get("array-prop-2").asList(String.class).orElseThrow(), contains("test4", "test5", "test6"));
    }
    @Test
    void testModelListing() {
        //language=YAML
        var cfg = """
                langchain4j:
                  models:
                    foo-bar-model:
                      provider: foo-bar-provider
                      model: chatemini-3a
                      array-prop-2:
                        - test4
                        - test5
                        - test6
                    bar-foo-model:
                      provider: foo-bar-provider
                """;
        var c = Config.create(ConfigSources.create(cfg, APPLICATION_YAML));
        assertThat(HelidonConstants.modelNames(c, MODEL, "foo-bar-provider"),
                   containsInAnyOrder("foo-bar-model", "bar-foo-model"));
    }
}
