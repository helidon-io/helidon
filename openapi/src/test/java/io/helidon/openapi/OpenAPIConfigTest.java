/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.nio.file.Paths;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.openapi.internal.OpenAPIConfigImpl;

import io.smallrye.openapi.api.OpenApiConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 *
 */
public class OpenAPIConfigTest {

    private final static String TEST_CONFIG_DIR = "src/test/resources";

    public OpenAPIConfigTest() {
    }

    @Test
    public void simpleConfigTest() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(ConfigSources.file(Paths.get(TEST_CONFIG_DIR, "simple.properties").toString()))
                .build();
        OpenApiConfig openAPIConfig = OpenAPIConfigImpl.builder()
                .config(config.get(OpenAPISupport.Builder.CONFIG_KEY))
                .build();

        assertThat("reader mismatch", openAPIConfig.modelReader(), is("io.helidon.openapi.test.MyModelReader"));
        assertThat("filter mismatch", openAPIConfig.filter(), is("io.helidon.openapi.test.MySimpleFilter"));
        assertThat("servers mismatch", openAPIConfig.servers(), containsInAnyOrder("s1","s2"));
        assertThat("path1 servers mismatch", openAPIConfig.pathServers("path1"), containsInAnyOrder("p1s1","p1s2"));
        assertThat("path2 servers mismatch", openAPIConfig.pathServers("path2"), containsInAnyOrder("p2s1","p2s2"));
    }

    @Test
    public void checkUnconfiguredValues() {
        Config config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .sources(ConfigSources.file(Paths.get(TEST_CONFIG_DIR, "simple.properties").toString()))
                .build();
        OpenApiConfig openAPIConfig = OpenAPIConfigImpl.builder()
                .config(config.get(OpenAPISupport.Builder.CONFIG_KEY))
                .build();

        assertThat("scan disable mismatch", openAPIConfig.scanDisable(), is(true));
    }
}
