/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.util.Map;
import java.util.StringJoiner;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.openapi.TestUtil.config;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link io.smallrye.openapi.api.OpenApiConfig}.
 */
class OpenApiConfigTest {

    private static final Map<String, String> SCHEMA_OVERRIDE_VALUES = Map.of(
            "name", "EpochMillis",
            "type", "number",
            "format", "int64",
            "description", "Milliseconds since January 1, 1970, 00:00:00 GMT");

    private static final String SCHEMA_OVERRIDE_JSON = prepareSchemaOverrideJSON();

    private static final String SCHEMA_OVERRIDE_CONFIG_FQCN = "java.util.Date";

    private static final Map<String, String> SIMPLE_CONFIG = Map.of(
            "mp.openapi.model.reader", "io.helidon.microprofile.openapi.test.MyModelReader",
            "mp.openapi.filter", "io.helidon.microprofile.openapi.test.MySimpleFilter",
            "mp.openapi.servers", "s1,s2",
            "mp.openapi.servers.path.path1", "p1s1,p1s2",
            "mp.openapi.servers.path.path2", "p2s1,p2s2",
            "mp.openapi.servers.operation.op1", "o1s1,o1s2",
            "mp.openapi.servers.operation.op2", "o2s1,o2s2",
            "mp.openapi.scan.disable", "true"
    );

    private static final Map<String, String> SCHEMA_OVERRIDE_CONFIG = Map.of(
            "mp.openapi.schema." + SCHEMA_OVERRIDE_CONFIG_FQCN, SCHEMA_OVERRIDE_JSON
    );

    private static String prepareSchemaOverrideJSON() {
        StringJoiner sj = new StringJoiner(",\n", "{\n", "\n}");
        SCHEMA_OVERRIDE_VALUES.forEach((key, value) -> sj.add("\"" + key + "\": \"" + value + "\""));
        return sj.toString();
    }

    @Test
    public void simpleConfigTest() {
        OpenApiConfig openApiConfig = openApiConfig(SIMPLE_CONFIG);

        assertThat(openApiConfig.modelReader(), is("io.helidon.microprofile.openapi.test.MyModelReader"));
        assertThat(openApiConfig.filter(), is("io.helidon.microprofile.openapi.test.MySimpleFilter"));
        assertThat(openApiConfig.scanDisable(), is(true));
        assertThat(openApiConfig.servers(), containsInAnyOrder("s1", "s2"));
        assertThat(openApiConfig.pathServers("path1"), containsInAnyOrder("p1s1", "p1s2"));
        assertThat(openApiConfig.pathServers("path2"), containsInAnyOrder("p2s1", "p2s2"));
    }

    @Test
    void checkSchemaConfig() {
        OpenApiConfig openApiConfig = openApiConfig(SIMPLE_CONFIG, SCHEMA_OVERRIDE_CONFIG);
        Map<String, String> schemas = openApiConfig.getSchemas();

        assertThat(schemas, hasKey(SCHEMA_OVERRIDE_CONFIG_FQCN));
        assertThat(schemas.get(SCHEMA_OVERRIDE_CONFIG_FQCN), is(SCHEMA_OVERRIDE_JSON));
    }

    @SafeVarargs
    private static OpenApiConfig openApiConfig(Map<String, String>... configSources) {
        return new OpenApiConfigImpl(config(configSources));
    }
}
