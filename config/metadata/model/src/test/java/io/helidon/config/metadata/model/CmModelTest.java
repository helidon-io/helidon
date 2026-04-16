/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.config.metadata.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.metadata.model.CmModel.CmAllowedValue;
import io.helidon.config.metadata.model.CmModel.CmModule;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.metadata.hson.Hson;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link CmModel}.
 */
class CmModelTest {

    @Test
    void testFromJsonArray() {
        var is = getClass().getResourceAsStream("/" + CmModel.LOCATION);
        assertThat(is, is(not(nullValue())));

        var actual = CmModel.fromJson(parseJson(is));
        assertThat(actual.modules(), is(hasItem(allOf(
                hasProperty("module", CmModule::module, is("com.acme")),
                hasProperty("types", CmModule::types, is(hasItem(allOf(List.of(
                        hasProperty("type", CmType::typeName, is("com.acme.AcmeConfig")),
                        hasProperty("description", CmType::description, is(Optional.of("ACME configuration"))),
                        hasProperty("prefix", CmType::prefix, is(Optional.of("acme"))),
                        hasProperty("standalone", CmType::standalone, is(false)),
                        hasProperty("inherits", CmType::inherits, is(List.of())),
                        hasProperty("provides", CmType::provides, is(hasItems(
                                "com.acme.AcmeProvider"))),
                        hasProperty("options", CmType::options, is(hasItem(allOf(List.of(
                                hasProperty("key", CmOption::key, is(Optional.of("mode"))),
                                hasProperty("description", CmOption::description, is(Optional.of("Mode"))),
                                hasProperty("type", CmOption::typeName, is("com.acme.AcmeMode")),
                                hasProperty("defaultValue", CmOption::defaultValue, is(Optional.of("MODE1"))),
                                hasProperty("required", CmOption::required, is(false)),
                                hasProperty("experimental", CmOption::experimental, is(false)),
                                hasProperty("deprecated", CmOption::deprecated, is(false)),
                                hasProperty("provider", CmOption::provider, is(false)),
                                hasProperty("merge", CmOption::merge, is(false)),
                                hasProperty("kind", CmOption::kind, is(CmOption.Kind.VALUE)),
                                hasProperty("allowedValues", CmOption::allowedValues, is(allOf(
                                        hasItem(allOf(
                                                hasProperty("value", CmAllowedValue::value, is("MODE1")),
                                                hasProperty("description", CmAllowedValue::description, is("mode 1"))
                                        )),
                                        hasItem(allOf(
                                                hasProperty("value", CmAllowedValue::value, is("MODE2")),
                                                hasProperty("description", CmAllowedValue::description, is("mode 2"))
                                        )),
                                        hasItem(allOf(
                                                hasProperty("value", CmAllowedValue::value, is("MODE3")),
                                                hasProperty("description", CmAllowedValue::description, is("mode 3"))
                                        ))
                                )))
                        )))))
                )))))
        ))));
    }

    @Test
    void testFromJsonArraySupplementaryUnicode() {
        String description = "Smile \uD83D\uDE00";
        JsonArray jsonArray = JsonArray.create(JsonObject.builder()
                                                      .set("module", "com.acme")
                                                      .setValues("types", List.of(JsonObject.builder()
                                                                                           .set("type",
                                                                                                "com.acme.AcmeConfig")
                                                                                           .set("description",
                                                                                                description)
                                                                                           .build()))
                                                      .build());

        var actual = CmModel.fromJson(jsonArray);

        assertThat(actual.modules().get(0).types().get(0).description(), is(Optional.of(description)));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testFromJsonInputStream() throws IOException {
        var is = getClass().getResourceAsStream("/" + CmModel.LOCATION);
        assertThat(is, is(not(nullValue())));

        var bytes = is.readAllBytes();
        var expected = CmModel.fromJson(parseHson(new ByteArrayInputStream(bytes)));
        var actual = CmModel.fromJson(new ByteArrayInputStream(bytes));

        assertThat(actual, is(expected));
    }

    @Test
    void testToJsonArray() throws IOException {
        var is = getClass().getResourceAsStream("/" + CmModel.LOCATION);
        assertThat(is, is(not(nullValue())));

        var expected = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        var metadata = CmModel.fromJson(parseJson(
                new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8))));
        var actual = formatJson(metadata.toJsonArray());

        assertThat(actual, is(expected));
    }

    @Test
    @SuppressWarnings("deprecation")
    void testDeprecatedHsonMethods() throws IOException {
        var is = getClass().getResourceAsStream("/" + CmModel.LOCATION);
        assertThat(is, is(not(nullValue())));

        var expected = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        var expectedModel = CmModel.fromJson(parseJson(
                new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8))));
        var actualModel = CmModel.fromJson(parseHson(
                new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8))));

        assertThat(actualModel, is(expectedModel));
        assertThat(formatHson(actualModel.toJson()), is(expected));
    }

    @Test
    void testLoadAll() throws Exception {
        var url = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL();
        try (var cl = new URLClassLoader(new URL[] {url}, null)) {
            var actual = CmModel.loadAll(cl);
            assertThat(actual.modules(), is(hasItem(
                    hasProperty("module", CmModule::module, is("com.acme")))
            ));
        }
    }

    @Test
    void testIdemPotent() {
        var is = getClass().getResourceAsStream("/" + CmModel.LOCATION);
        assertThat(is, is(not(nullValue())));

        var expected = CmModel.fromJson(parseJson(is));
        var expectedJson = formatJson(expected.toJsonArray());
        var actual = CmModel.fromJson(parseJson(
                new ByteArrayInputStream(expectedJson.getBytes(StandardCharsets.UTF_8))));

        assertThat(actual, is(expected));
    }

    @Test
    void testSimpleTypeName() {
        var type = CmType.builder().type("com.acme.AcmeConfig").build();
        var option = CmOption.builder().type("com.acme.AcmeMode").build();

        assertThat(CmType.simpleTypeName("com.acme.AcmeConfig"), is("AcmeConfig"));
        assertThat(type.simpleTypeName(), is("AcmeConfig"));
        assertThat(option.simpleTypeName(), is("AcmeMode"));
    }

    static JsonArray parseJson(InputStream is) {
        return JsonParser.create(is).readJsonArray();
    }

    static Hson.Array parseHson(InputStream is) {
        return Hson.parse(is).asArray();
    }

    static String formatJson(JsonArray jsonArray) {
        return formatHson(Hson.parse(new ByteArrayInputStream(jsonArray.toString().getBytes(StandardCharsets.UTF_8)))
                                  .asArray());
    }

    static String formatHson(Hson.Array jsonArray) {
        var baos = new ByteArrayOutputStream();
        try (var printer = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            jsonArray.write(printer, true);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }
}
