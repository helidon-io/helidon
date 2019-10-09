/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.media.type;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link MediaTypes}.
 */
class MediaTypesTest {

    @Test
    void testBuiltIn() throws MalformedURLException {
        Optional<String> expected = Optional.of("application/x-yaml");

        // file suffix
        Optional<String> yml = MediaTypes.detectExtensionType("yml");
        assertThat(yml, is(expected));

        // URI
        yml = MediaTypes.detectType(URI.create("http://localhost:8080/static/test.yml"));
        assertThat(yml, is(expected));

        // URL
        yml = MediaTypes.detectType(new URL("http://localhost:8080/static/test.yml"));
        assertThat(yml, is(expected));

        // Path object
        yml = MediaTypes.detectType(Paths.get("/home/config.yml"));
        assertThat(yml, is(expected));

        // Path string
        yml = MediaTypes.detectType("some path/forward\\back\\config.yml");
        assertThat(yml, is(expected));
    }

    @Test
    void testCustom() {
        Optional<String> hocon = MediaTypes.detectExtensionType("json");

        assertThat(hocon, is(Optional.of("application/hocon")));
    }

    @Test
    void testService() throws MalformedURLException {
        Optional<String> type = MediaTypes.detectExtensionType(CustomTypeDetector.SUFFIX);
        assertThat(type, is(Optional.of(CustomTypeDetector.MEDIA_TYPE)));

        type = MediaTypes.detectType(new URL("http", "localhost", 80, "/test/path.mine"));
        assertThat(type, is(Optional.of(CustomTypeDetector.MEDIA_TYPE_HTTP)));

        type = MediaTypes.detectType(URI.create("http://localhost/files/file.mine"));
        assertThat(type, is(Optional.of(CustomTypeDetector.MEDIA_TYPE)));
    }

    @Test
    void testServiceDockerfile() throws MalformedURLException {
        Optional<String> type = MediaTypes.detectType(new URL("http", "localhost", 80, "/test/Dockerfile.native"));
        assertThat(type, is(Optional.of(DockerfileTypeDetector.MEDIA_TYPE)));

        type = MediaTypes.detectType(URI.create("http://localhost/files/Dockerfile"));
        assertThat(type, is(Optional.of(DockerfileTypeDetector.MEDIA_TYPE)));

        type = MediaTypes.detectType("some text pointing to a file: Dockerfile");
        assertThat(type, is(Optional.of(DockerfileTypeDetector.MEDIA_TYPE)));
    }
}