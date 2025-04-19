/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link MediaTypes}.
 */
class MediaTypesTest {
    private static final Class<MediaTypes> clazz = MediaTypes.class;
    private static final Set<String> constants = Stream.of(clazz.getDeclaredFields())
            .filter(it -> Modifier.isStatic(it.getModifiers()))
            .filter(it -> Modifier.isFinal(it.getModifiers()))
            .filter(it -> Modifier.isPublic(it.getModifiers()))
            .filter(it -> it.getType().equals(MediaType.class))
            .map(Field::getName)
            .collect(Collectors.toSet());

    @Test
    void testAllEnumValuesHaveConstants() {
        MediaTypeEnum[] expectedNames = MediaTypeEnum.values();

        Set<String> missing = new LinkedHashSet<>();

        for (MediaTypeEnum expectedName : expectedNames) {
            String name = expectedName.name();
            if (!constants.contains(name)) {
                missing.add(name);
            }
        }

        assertThat(missing, emptyCollectionOf(String.class));
    }

    @Test
    void testAllConstantsAreValid() throws NoSuchFieldException, IllegalAccessException {
        // this is to test correct initialization (there may be an issue when the constants
        // are defined on the interface and implemented by enum outside of it)
        for (String constant : constants) {
            MediaType value = (MediaType) clazz.getField(constant)
                    .get(null);

            assertAll(
                    () -> assertThat(value, notNullValue()),
                    () -> assertThat(value.text(), notNullValue()),
                    () -> assertThat(value.subtype(), notNullValue()),
                    () -> assertThat(value.type(), notNullValue())
            );
        }
    }

    @Test
    @DisplayName("Test all MediaType constants have matching String value constant")
    void testAllConstantsHaveValue() throws Exception {
        for (String constant : constants) {
            MediaType mediaType = (MediaType) clazz.getField(constant)
                    .get(null);
            String value = (String) clazz.getField(constant + "_VALUE")
                    .get(null);
            assertThat("String value must match text of the MediaType constant for: " + constant,
                       value,
                       is(mediaType.text()));
        }
    }

    @Test
    void testBuiltIn() throws MalformedURLException {

        // file suffix
        Optional<MediaType> yml = MediaTypes.detectExtensionType("yml");
        assertThat(yml, optionalValue(is(MediaTypes.APPLICATION_X_YAML)));

        // URI
        yml = MediaTypes.detectType(URI.create("http://localhost:8080/static/test.yml"));
        assertThat(yml, optionalValue(is(MediaTypes.APPLICATION_X_YAML)));

        // URL
        yml = MediaTypes.detectType(new URL("http://localhost:8080/static/test.yml"));
        assertThat(yml, optionalValue(is(MediaTypes.APPLICATION_X_YAML)));

        // Path object
        yml = MediaTypes.detectType(Paths.get("/home/config.yml"));
        assertThat(yml, optionalValue(is(MediaTypes.APPLICATION_X_YAML)));

        // Path string
        yml = MediaTypes.detectType("some path/forward\\back\\config.yml");
        assertThat(yml, optionalValue(is(MediaTypes.APPLICATION_X_YAML)));
    }

    @Test
    void testCustom() {
        Optional<MediaType> hocon = MediaTypes.detectExtensionType("json");

        assertThat(hocon, optionalValue(is(MediaTypes.APPLICATION_HOCON)));
    }

    @Test
    void testService() throws MalformedURLException {
        Optional<MediaType> type = MediaTypes.detectExtensionType(CustomTypeDetector.SUFFIX);
        assertThat(type, optionalValue(is(CustomTypeDetector.MEDIA_TYPE)));

        type = MediaTypes.detectType(new URL("http", "localhost", 80, "/test/path.mine"));
        assertThat(type, optionalValue(is(CustomTypeDetector.MEDIA_TYPE_HTTP)));

        type = MediaTypes.detectType(URI.create("http://localhost/files/file.mine"));
        assertThat(type, optionalValue(is(CustomTypeDetector.MEDIA_TYPE)));
    }

    @Test
    void testServiceDockerfile() throws MalformedURLException {
        Optional<MediaType> type = MediaTypes.detectType(new URL("http", "localhost", 80, "/test/Dockerfile.native"));
        assertThat(type, optionalValue(is(DockerfileTypeDetector.MEDIA_TYPE)));

        type = MediaTypes.detectType(URI.create("http://localhost/files/Dockerfile"));
        assertThat(type, optionalValue(is(DockerfileTypeDetector.MEDIA_TYPE)));

        type = MediaTypes.detectType("some text pointing to a file: Dockerfile");
        assertThat(type, optionalValue(is(DockerfileTypeDetector.MEDIA_TYPE)));
    }

    @Test
    void testAllTypes() throws IOException {
        Properties all = new Properties();
        all.load(MediaTypes.class.getResourceAsStream("default-media-types.properties"));

        for (String propertyName : all.stringPropertyNames()) {
            Optional<MediaType> detected = MediaTypes.detectExtensionType(propertyName);

            assertThat("We should find a mapping for all properties", detected, optionalPresent());

            assertThat(detected.map(MediaType::text), optionalValue(containsString("/")));
        }
    }
}