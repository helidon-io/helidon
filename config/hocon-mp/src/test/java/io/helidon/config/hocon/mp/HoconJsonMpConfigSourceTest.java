/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.config.hocon.mp;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Enumeration;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class HoconJsonMpConfigSourceTest {
    private static final String TEST_HOCON_1 =
            "server {\n" +
            "  host = \"localhost\"\n" +
            "  port = 8080\n" +
            "}";
    private static final String TEST_HOCON_2 =
            "providers = [\n" +
            "  {abac = {enabled = true}}\n" +
            "]\n" +
            "names = [\n" +
            "  first\n" +
            "  second\n" +
            "  third\n" +
            "]";
    private static final String TEST_JSON_1 =
            "{\n" +
            "  \"server\": {\"host\": \"remotehost\", \"port\": 9090}\n" +
            "}";
    private static final String TEST_JSON_2 =
            "{\n" +
            "  \"providers\": [{\"abac\": {\"enabled\": false}}],\n" +
            "  \"names\": [\n" +
            "    \"one\",\n" +
            "    \"two\",\n" +
            "    \"three\"]\n" +
            "}";

    @Test
    void testHoconObjectNode() throws IOException {
        ConfigSource source = HoconMpConfigSource.create("testObjectNode", new StringReader(TEST_HOCON_1));
        assertThat(source.getValue("server.host"), is("localhost"));
        assertThat(source.getValue("server.port"), is("8080"));
    }

    @Test
    void testHoconListNode() {
        ConfigSource source = HoconMpConfigSource.create("testObjectNode", new StringReader(TEST_HOCON_2));
        assertThat(source.getValue("providers.0.abac.enabled"), is("true"));
        assertThat(source.getValue("names.0"), is("first"));
        assertThat(source.getValue("names.1"), is("second"));
        assertThat(source.getValue("names.2"), is("third"));
    }

    @Test
    void testJsonObjectNode() {
        ConfigSource source = HoconMpConfigSource.create("testObjectNode", new StringReader(TEST_JSON_1));
        assertThat(source.getValue("server.host"), is("remotehost"));
        assertThat(source.getValue("server.port"), is("9090"));
    }

    @Test
    void testJsonListNode() {
        ConfigSource source = HoconMpConfigSource.create("testObjectNode", new StringReader(TEST_JSON_2));
        assertThat(source.getValue("providers.0.abac.enabled"), is("false"));
        assertThat(source.getValue("names.0"), is("one"));
        assertThat(source.getValue("names.1"), is("two"));
        assertThat(source.getValue("names.2"), is("three"));
    }

    @Test
    void testHoconConfig() throws IOException {
        ConfigSource source = HoconMpConfigSource.create(getResourceUrlPath("application.conf"));
        // Test Main Config
        assertThat(source.getValue("hocon.string"), is("String"));
        assertThat(source.getValue("hocon.number"), is("10"));
        assertThat(source.getValue("hocon.array.0"), is("Array 1"));
        assertThat(source.getValue("hocon.array.1"), is("Array 2"));
        assertThat(source.getValue("hocon.array.2"), is("Array 3"));
        assertThat(source.getValue("hocon.boolean"), is("true"));
        // Test Include
        assertThat(source.getValue("hocon_include.string"), is("Include String"));
        assertThat(source.getValue("hocon_include.number"), is("20"));
        assertThat(source.getValue("hocon_include.array.0"), is("Include Array 1"));
        assertThat(source.getValue("hocon_include.array.1"), is("Include Array 2"));
        assertThat(source.getValue("hocon_include.array.2"), is("Include Array 3"));
        assertThat(source.getValue("hocon_include.boolean"), is("false"));
    }

    @Test
    void testJsonConfig() throws IOException {
        ConfigSource source = HoconMpConfigSource.create(getResourceUrlPath("application.json"));
        assertThat(source.getValue("json.string"), is("String"));
        assertThat(source.getValue("json.number"), is("10"));
        assertThat(source.getValue("json.array.0"), is("Array 1"));
        assertThat(source.getValue("json.array.1"), is("Array 2"));
        assertThat(source.getValue("json.array.2"), is("Array 3"));
        assertThat(source.getValue("json.boolean"), is("true"));
    }

    private static URL getResourceUrlPath(String resource) throws IOException {
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resource);
        while (resources.hasMoreElements()) {
            return resources.nextElement();
        }
        return null;
    }

}
