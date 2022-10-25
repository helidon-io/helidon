/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.openapi.internal.OpenAPIConfigImpl;

import io.smallrye.openapi.api.OpenApiConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

class OpenAPIConfigTest {

    private final static String TEST_CONFIG_DIR = "src/test/resources";

    private static final List<String> SCHEMA_OVERRIDE_CONTENTS = List.of(
            "\"name\": \"EpochMillis\"",
            "\"type\": \"number\",",
            "\"format\": \"int64\",",
            "\"description\": \"Milliseconds since January 1, 1970, 00:00:00 GMT\"");

    private static final Map<String, String> SCHEMA_OVERRIDE_VALUES = Map.of(
            "name", "EpochMillis",
            "type", "number",
            "format", "int64",
            "description", "Milliseconds since January 1, 1970, 00:00:00 GMT");

    private static final String SCHEMA_OVERRIDE_JSON = prepareSchemaOverrideJSON();

    private static final String SCHEMA_OVERRIDE_CONFIG_FQCN = "java.util.Date";

    private static final Map<String, String> SCHEMA_OVERRIDE_CONFIG = Map.of(
                 "openapi."
                    + OpenAPIConfigImpl.Builder.SCHEMA
                    + "."
                    + SCHEMA_OVERRIDE_CONFIG_FQCN,
            SCHEMA_OVERRIDE_JSON);

    private static String prepareSchemaOverrideJSON() {
        StringJoiner sj = new StringJoiner(",\n", "{\n", "\n}");
        SCHEMA_OVERRIDE_VALUES.forEach((key, value) -> sj.add("\"" + key + "\": \"" + value + "\""));
        return sj.toString();
    }

    @Test
    void simpleConfigTest() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(ConfigSources.file(Paths.get(TEST_CONFIG_DIR, "simple.properties").toString()))
                .build();
        OpenApiConfig openAPIConfig = OpenAPIConfigImpl.builder()
                .config(config.get("openapi"))
                .build();

        assertThat("reader mismatch", openAPIConfig.modelReader(), is("io.helidon.openapi.test.MyModelReader"));
        assertThat("filter mismatch", openAPIConfig.filter(), is("io.helidon.openapi.test.MySimpleFilter"));
        assertThat("servers mismatch", openAPIConfig.servers(), containsInAnyOrder("s1","s2"));
        assertThat("path1 servers mismatch", openAPIConfig.pathServers("path1"), containsInAnyOrder("p1s1","p1s2"));
        assertThat("path2 servers mismatch", openAPIConfig.pathServers("path2"), containsInAnyOrder("p2s1","p2s2"));
    }

    @Test
    void checkUnconfiguredValues() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(ConfigSources.file(Paths.get(TEST_CONFIG_DIR, "simple.properties").toString()))
                .build();
        OpenApiConfig openAPIConfig = OpenAPIConfigImpl.builder()
                .config(config.get("openapi"))
                .build();

        assertThat("scan disable mismatch", openAPIConfig.scanDisable(), is(true));
    }

    @Test
    void checkSchemaConfig() {
        Config config = Config.just(ConfigSources.file(Paths.get(TEST_CONFIG_DIR, "simple.properties").toString()),
                                    ConfigSources.create(SCHEMA_OVERRIDE_CONFIG));
        OpenApiConfig openAPIConfig = OpenAPIConfigImpl.builder()
                .config(config.get("openapi"))
                .build();

        assertThat("Schema override", openAPIConfig.getSchemas(), hasKey(SCHEMA_OVERRIDE_CONFIG_FQCN));
        assertThat("Schema override value for " + SCHEMA_OVERRIDE_CONFIG_FQCN,
                   openAPIConfig.getSchemas().get(SCHEMA_OVERRIDE_CONFIG_FQCN),
                   is(SCHEMA_OVERRIDE_JSON));
    }
}
