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

package io.helidon.config.sources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import io.helidon.config.spi.ConfigParser;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class FileConfigSourceTest {
    @Test
    void testFileExisting() throws IOException {
        String firstValue = "First value";
        String secondValue = "Second value";

        Path tempFile = Files.createTempFile("helidon-unit", ".properties");
        Files.write(tempFile, List.of("value=" + firstValue));

        FileConfigSource source = FileConfigSource.create(tempFile);

        assertThat(tempFile + " should exist on file system", source.exists(), is(true));
        assertThat("Description should not be null", source.description(), notNullValue());

        byte[] stamp = testContent(tempFile, source.load(), firstValue);

        assertThat("Stamp should be the same if not changed", source.isModified(stamp), is(false));

        Files.write(tempFile, List.of("value=" + secondValue));
        assertThat("File was changed", source.isModified(stamp), is(true));

        byte[] newStamp = testContent(tempFile, source.load(), secondValue);

        assertThat("Stamps should differ for different content", stamp, not(newStamp));
    }

    @Test
    void testFileNot() {
        FileConfigSource source = FileConfigSource.create(Paths.get("/wrong/file/location/that/is/not/there.properties"));

        assertThat("The path should not exist on file system", source.exists(), is(false));
        assertThat("Description should not be null", source.description(), notNullValue());

        // this must work to prevent from race - in case somebody deletes the file after we check it exists
        ConfigParser.Content content = source.load();
        assertThat("File should not exist", content.exists(), is(false));
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "unchecked"})
    private <T> T testTypedOptional(Optional<Object> optional, String field, Class<T> type) {
        assertThat(field + " should not be empty", optional, not(Optional.empty()));
        Object object = optional.get();
        assertThat(field + " should be of correct type", object, instanceOf(type));
        return (T) object;
    }

    private byte[] testContent(Path path, ConfigParser.Content content, String value) throws IOException {
        // guessed from file suffix
        assertThat(content.mediaType(), is(Optional.of("text/x-java-properties")));
        assertThat(content.parser(), is(Optional.empty()));

        // test the stamp
        byte[] stamp = testTypedOptional(content.stamp(), "Stamp", byte[].class);

        // test the target
        Path target = testTypedOptional(content.target(), "Target", Path.class);

        assertThat(target, is(path));

        InputStream data = content.data();

        Properties properties = new Properties();
        properties.load(data);

        assertThat(properties.get("value"), is(value));
        content.close();

        return stamp;
    }

}