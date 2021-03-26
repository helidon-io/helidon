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
package io.helidon.tests.integration.webclient;

import io.helidon.common.http.FormParams;
import io.helidon.common.http.MediaType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests if client-server form sending.
 */
public class FormTest extends TestParent {

    private static final FormParams TEST_FORM = FormParams.builder()
            .add("name", "David Tester")
            .build();

    private static final String SPECIAL = "special";
    private static final String MULTIPLE = "multiple";
    private static final String NO_VALUE = "noValue";
    private static final String SPECIAL_VALUE = "some &@#/ special value";

    private static final FormParams ADVANCED_TEST_FORM = FormParams.builder()
            .add(SPECIAL, SPECIAL_VALUE)
            .add(MULTIPLE, "value1", "value2")
            .add(NO_VALUE)
            .build();

    @Test
    public void testHelloWorld() {
        webClient.post()
                .path("/form")
                .submit(TEST_FORM, String.class)
                .thenAccept(resp -> assertThat(resp, is("Hi David Tester")))
                .await();
    }

    @Test
    public void testSpecificContentType() {
        webClient.post()
                .path("/form")
                .contentType(MediaType.TEXT_PLAIN)
                .submit(TEST_FORM, String.class)
                .thenAccept(resp -> assertThat(resp, is("Hi David Tester")))
                .await();
    }

    @Test
    public void testSpecificContentTypeIncorrect() {
        Exception ex = assertThrows(IllegalStateException.class, () -> webClient.post()
                .path("/form")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .submit(TEST_FORM).await());

        assertThat(ex.getCause().getMessage(),
                   startsWith("No writer found for type: class io.helidon.common.http.FormParamsImpl"));
    }

    @Test
    public void testFormContent() {
        FormParams received = webClient.post()
                .path("/form/content")
                .submit(ADVANCED_TEST_FORM, FormParams.class)
                .await();

        assertThat(received.all(SPECIAL), is(ADVANCED_TEST_FORM.all(SPECIAL)));
        assertThat(received.all(MULTIPLE), is(ADVANCED_TEST_FORM.all(MULTIPLE)));
        assertThat(received.all(NO_VALUE).size(), is(0));
    }
}
