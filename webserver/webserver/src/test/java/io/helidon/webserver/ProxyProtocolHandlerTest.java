/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.http.RequestException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyProtocolHandlerTest {

    @Test
    void basicV1Test() throws IOException {
        String header = " TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";     // excludes PROXY prefix
        ProxyProtocolData data = ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII))));
        assertThat(data.protocolFamily(), is(ProxyProtocolData.ProtocolFamily.TCP4));
        assertThat(data.sourceAddress(), is("192.168.0.1"));
        assertThat(data.destAddress(), is("192.168.0.11"));
        assertThat(data.sourcePort(), is(56324));
        assertThat(data.destPort(), is(443));
    }

    @Test
    void unknownV1Test() throws IOException {
        String header = " UNKNOWN\r\n";     // excludes PROXY prefix
        ProxyProtocolData data = ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII))));
        assertThat(data.protocolFamily(), is(ProxyProtocolData.ProtocolFamily.UNKNOWN));
        assertThat(data.sourceAddress(), nullValue());
        assertThat(data.destAddress(), nullValue());
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destPort(), is(-1));
    }

    @Test
    void badV1Test() {
        String header1 = " MYPROTOCOL 192.168.0.1 192.168.0.11 56324 443\r\n";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(header1.getBytes(StandardCharsets.US_ASCII)))));
        String header2 = " TCP4 192.168.0.1 192.168.0.11 56324\r\n";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(header2.getBytes(StandardCharsets.US_ASCII)))));
        String header3 = " TCP4 192.168.0.1 192.168.0.11 56324 443";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(header3.getBytes(StandardCharsets.US_ASCII)))));
        String header4 = " TCP4 192.168.0.1 56324 443\r\n";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(header4.getBytes(StandardCharsets.US_ASCII)))));
    }
}
