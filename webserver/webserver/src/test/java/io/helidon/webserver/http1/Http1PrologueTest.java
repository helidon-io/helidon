/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.DirectHandler;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.Status;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1PrologueTest {
    @Test
    void testOk() {
        DataReader reader = new DataReader(() -> "GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        Http1Prologue p = new Http1Prologue(reader, 100, false);

        HttpPrologue prologue = p.readPrologue();
        assertThat(prologue.method(), is(Method.GET));
        assertThat(prologue.uriPath().path(), is("/"));
        assertThat(prologue.protocol(), is("HTTP"));
        assertThat(prologue.protocolVersion(), is("1.1"));
    }

    @Test
    void testUriTooLong() {
        // make sure this regression does not happen again
        DataReader reader = new DataReader(() -> "GET /01234567890123456789012 HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        Http1Prologue p = new Http1Prologue(reader, 20, false);

        RequestException e = assertThrows(RequestException.class, p::readPrologue);

        assertThat(e.status(), is(Status.REQUEST_URI_TOO_LONG_414));
        assertThat(e.eventType(), is(DirectHandler.EventType.BAD_REQUEST));
    }
}