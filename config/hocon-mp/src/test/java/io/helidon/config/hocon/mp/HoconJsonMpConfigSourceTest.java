/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.nio.file.Paths;
import java.util.Enumeration;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HoconJsonMpConfigSourceTest {
    @Test
    void testHoconViaClasspath() throws IOException {
        ConfigSource source = HoconMpConfigSource.create(getResourceUrlPath("application.conf"));
        // Test Main Config
        validateHoconConfig(source);
    }

    @Test
    void testHoconViaPath() {
        ConfigSource source = HoconMpConfigSource.create(Paths.get("src/test/resources/application.conf")) ;
        validateHoconConfig(source);
    }

    private void validateHoconConfig(ConfigSource source) {
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
    void testJsonConfigViaClassPath() throws IOException {
        ConfigSource source = HoconMpConfigSource.create(getResourceUrlPath("application.json"));
        validateJsonConfig(source);
    }

    @Test
    void testJsonConfigViaPath() throws IOException {
        ConfigSource source = HoconMpConfigSource.create(Paths.get("src/test/resources/application.json"));
        validateJsonConfig(source);
    }

    @Test
    void testJsonMetaConfigProvider() {
        typeChecks("json", """
                {
                another1.key: "another1.value",
                another2.key: "another2.value"
                }
            """);
    }

    private void typeChecks(String type, String content) {
        org.eclipse.microprofile.config.spi.ConfigSource source =
                MpConfigSources.create(type, new StringReader(content));
        assertThat(source.getValue("another1.key"), is("another1.value"));
        assertThat(source.getValue("another2.key"), is("another2.value"));
    }
    private void validateJsonConfig(ConfigSource source) {
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
