/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Resource}.
 */
class ResourceTest {
    private static final String COPYRIGHT_TEXT = "Copyright (c) 2017,2018 Oracle and/or its affiliates. All rights reserved.\n";
    // intentionally UTF-8 string
    private static final String STRING_CONTENT = "abcdefgčřžúů";
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create();
    }

    @Test
    void testString() throws IOException {
        Resource r = Resource.fromContent("unitTest", STRING_CONTENT);

        assertThat(r.getString(), is(STRING_CONTENT));
        assertThat(r.getString(StandardCharsets.UTF_8), is(STRING_CONTENT));

        String other = new String(r.getBytes(), StandardCharsets.UTF_8);
        assertThat(other, is(STRING_CONTENT));

        assertThat(r.getLocation(), is("unitTest"));
        assertThat(r.getSourceType(), is(Resource.Source.CONTENT));

        InputStream is = r.getStream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));
    }

    @Test
    void testStreamOnlyOnce() throws IOException {
        Resource r = Resource.from(new ByteArrayInputStream(STRING_CONTENT.getBytes(StandardCharsets.UTF_8)), "unit-test");

        InputStream is = r.getStream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));

        Assertions.assertThrows(IllegalStateException.class, r::getString);
    }

    @Test
    void testStreamCached() throws IOException {
        Resource r = Resource.from(new ByteArrayInputStream(STRING_CONTENT.getBytes(StandardCharsets.UTF_8)), "unit-test");

        //cache it
        assertThat(r.getString(), is(STRING_CONTENT));

        //get stream
        InputStream is = r.getStream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));

        assertThat(r.getString(), is(STRING_CONTENT));
    }

    @Test
    void testConfigPath() {
        Resource resource = Resource.from(config.get("test-1"), "resource").get();
        assertThat(resource.getString(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigClasPath() {
        Resource resource = Resource.from(config.get("test-2"), "resource").get();
        assertThat(resource.getString(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigUrl() {
        Resource resource = Resource.from(config.get("test-3"), "resource").get();
        assertThat(resource.getString(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigPlainContent() {
        Resource resource = Resource.from(config.get("test-4"), "resource").get();
        assertThat(resource.getString(), is("content"));
    }

    @Test
    void testConfigContent() {
        Resource resource = Resource.from(config.get("test-5"), "resource").get();
        assertThat(resource.getString(), is(STRING_CONTENT));
    }
}
