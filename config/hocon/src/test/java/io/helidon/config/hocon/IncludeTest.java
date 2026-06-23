/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.hocon;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.ClasspathConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.FileConfigSource;
import io.helidon.config.spi.ConfigParserException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncludeTest {

    private static final String RELATIVE_PATH_TO_RESOURCE = "./src/test/resources/";

    @Test
    void testClasspathIncludes() {
        Config config = Config.just(ClasspathConfigSource.create("conf/application.conf"));

        String value = config.get("app.greeting").asString().orElse(null);

        assertThat("app.greeting should be loaded from application.conf", value, notNullValue());
        assertThat(value, is("Hello"));

        value = config.get("server.host").asString().orElse(null);

        assertThat("server.host should be loaded from included.conf", value, notNullValue());
        assertThat(value, is("localhost"));
    }

    @Test
    void testFilesIncludes() {
        Config config = Config.just(FileConfigSource.builder()
                                              .path(Paths.get(RELATIVE_PATH_TO_RESOURCE + "conf/application2.conf")));

        String value = config.get("app.greeting").asString().orElse(null);

        assertThat("app.greeting should be loaded from application2.conf", value, notNullValue());
        assertThat(value, is("Hello"));

        value = config.get("server.host").asString().orElse(null);

        assertThat("server.host should be loaded from sub/included.conf", value, notNullValue());
        assertThat(value, is("127.0.0.1"));
    }

    @Test
    void testSelfReferenceFromIncludedValue() {
        Config config = Config.just(ClasspathConfigSource.create("conf/self-reference-override.conf"));

        List<String> value = config.get("items")
                .asList(String.class)
                .orElse(List.of());

        assertThat(value, is(List.of("base-alpha",
                                     "base-beta",
                                     "override-alpha",
                                     "override-beta",
                                     "override-gamma")));
    }

    @Test
    void testBasicAuthEnabledServicesSelfReferenceFromIncludedProd() {
        Config config = Config.just(ClasspathConfigSource.create("conf/basic-auth-services.conf"));

        List<String> value = config.get("basicAuthEnabledServices")
                .asList(String.class)
                .orElse(List.of());

        assertThat(value, is(List.of("prod-alpha",
                                     "prod-beta",
                                     "canary-unstable-alpha",
                                     "canary-unstable-beta",
                                     "canary-unstable-gamma")));
    }

    @Test
    void testBasicAuthEnabledServicesSelfReferenceWithoutIncludedValueFails() {
        String hocon = ""
                + "basicAuthEnabledServices: ${basicAuthEnabledServices} [\n"
                + "  \"canary-unstable-alpha\",\n"
                + "  \"canary-unstable-beta\",\n"
                + "  \"canary-unstable-gamma\"\n"
                + "]\n";

        Config config = Config.just(ConfigSources.create(hocon, MediaTypes.APPLICATION_HOCON));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                                                () -> config.get("basicAuthEnabledServices")
                                                        .asList(String.class)
                                                        .get());

        assertThat(ex.getMessage(), is("Recursive update"));
    }

    @Test
    void testSelfReferenceKeepsNestedReferencesDeferred() {
        Config config = Config.builder(ConfigSources.create(Map.of("itemName", "external")),
                                       ClasspathConfigSource.create("conf/self-reference-deferred-override.conf"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        List<String> value = config.get("items")
                .asList(String.class)
                .orElse(List.of());

        assertThat(value, is(List.of("external", "override")));
    }

    @Test
    void testIncludesWithRequiredIncludeNotPresent() {
        ConfigParserException cpe = assertThrows(ConfigParserException.class, () ->
                Config.just(FileConfigSource.builder()
                                      .path(Paths.get(RELATIVE_PATH_TO_RESOURCE + "conf/application3.conf"))));
        assertThat(cpe.getMessage(), is("bogus.conf is missing"));
    }

    @Test
    void testClasspathIncludesNoExtension() {
        Config config = Config.just(ClasspathConfigSource.create("conf/application4.conf"));

        String value = config.get("app.greeting").asString().orElse(null);

        assertThat("app.greeting should be loaded from application.conf", value, notNullValue());
        assertThat(value, is("Hello"));

        value = config.get("server.host").asString().orElse(null);

        assertThat("server.host should be loaded from included.conf", value, notNullValue());
        assertThat(value, is("localhost"));

        value = config.get("server.port").asString().orElse(null);

        assertThat("server.port should be loaded from sub/included.conf", value, notNullValue());
        assertThat(value, is("8080"));
    }

    @Test
    void testIncludedReferenceOverridesBaseValue() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.create(Map.of("T2_PROJECT", "ed_dedicated_intc",
                                                       "T2_FLEET", "ed-intc-omta")))
                .addSource(ClasspathConfigSource.create("conf/merge-reference-override.conf"))
                .build();

        assertThat(config.get("metrics.publishers.oci.enabled").asString().orElse(null), is("true"));
        assertThat(config.get("metrics.publishers.oci.gauge-sample-interval").asString().orElse(null), is("PT3S"));
        assertThat(config.get("metrics.publishers.oci.project").asString().orElse(null), is("ed_dedicated_intc"));
        assertThat(config.get("metrics.publishers.oci.fleet").asString().orElse(null), is("ed-intc-omta"));
        assertThat(config.get("metrics.publishers.oci.default-dimensions.project").asString().orElse(null),
                   is("ed_dedicated_intc"));
        assertThat(config.get("metrics.publishers.oci.default-dimensions.fleet").asString().orElse(null),
                   is("ed-intc-omta"));
    }
}
