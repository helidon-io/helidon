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

package io.helidon.json.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.json.JsonException;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileParser;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class HashCollisionDispatchTest {
    // FNV-1a("project") == FNV-1a("unknown-IpUce6").
    private static final String COLLIDING_PROPERTY = "{\"unknown-IpUce6\":\"payments\"}";

    private final JsonBinding jsonBinding;

    HashCollisionDispatchTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingUnknownProperty(BindingMethod method) {
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, StrictRequest.class));
        assertThat(exception.getMessage(), containsString("Unknown properties"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingUnknownOptionalProperty(BindingMethod method) {
        assertThrows(JsonException.class,
                     () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, StrictOptionalRequest.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void collisionDoesNotSatisfyRequiredProperty(BindingMethod method) {
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, RequiredRequest.class));
        assertThat(exception.getMessage(), containsString("project"));
        assertThat(exception.getMessage(), containsString("missing"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void ignoresCollidingUnknownProperty(BindingMethod method) {
        OptionalRequest request = method.deserialize(jsonBinding, COLLIDING_PROPERTY, OptionalRequest.class);
        assertThat(request.project(), nullValue());
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void collisionDoesNotOverwriteKnownProperty(BindingMethod method) {
        String json = "{\"project\":\"original\",\"unknown-IpUce6\":\"replacement\"}";
        RequiredRequest request = method.deserialize(jsonBinding, json, RequiredRequest.class);
        assertThat(request.project(), is("original"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void skipsCollidingStructuredValueAndContinues(BindingMethod method) {
        String json = "{\"unknown-IpUce6\":{\"nested\":[1,2]},\"project\":\"payments\"}";
        RequiredRequest request = method.deserialize(jsonBinding, json, RequiredRequest.class);
        assertThat(request.project(), is("payments"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollisionWithManyProperties(BindingMethod method) {
        assertThrows(JsonException.class,
                     () -> method.deserialize(jsonBinding, COLLIDING_PROPERTY, ManyProperties.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsKnownPropertyWithManyProperties(BindingMethod method) {
        ManyProperties request = method.deserialize(jsonBinding, "{\"project\":\"payments\",\"ninth\":9}",
                                                    ManyProperties.class);
        assertThat(request.project(), is("payments"));
        assertThat(request.ninth(), is(9));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsEscapedKnownProperty(BindingMethod method) {
        StrictRequest request = method.deserialize(jsonBinding, "{\"pro\\u006aect\":\"payments\"}", StrictRequest.class);
        assertThat(request.project(), is("payments"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsEscapedCollidingProperty(BindingMethod method) {
        String json = "{\"unknown-IpUce\\u0036\":\"payments\"}";
        assertThrows(JsonException.class, () -> method.deserialize(jsonBinding, json, StrictRequest.class));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsUnicodeProperty(BindingMethod method) {
        UnicodeRequest expected = new UnicodeRequest("payments");
        assertThat(method.deserialize(jsonBinding, "{\"caf\u00e9\":\"payments\"}", UnicodeRequest.class), is(expected));
        assertThat(method.deserialize(jsonBinding, "{\"caf\\u00e9\":\"payments\"}", UnicodeRequest.class), is(expected));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingDiscriminator(BindingMethod method) {
        // FNV-1a("costarring") == FNV-1a("liquid").
        String json = "{\"liquid\":\"project\",\"value\":\"payments\"}";
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, json, PolymorphicRequest.class));
        assertThat(exception.getMessage(), containsString("Missing property containing alias"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void rejectsCollidingAlias(BindingMethod method) {
        String json = "{\"costarring\":\"unknown-IpUce6\",\"value\":\"payments\"}";
        JsonException exception = assertThrows(JsonException.class,
                                              () -> method.deserialize(jsonBinding, json, PolymorphicRequest.class));
        assertThat(exception.getMessage(), containsString("Unknown alias value"));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    void readsEscapedDiscriminatorAndAlias(BindingMethod method) {
        String json = "{\"costar\\u0072ing\":\"pro\\u006aect\",\"value\":\"payments\"}";
        assertThat(method.deserialize(jsonBinding, json, PolymorphicRequest.class), is(new ProjectRequest("payments")));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void rejectsCollidingPropertyWithAlternateParser(ParserInput input) {
        JsonParser parser = input.parser(COLLIDING_PROPERTY, jsonBinding);
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(parser, StrictRequest.class));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void skipsCollidingPropertyWithAlternateParser(ParserInput input) {
        String json = "{\"unknown-IpUce6\":[1,2],\"project\":\"payments\"}";
        RequiredRequest request = jsonBinding.deserialize(input.parser(json, jsonBinding), RequiredRequest.class);
        assertThat(request.project(), is("payments"));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void rejectsCollidingAliasWithAlternateParser(ParserInput input) {
        JsonParser parser = input.parser("{\"costarring\":\"unknown-IpUce6\",\"value\":\"payments\"}", jsonBinding);
        assertThrows(JsonException.class, () -> jsonBinding.deserialize(parser, PolymorphicRequest.class));
    }

    @ParameterizedTest
    @EnumSource(ParserInput.class)
    void readsKnownNamesWithAlternateParser(ParserInput input) {
        String json = "{\"costar\\u0072ing\":\"pro\\u006aect\",\"value\":\"payments\"}";
        assertThat(jsonBinding.deserialize(input.parser(json, jsonBinding), PolymorphicRequest.class),
                   is(new ProjectRequest("payments")));
    }

    enum ParserInput {
        SMALL_STREAM,
        JSON_VALUE,
        SMILE_ARRAY,
        SMILE_STREAM;

        JsonParser parser(String json, JsonBinding binding) {
            if (this == SMALL_STREAM) {
                return JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 6);
            }
            JsonValue value = JsonParser.create(json).readJsonValue();
            if (this == JSON_VALUE) {
                return JsonParser.create(value);
            }
            byte[] smile = SmileBindingSupport.serializeSmile(binding, value);
            return this == SMILE_ARRAY ? SmileParser.create(smile) : SmileParser.create(new ByteArrayInputStream(smile), 8);
        }
    }

    @Json.Entity
    @Json.Polymorphic(key = "costarring")
    @Json.Subtype(alias = "project", value = ProjectRequest.class)
    interface PolymorphicRequest {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record StrictRequest(@Json.Required String project) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record StrictOptionalRequest(String project) {
    }

    @Json.Entity
    record RequiredRequest(@Json.Required String project) {
    }

    @Json.Entity
    record OptionalRequest(String project) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record ManyProperties(@Json.Required String project, int first, int second, int third, int fourth, int fifth,
                          int sixth, int seventh, int eighth, int ninth) {
    }

    @Json.Entity
    @Json.FailOnUnknown
    record UnicodeRequest(@Json.Property("caf\u00e9") String value) {
    }

    @Json.Entity
    record ProjectRequest(String value) implements PolymorphicRequest {
    }
}
