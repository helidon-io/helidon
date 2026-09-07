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

package io.helidon.http;

import java.util.List;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcceptQueryTest {
    @Test
    void parsesRfcExample() {
        List<HttpMediaType> result = AcceptQuery.parse(
                "\"application/jsonpath\", application/sql;charset=\"UTF-8\"");

        assertAll(
                () -> assertThat(result.size(), is(2)),
                () -> assertThat(result.get(0).mediaType(), equalTo(MediaTypes.create("application/jsonpath"))),
                () -> assertThat(result.get(0).parameters().isEmpty(), is(true)),
                () -> assertThat(result.get(1).mediaType(), equalTo(MediaTypes.create("application/sql"))),
                () -> assertThat(result.get(1).parameters().get("charset"), is("UTF-8"))
        );
    }

    @Test
    void parsesAllowedWildcardsAndLeadingDigitType() {
        List<HttpMediaType> result = AcceptQuery.parse("*/*, application/*, \"1example/query\"");

        assertAll(
                () -> assertThat(result.get(0).mediaType(), equalTo(MediaTypes.WILDCARD)),
                () -> assertThat(result.get(1).text(), is("application/*")),
                () -> assertThat(result.get(2).text(), is("1example/query"))
        );
    }

    @Test
    void treatsStringAndTokenItemsTheSame() {
        List<HttpMediaType> result = AcceptQuery.parse("\"application/json\", application/json");

        assertThat(result.get(0), equalTo(result.get(1)));
    }

    @Test
    void usesLastDuplicateParameterValue() {
        List<HttpMediaType> result = AcceptQuery.parse(
                "application/sql;charset=UTF-8;charset=\"utf-16\"");

        assertThat(result.get(0).parameters().get("charset"), is("utf-16"));
    }

    @Test
    void parsesAllowedWhitespaceAndStringParameterValues() {
        List<HttpMediaType> result = AcceptQuery.parse(
                " application/json; profile=\"a,b;c\" \t,\t application/sql; note=\"\" \t");

        assertAll(
                () -> assertThat(result.get(0).parameters().get("profile"), is("a,b;c")),
                () -> assertThat(result.get(1).parameters().get("note"), is(""))
        );
    }

    @Test
    void combinesSeparateHeaderFieldsBeforeParsing() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.create(HeaderNames.ACCEPT_QUERY, "application/json"));
        headers.add(HeaderValues.create(HeaderNames.ACCEPT_QUERY, "application/sql;charset=UTF-8"));

        List<HttpMediaType> result = ClientResponseHeaders.create(headers).acceptQueries();

        assertAll(
                () -> assertThat(result.size(), is(2)),
                () -> assertThat(result.get(0).text(), is("application/json")),
                () -> assertThat(result.get(1).parameters().get("charset"), is("UTF-8"))
        );
    }

    @Test
    void ignoresAnInvalidWholeField() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderValues.create(HeaderNames.ACCEPT_QUERY, "application/json"));
        headers.add(HeaderValues.create(HeaderNames.ACCEPT_QUERY, "application/sql,"));

        assertThat(ClientResponseHeaders.create(headers).acceptQueries().isEmpty(), is(true));
    }

    @Test
    void handlesAbsentAndEmptyFields() {
        WritableHeaders<?> emptyHeaders = WritableHeaders.create();
        WritableHeaders<?> emptyField = WritableHeaders.create();
        emptyField.set(HeaderValues.create(HeaderNames.ACCEPT_QUERY, ""));

        assertAll(
                () -> assertThat(ClientResponseHeaders.create(emptyHeaders).acceptQueries().isEmpty(), is(true)),
                () -> assertThat(ClientResponseHeaders.create(emptyField).acceptQueries().isEmpty(), is(true)),
                () -> assertThat(AcceptQuery.parse("   ").isEmpty(), is(true))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/json,",
            "application/json,,application/sql",
            "(\"application/json\")",
            "42",
            "application/json;q",
            "application/json;q=?0",
            "application/json;q=1",
            "application/json;q=:YWJj:",
            "application/json;q=@1",
            "application/json;q=%\"value\"",
            "application/json;Charset=UTF-8",
            "application/json ;q=UTF-8",
            "application/json; \tq=UTF-8",
            "\tapplication/json",
            "\"application\\/json\"",
            "\"application/json",
            "application/*+json",
            "*/json",
            "application/json;note=\"non-ASCII-\u00e9\""
    })
    void rejectsInvalidStructuredFields(String value) {
        assertThrows(IllegalArgumentException.class, () -> AcceptQuery.parse(value));
    }

    @Test
    void serializesMediaTypesAndParameters() {
        HttpMediaType sql = HttpMediaType.builder()
                .mediaType(MediaTypes.create("application/sql"))
                .charset("UTF-8")
                .build();

        String result = AcceptQuery.serialize(MediaTypes.APPLICATION_JSON,
                                                     MediaTypes.create("1example", "query"),
                                                     sql);

        assertThat(result, is("application/json, \"1example/query\", application/sql;charset=UTF-8"));
        assertThat(AcceptQuery.parse(result).size(), is(3));
    }

    @Test
    void escapesStructuredFieldStrings() {
        HttpMediaType mediaType = HttpMediaType.builder()
                .mediaType(MediaTypes.APPLICATION_JSON)
                .addParameter("profile", "a\"b\\c")
                .build();

        String result = AcceptQuery.serialize(mediaType);

        assertAll(
                () -> assertThat(result, is("application/json;profile=\"a\\\"b\\\\c\"")),
                () -> assertThat(AcceptQuery.parse(result).get(0).parameters().get("profile"), is("a\"b\\c"))
        );
    }

    @Test
    void addsAcceptedQueriesToServerResponseHeaders() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        HttpMediaType sql = HttpMediaType.builder()
                .mediaType(MediaTypes.create("application/sql"))
                .charset("UTF-8")
                .build();

        assertThat(headers.addAcceptQueries(), sameInstance(headers));
        assertThat(headers.contains(HeaderNames.ACCEPT_QUERY), is(false));

        headers.addAcceptQueries(MediaTypes.APPLICATION_JSON, sql);

        assertAll(
                () -> assertThat(headers.get(HeaderNames.ACCEPT_QUERY).get(),
                                 is("application/json, application/sql;charset=UTF-8")),
                () -> assertThat(headers.acceptQueries().size(), is(2))
        );
    }

    @Test
    void rejectsValuesThatCannotBeSerialized() {
        HttpMediaType invalidParameterName = HttpMediaType.builder()
                .mediaType(MediaTypes.APPLICATION_JSON)
                .addParameter("1parameter", "value")
                .build();
        HttpMediaType invalidParameterValue = HttpMediaType.builder()
                .mediaType(MediaTypes.APPLICATION_JSON)
                .addParameter("parameter", "non-ASCII-\u00e9")
                .build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> AcceptQuery.serialize(MediaTypes.create("*", "json"))),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> AcceptQuery.serialize(MediaTypes.create("application", "*+json"))),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> AcceptQuery.serialize(invalidParameterName)),
                () -> assertThrows(IllegalArgumentException.class,
                                   () -> AcceptQuery.serialize(invalidParameterValue)),
                () -> assertThrows(NullPointerException.class,
                                   () -> AcceptQuery.serialize((MediaType[]) null)),
                () -> assertThrows(NullPointerException.class,
                                   () -> AcceptQuery.serialize(MediaTypes.APPLICATION_JSON, null))
        );
    }
}
