/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.yaml.mp;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Enumeration;

import io.helidon.config.mp.MpConfigSources;

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

    @Test
    void testConfigViaClassPath() throws IOException {
        ConfigSource source = YamlMpConfigSource.create(getResourceUrlPath("application.yaml"));
        validateConfig(source);
    }

    @Test
    void testConfigViaPath() throws IOException {
        ConfigSource source = YamlMpConfigSource.create(Paths.get("src/test/resources/application.yaml"));
        validateConfig(source);
    }

    @Test
    void testYamlMetaConfigProvider() {
        typeChecks("yaml", """
            another1:
                key: "another1.value"
            another2:
                key: "another2.value"
            """);
    }

    private void typeChecks(String type, String content) {
        org.eclipse.microprofile.config.spi.ConfigSource source =
                MpConfigSources.create(type, new StringReader(content));
        assertThat(source.getValue("another1.key"), is("another1.value"));
        assertThat(source.getValue("another2.key"), is("another2.value"));
    }
    private void validateConfig(ConfigSource source) {
        assertThat(source.getValue("yaml.string"), is("String"));
        assertThat(source.getValue("yaml.number"), is("10"));
        assertThat(source.getValue("yaml.array.0"), is("Array 1"));
        assertThat(source.getValue("yaml.array.1"), is("Array 2"));
        assertThat(source.getValue("yaml.array.2"), is("Array 3"));
        assertThat(source.getValue("yaml.boolean"), is("true"));
    }

    private static URL getResourceUrlPath(String resource) throws IOException {
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resource);
        while (resources.hasMoreElements()) {
            return resources.nextElement();
        }
        return null;
    }
}
