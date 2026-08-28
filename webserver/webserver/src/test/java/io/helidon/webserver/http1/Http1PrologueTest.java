/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class Http1PrologueTest {
    @Test
    void testOk() {
        DataReader reader = DataReader.create(() -> "GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
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
        DataReader reader = DataReader.create(() -> "GET /01234567890123456789012 HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        Http1Prologue p = new Http1Prologue(reader, 20, false);

        RequestException e = assertThrows(RequestException.class, p::readPrologue);

        assertThat(e.status(), is(Status.REQUEST_URI_TOO_LONG_414));
        assertThat(e.eventType(), is(DirectHandler.EventType.BAD_REQUEST));
    }

    @Test
    void testHttp10Error() {
        DataReader reader = DataReader.create(() -> "GET / HTTP/1.0\r\n".getBytes(StandardCharsets.US_ASCII));
        Http1Prologue p = new Http1Prologue(reader, 100, false);

        try {
            p.readPrologue();
            fail();     // exception not thrown
        } catch (RequestException e) {
            assertThat(e.status(), is(Status.HTTP_VERSION_NOT_SUPPORTED_505));
            assertThat(e.safeMessage(), is(true));
            assertThat(e.getMessage(), containsString("HTTP 1.0 is not supported"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com:443",
            "service+name:443",
            "service%2Dname:443",
            "192.0.2.10:8443",
            "[2001:db8::1]:9443"
    })
    void testConnectAuthorityForm(String authority) {
        DataReader reader = DataReader.create(() -> ("CONNECT " + authority + " HTTP/1.1\r\n")
                .getBytes(StandardCharsets.US_ASCII));

        HttpPrologue prologue = new Http1Prologue(reader, 100, true).readPrologue();

        assertThat(prologue.method(), is(Method.CONNECT));
        assertThat(prologue.uriPath().rawPath(), is(authority));
        assertThat(prologue.uriPath().path(), is(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com",
            ":443",
            "boards/",
            "user@example.com:443",
            "http://example.com:443",
            "example.com:443?query=value",
            "example.com:443#fragment",
            "service/name:443",
            "service%2Gname:443",
            "[2001:db8::1]",
            "[1:2:3]:443",
            "[1.2.3.4]:443",
            "[1:2:3:4:5:6:010.000.000.001]:443",
            "[v1.]:443",
            "[vG.fe80]:443",
            "[v1.fe80]x:443",
            "2001:db8::1:443",
            "example.com:not-a-port",
            "example.com:0",
            "example.com:65536",
            "example.com:2147483648"
    })
    void testInvalidConnectAuthorityForm(String authority) {
        DataReader reader = DataReader.create(() -> ("CONNECT " + authority + " HTTP/1.1\r\n")
                .getBytes(StandardCharsets.US_ASCII));

        RequestException exception = assertThrows(RequestException.class,
                                                  () -> new Http1Prologue(reader, 100, true).readPrologue());

        assertThat(exception.status(), is(Status.BAD_REQUEST_400));
        assertThat(exception.eventType(), is(DirectHandler.EventType.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[v1.fe80]:443", "[Vf.foo-bar]:443"})
    void testConnectIpFutureIsNotImplemented(String authority) {
        DataReader reader = DataReader.create(() -> ("CONNECT " + authority + " HTTP/1.1\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        Http1Prologue prologue = new Http1Prologue(reader, 100, true);

        RequestException exception = assertThrows(RequestException.class, prologue::readPrologue);

        assertThat(exception.status(), is(Status.NOT_IMPLEMENTED_501));
        assertThat(exception.eventType(), is(DirectHandler.EventType.OTHER));
    }

    @Test
    void testRelativeOriginFormIsBadRequest() {
        DataReader reader = DataReader.create(() -> "GET boards/ HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        Http1Prologue p = new Http1Prologue(reader, 100, true);

        RequestException e = assertThrows(RequestException.class, p::readPrologue);

        assertThat(e.status(), is(Status.BAD_REQUEST_400));
        assertThat(e.eventType(), is(DirectHandler.EventType.BAD_REQUEST));
        assertThat(e.getMessage(), containsString("Relative path in HTTP request-target"));
    }

    @Test
    void testQueryOnlyOriginFormIsBadRequest() {
        DataReader reader = DataReader.create(() -> "GET ?q=1 HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        Http1Prologue p = new Http1Prologue(reader, 100, true);

        RequestException e = assertThrows(RequestException.class, p::readPrologue);

        assertThat(e.status(), is(Status.BAD_REQUEST_400));
        assertThat(e.eventType(), is(DirectHandler.EventType.BAD_REQUEST));
        assertThat(e.getMessage(), containsString("Relative path in HTTP request-target"));
    }

    @Test
    void testAsteriskFormRemainsValid() {
        DataReader reader = DataReader.create(() -> "OPTIONS * HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        HttpPrologue prologue = new Http1Prologue(reader, 100, true).readPrologue();

        assertThat(prologue.method(), is(Method.OPTIONS));
        assertThat(prologue.uriPath().rawPath(), is("*"));
        assertThat(prologue.uriPath().path(), is("*"));
        assertThat(prologue.uriPath().absolute().path(), is("/"));
        assertThat(prologue.uriPath().segments().size(), is(1));
        assertThat(prologue.uriPath().segments().get(0).value(), is("*"));
    }

    @Test
    void testAsteriskFormRequiresOptions() {
        assertInvalidRequestTarget("GET * HTTP/1.1\r\n");
    }

    @Test
    void testAsteriskFormRejectsFragment() {
        assertInvalidRequestTarget("OPTIONS *#fragment HTTP/1.1\r\n");
    }

    @Test
    void testAsteriskFormDoesNotAllowQuery() {
        assertInvalidRequestTarget("OPTIONS *?q=1 HTTP/1.1\r\n");
    }

    @Test
    void testAbsoluteFormRemainsValid() {
        DataReader reader = DataReader.create(() -> "GET http://example.com/boards/ HTTP/1.1\r\n"
                .getBytes(StandardCharsets.US_ASCII));
        HttpPrologue prologue = new Http1Prologue(reader, 100, true).readPrologue();

        assertThat(prologue.method(), is(Method.GET));
        assertThat(prologue.uriPath().path(), is("/boards/"));
    }

    private static void assertInvalidRequestTarget(String requestLine) {
        DataReader reader = DataReader.create(() -> requestLine.getBytes(StandardCharsets.US_ASCII));
        Http1Prologue prologue = new Http1Prologue(reader, 100, true);

        RequestException e = assertThrows(RequestException.class, prologue::readPrologue);

        assertThat(e.status(), is(Status.BAD_REQUEST_400));
        assertThat(e.eventType(), is(DirectHandler.EventType.BAD_REQUEST));
    }

}
