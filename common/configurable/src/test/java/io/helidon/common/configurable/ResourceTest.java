/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Resource}.
 */
class ResourceTest {
    private static final String COPYRIGHT_TEXT = "Copyright (c) 2017,2018 Oracle and/or its affiliates.";
    // intentionally UTF-8 string
    private static final String STRING_CONTENT = "abcdefgčřžúů";
    private static Config prefixedConfig;
    private static Config config;

    @BeforeAll
    static void initClass() {
        prefixedConfig = Config.create().get("resources-prefix");
        config = Config.create().get("resources");
    }

    @Test
    void testString() throws IOException {
        Resource r = Resource.create("unitTest", STRING_CONTENT);

        assertThat(r.string(), is(STRING_CONTENT));
        assertThat(r.string(StandardCharsets.UTF_8), is(STRING_CONTENT));

        String other = new String(r.bytes(), StandardCharsets.UTF_8);
        assertThat(other, is(STRING_CONTENT));

        assertThat(r.location(), is("unitTest"));
        assertThat(r.sourceType(), is(Resource.Source.CONTENT));

        InputStream is = r.stream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));
    }

    @Test
    void testStreamOnlyOnce() throws IOException {
        Resource r = Resource.create("unit-test", new ByteArrayInputStream(STRING_CONTENT.getBytes(StandardCharsets.UTF_8)));

        InputStream is = r.stream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));

        assertThrows(IllegalStateException.class, r::string);
    }

    @Test
    void testStreamCached() throws IOException {
        Resource r = Resource.create("unit-test", new ByteArrayInputStream(STRING_CONTENT.getBytes(StandardCharsets.UTF_8)));

        //cache it
        assertThat(r.string(), is(STRING_CONTENT));

        //get stream
        InputStream is = r.stream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));

        assertThat(r.string(), is(STRING_CONTENT));
    }

    @Test
    void testConfigPath() {
        Resource resource = Resource.create(prefixedConfig.get("test-1"), "resource").get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));

        resource = config.get("test-1.resource").as(Resource::create).get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigClasPath() {
        Resource resource = Resource.create(prefixedConfig.get("test-2"), "resource").get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));

        resource = config.get("test-2.resource").as(Resource::create).get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigUrl() {
        Resource resource = Resource.create(prefixedConfig.get("test-3"), "resource").get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));

        resource = config.get("test-3.resource").as(Resource::create).get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigPlainContent() {
        Resource resource = Resource.create(prefixedConfig.get("test-4"), "resource").get();
        assertThat(resource.string(), is("content"));

        resource = config.get("test-4.resource").as(Resource::create).get();
        assertThat(resource.string(), is("content"));
    }

    @Test
    void testConfigContent() {
        Resource resource = Resource.create(prefixedConfig.get("test-5"), "resource").get();
        assertThat(resource.string(), is(STRING_CONTENT));

        resource = config.get("test-5.resource").as(Resource::create).get();
        assertThat(resource.string(), is(STRING_CONTENT));
    }

    @Test
    void testWrongConfig() {
        assertThrows(ConfigException.class, () -> config.get("test-6.resource").as(Resource::create).get());
    }
}
