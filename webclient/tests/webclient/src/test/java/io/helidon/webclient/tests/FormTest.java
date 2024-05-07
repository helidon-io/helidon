/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.webclient.tests;

import java.util.List;

import io.helidon.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests if client-server form sending.
 */
public class FormTest extends TestParent {

    private static final Parameters TEST_FORM = Parameters.builder("webclient-form")
            .add("name", "David Tester")
            .build();

    private static final String SPECIAL = "special";
    private static final String MULTIPLE = "multiple";
    private static final String NO_VALUE = "noValue";
    private static final String SPECIAL_VALUE = "some &@#/ special value";

    private static final Parameters ADVANCED_TEST_FORM = Parameters.builder("webclient-form")
            .add(SPECIAL, SPECIAL_VALUE)
            .add(MULTIPLE, "value1", "value2")
            .add(NO_VALUE)
            .build();

    FormTest(WebServer server) {
        super(server);
    }

    @Test
    public void testHelloWorld() {
        try (Http1ClientResponse res = client.post("/form").submit(TEST_FORM)) {
            assertThat(res.as(String.class), is("Hi David Tester"));
        }
    }

    @Test
    public void testSpecificContentType() {
        try (Http1ClientResponse res = client.post("/form")
                .contentType(MediaTypes.TEXT_PLAIN)
                .submit(TEST_FORM)) {
            assertThat(res.as(String.class), is("Hi David Tester"));
        }
    }

    @Test
    public void testSpecificContentTypeIncorrect() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try (Http1ClientResponse ignored = client.post()
                    .path("/form")
                    .contentType(HttpMediaType.create(MediaTypes.APPLICATION_ATOM_XML))
                    .submit(TEST_FORM)) {

                Assertions.fail();
            }
        });

        assertThat(ex.getMessage(), startsWith("No client request media writer for class "));
    }

    @Test
    public void testFormContent() {
        try (Http1ClientResponse res = client.post("/form/content").submit(ADVANCED_TEST_FORM)) {
            Parameters received = res.as(Parameters.class);
            assertThat(received.all(SPECIAL, List::of), is(ADVANCED_TEST_FORM.all(SPECIAL)));
            assertThat(received.all(MULTIPLE), is(ADVANCED_TEST_FORM.all(MULTIPLE)));
            assertThat(received.all(NO_VALUE).size(), is(0));
            assertThat(received.first(NO_VALUE).isEmpty(), is(true));
        }
    }
}
