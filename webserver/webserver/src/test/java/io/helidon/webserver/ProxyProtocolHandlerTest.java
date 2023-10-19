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
import static io.helidon.common.testing.junit5.HexStringDecoder.decodeHexString;

class ProxyProtocolHandlerTest {

    static final String V2_PREFIX_2 = "\0x0D\0x0A\0x51\0x55\0x49\0x54\0x0A";

    @Test
    void basicV1Test() throws IOException {
        String header = " TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";     // excludes PROXY prefix
        ProxyProtocolData data = ProxyProtocolHandler.handleV1Protocol(new PushbackInputStream(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII))));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
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
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
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

    @Test
    void basicV2TestIPv4() throws IOException {
        String header = V2_PREFIX_2
                + "\0x20\0x11\0x00\0x0C"    // version, family/protocol, length
                + "\0xC0\0xA8\0x00\0x01"    // 192.168.0.1
                + "\0xC0\0xA8\0x00\0x0B"    // 192.168.0.11
                + "\0xDC\0x04"              // 56324
                + "\0x01\0xBB";             // 443
        ProxyProtocolData data = ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                new ByteArrayInputStream(decodeHexString(header))));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceAddress(), is("192.168.0.1"));
        assertThat(data.destAddress(), is("192.168.0.11"));
        assertThat(data.sourcePort(), is(56324));
        assertThat(data.destPort(), is(443));
    }

    @Test
    void basicV2TestIPv6() throws IOException {
        String header = V2_PREFIX_2
                + "\0x20\0x21\0x00\0x0C"    // version, family/protocol, length
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"    // source
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"    // dest
                + "\0xDC\0x04"              // 56324
                + "\0x01\0xBB";             // 443
        ProxyProtocolData data = ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                new ByteArrayInputStream(decodeHexString(header))));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv6));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceAddress(), is("aaaa:bbbb:cccc:dddd:aaaa:bbbb:cccc:dddd"));
        assertThat(data.destAddress(), is("aaaa:bbbb:cccc:dddd:aaaa:bbbb:cccc:dddd"));
        assertThat(data.sourcePort(), is(56324));
        assertThat(data.destPort(), is(443));
    }

    @Test
    void unknownV2Test() throws IOException {
        String header = V2_PREFIX_2
                + "\0x20\0x00\0x00\0x40"    // version, family/protocol, length=64
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD";
        ProxyProtocolData data = ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                new ByteArrayInputStream(decodeHexString(header))));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceAddress(), nullValue());
        assertThat(data.destAddress(), nullValue());
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destPort(), is(-1));
    }

    @Test
    void badV2Test() {
        String header1 = V2_PREFIX_2
                + "\0x20\0x21\0x00\0x0C"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB"    // bad source
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xDC\0x04"
                + "\0x01\0xBB";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(decodeHexString(header1)))));

        String header2 = V2_PREFIX_2
                + "\0x20\0x21\0x0F\0xFF"    // bad length, over our limit
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xDC\0x04"
                + "\0x01\0xBB";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(decodeHexString(header2)))));

        String header3 = V2_PREFIX_2
                + "\0x20\0x21\0x00\0x0C"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xAA\0xAA\0xBB\0xBB\0xCC\0xCC\0xDD\0xDD"
                + "\0xDC\0x04";             // missing dest port
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(decodeHexString(header3)))));
    }
}
