/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.yaml;

import java.io.StringReader;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class YamlMpConfigSourceTest {
    private static final String TEST_1 = "server:\n"
            + "  host: \"localhost\"\n"
            + "  port: 8080\n";

    private static final String TEST_2 = "providers:\n"
            + "  - abac:\n"
            + "      enabled: true\n"
            + "names: [\"first\", \"second\", \"third\"]";
    @Test
    void testObjectNode() {
        ConfigSource source = YamlMpConfigSource.create("testObjectNode", new StringReader(TEST_1));
        assertThat(source.getValue("server.host"), is("localhost"));
        assertThat(source.getValue("server.port"), is("8080"));
    }

    @Test
    void testListNode() {
        ConfigSource source = YamlMpConfigSource.create("testObjectNode", new StringReader(TEST_2));
        assertThat(source.getValue("providers.0.abac.enabled"), is("true"));
        assertThat(source.getValue("names.0"), is("first"));
        assertThat(source.getValue("names.1"), is("second"));
        assertThat(source.getValue("names.2"), is("third"));
    }
}
