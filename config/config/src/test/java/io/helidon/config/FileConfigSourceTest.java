/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.test.infra.TemporaryFolderExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.typeCompatibleWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link io.helidon.config.FileConfigSource}.
 */
public class FileConfigSourceTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";
    private static final String RELATIVE_PATH_TO_RESOURCE = "/src/test/resources/";

    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();
    
    
    @Test
    public void testDescriptionMandatory() {
        ConfigSource configSource = ConfigSources.file("application.conf").build();

        assertThat(configSource.description(), is("FileConfig[application.conf]"));
    }

    @Test
    public void testDescriptionOptional() {
        ConfigSource configSource = ConfigSources.file("application.conf").optional().build();

        assertThat(configSource.description(), is("FileConfig[application.conf]?"));
    }

    @Test
    public void testGetMediaTypeSet() {
        FileConfigSource configSource = ConfigSources.file("application.conf")
                .optional()
                .mediaType(TEST_MEDIA_TYPE)
                .build();

        assertThat(configSource.mediaType(), is(Optional.of(TEST_MEDIA_TYPE)));
    }

    @Test
    public void testGetMediaTypeUnknown() {
        FileConfigSource configSource = ConfigSources.file("application.unknown")
                .optional()
                .build();

        assertThat(configSource.mediaType(), is(Optional.empty()));
    }

    @Test
    public void testLoadNotExists() {
        FileConfigSource configSource = ConfigSources.file("application.unknown")
                .build();

        assertThat(configSource.load(), is(Optional.empty()));
    }

    @Test
    public void testLoadExists() throws IOException {
        Path path = Paths.get(getDir() + "io/helidon/config/application.conf");
        FileConfigSource configSource = ConfigSources.file(path)
                .mediaType("application/hocon")
                .build();

        assertThat(configSource.mediaType(), is(Optional.of("application/hocon")));
        assertThat(configSource.target(), is(path));
        assertThat(configSource.targetType(), is(typeCompatibleWith(Path.class)));
        assertThat(configSource.exists(), is(true));

        Optional<ConfigParser.Content> maybeContent = configSource.load();
        assertThat(maybeContent, not(Optional.empty()));
        ConfigParser.Content content = maybeContent.get();

        try {
            InputStream data = content.data();
            char first = (char) new InputStreamReader(data).read();
            assertThat(first, is('#'));
        } catch (IOException e) {
            fail("Cannot read from source's reader");
        } finally {
            content.data().close();
        }

        Optional<Object> maybeStamp = content.stamp();
        assertThat(maybeStamp, not(Optional.empty()));
        Object stamp = maybeStamp.get();
        assertThat(configSource.isModified((byte[]) stamp), is(false));
    }

    @Test
    public void testBuilder() {
        ConfigSource configSource = ConfigSources.file("application.conf").build();

        assertThat(configSource, notNullValue());
    }

    @Test
    public void testBuilderWithMediaType() {
        ConfigSource configSource = ConfigSources.file("application.conf")
                .mediaType("application/hocon")
                .build();

        assertThat(configSource, notNullValue());
    }

    private static String getDir() {
        return Paths.get("").toAbsolutePath() + RELATIVE_PATH_TO_RESOURCE;
    }
}
