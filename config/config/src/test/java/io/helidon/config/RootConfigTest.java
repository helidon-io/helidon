/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RootConfigTest {
    private static Config config;

    @BeforeAll
    static void createConfig() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader("""
                                                 server.host=localhost
                                                 server.port=8080
                                                 server.tls.enabled=true
                                                 """));
        config = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.create(properties))
                .build();
    }

    @Test
    void testRootFromRoot() {
        Config root = config.root();
        assertThat(root.get("server.host").asString().asOptional(), optionalValue(is("localhost")));
    }

    @Test
    void testRootFromNested() {
        Config root = config.get("server").root();
        assertThat(root.get("server.host").asString().asOptional(), optionalValue(is("localhost")));
    }

    @Test
    void testRootFromDetached() {
        Config root = config.get("server").detach().root();
        assertThat(root.get("host").asString().asOptional(), optionalValue(is("localhost")));
    }
}
