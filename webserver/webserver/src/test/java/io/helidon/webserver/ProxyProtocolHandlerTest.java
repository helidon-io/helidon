/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

import io.helidon.http.RequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyProtocolHandlerTest {

    static final String V2_PREFIX_2 = "0D:0A:51:55:49:54:0A:";

    private static final HexFormat hexFormat = HexFormat.of().withUpperCase().withDelimiter(":");

    @Test
    void basicV1Test() throws IOException {
        String header = "PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n";
        ProxyProtocolData data = ProxyProtocolHandler.handleAnyProtocol(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII)));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceAddress(), is("192.168.0.1"));
        assertThat(data.destAddress(), is("192.168.0.11"));
        assertThat(data.sourcePort(), is(56324));
        assertThat(data.destPort(), is(443));
    }

    @Test
    void basicV1TestIPv6() throws IOException {
        String header = "PROXY TCP6 2001:db8::1 2001:db8::2 56324 443\r\n";
        ProxyProtocolData data = ProxyProtocolHandler.handleAnyProtocol(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII)));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv6));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceAddress(), is("2001:db8::1"));
        assertThat(data.destAddress(), is("2001:db8::2"));
        assertThat(data.sourcePort(), is(56324));
        assertThat(data.destPort(), is(443));
    }

    @Test
    void v1CanonicalZeroValues() throws IOException {
        String header = "PROXY TCP4 0.0.0.0 192.168.0.1 0 0\r\n";
        ProxyProtocolData data = ProxyProtocolHandler.handleAnyProtocol(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII)));
        assertThat(data.sourceAddress(), is("0.0.0.0"));
        assertThat(data.destAddress(), is("192.168.0.1"));
        assertThat(data.sourcePort(), is(0));
        assertThat(data.destPort(), is(0));
    }

    @Test
    void unknownV1Test() throws IOException {
        String header = " UNKNOWN ignored fields\r\nGET / HTTP/1.1\r\n";     // excludes PROXY prefix
        ByteArrayInputStream inputStream = new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII));
        ProxyProtocolData data = ProxyProtocolHandler.handleV1Protocol(inputStream);
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.destAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destPort(), is(-1));
        assertThat(inputStream.read(), is((int) 'G'));
    }

    @Test
    void unknownV1TestMaxLength() throws IOException {
        String header = "PROXY UNKNOWN " + "a".repeat(91) + "\r\nGET / HTTP/1.1\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII));
        ProxyProtocolData data = ProxyProtocolHandler.handleAnyProtocol(inputStream);
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.destAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destPort(), is(-1));
        assertThat(inputStream.read(), is((int) 'G'));

        assertBadV1("PROXY UNKNOWN " + "a".repeat(92) + "\r\n");
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
    void badV1TestInvalidMetadata() {
        assertBadV1("PROXY TCP4 999.168.0.1 192.168.0.11 56324 443\r\n");
        assertBadV1("PROXY TCP4 192.168.0.1 192.168.0.11. 56324 443\r\n");
        assertBadV1("PROXY TCP4 2001:db8::1 192.168.0.11 56324 443\r\n");
        assertBadV1("PROXY TCP6 192.168.0.1 2001:db8::2 56324 443\r\n");
        assertBadV1("PROXY TCP6 2001:db8:::1 2001:db8::2 56324 443\r\n");
        assertBadV1("PROXY TCP4 192.168.0.1 192.168.0.11 65536 443\r\n");
        assertBadV1("PROXY TCP4 192.168.0.1 192.168.0.11 56324 -1\r\n");
        assertBadV1("PROXY TCP4 192.168.0.1 192.168.0.11 56324 999999999999999999999999\r\n");
    }

    @Test
    void v1RejectsLeadingZeroSourceAddress() {
        assertBadV1("PROXY TCP4 010.000.000.001 192.168.0.11 80 443\r\n");
    }

    @Test
    void v1RejectsLeadingZeroDestinationAddress() {
        assertBadV1("PROXY TCP4 10.0.0.1 192.168.000.011 80 443\r\n");
    }

    @Test
    void v1RejectsLeadingZeroSourcePort() {
        assertBadV1("PROXY TCP4 10.0.0.1 192.168.0.11 00080 443\r\n");
    }

    @Test
    void v1RejectsLeadingZeroDestinationPort() {
        assertBadV1("PROXY TCP4 10.0.0.1 192.168.0.11 80 00443\r\n");
    }

    @Test
    void basicV2TestIPv4() throws IOException {
        String header = V2_PREFIX_2
                + "21:11:00:0C:"    // version, family/protocol, length
                + "C0:A8:00:01:"    // 192.168.0.1
                + "C0:A8:00:0B:"    // 192.168.0.11
                + "DC:04:"              // 56324
                + "01:BB";             // 443
        ProxyProtocolData data = ProxyProtocolHandler.handleV2Protocol(
                new ByteArrayInputStream(hexFormat.parseHex(header)));
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
                + "21:21:00:24:"                // version, family/protocol, length
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"    // source
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"    // dest
                + "DC:04:"                      // 56324
                + "01:BB";                      // 443
        ProxyProtocolData data = ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                new ByteArrayInputStream(hexFormat.parseHex(header))));
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
                + "21:00:00:40:"    // version, family/protocol, length=64
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD";
        ProxyProtocolData data = ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                new ByteArrayInputStream(hexFormat.parseHex(header))));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.destAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destPort(), is(-1));
    }

    @Test
    void badV2Test() {
        String header1 = V2_PREFIX_2
                + "21:21:00:0C:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:"    // bad source
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "DC:04:"
                + "01:BB";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(hexFormat.parseHex(header1)))));

        String header2 = V2_PREFIX_2
                + "21:21:0F:FF:"    // bad length, over our limit
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "DC:04:"
                + "01:BB";
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(hexFormat.parseHex(header2)))));

        String header3 = V2_PREFIX_2
                + "21:21:00:0C:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "AA:AA:BB:BB:CC:CC:DD:DD:"
                + "DC:04";          // missing dest port
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleV2Protocol(new PushbackInputStream(
                        new ByteArrayInputStream(hexFormat.parseHex(header3)))));
    }

    @Test
    void prefixTooShort() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex("0D:0A:0D:0A"))));
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex("0D:0A"))));
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex("00"))));
    }

    @Test
    void wrongPrefix() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex("00:00:00:00:00"))));
    }

    @Test
    void invalidV2Version() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
            "31" // version 3
        ))));
    }

    @Test
    void invalidV2Command() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
            "22" // command 0x2
        ))));
    }

    @Test
    void v2LocalIgnoresInvalidProtocolBlock() throws IOException {
        var inputStream = new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "20:" + // version 2, command 0 = LOCAL
                "FF:" + // ignored family and protocol
                "00:00:" + // declared header length
                "47")); // first application byte, ASCII 'G'

        var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(inputStream);
        SocketAddress unspecifiedAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0);
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.LOCAL));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceSocketAddress(), is(unspecifiedAddress));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destSocketAddress(), is(unspecifiedAddress));
        assertThat(data.destAddress(), is(""));
        assertThat(data.destPort(), is(-1));
        assertThat(data.tlvs().size(), is(0));
        // LOCAL must ignore its protocol block without consuming application data.
        assertThat(inputStream.read(), is((int) 'G'));
    }

    @Test
    void v2LocalSkipsDeclaredProtocolBlock() throws IOException {
        var inputStream = new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "20:" + // version 2, command 0 = LOCAL
                "FF:" + // ignored family and protocol
                "00:04:" + // declared header length
                "DE:AD:C0:DE:" + // ignored protocol block
                "47")); // first application byte, ASCII 'G'

        var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(inputStream);
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.LOCAL));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destAddress(), is(""));
        assertThat(data.destPort(), is(-1));
        assertThat(data.tlvs().size(), is(0));
        // The declared length remains authoritative even though LOCAL payload bytes are opaque.
        assertThat(inputStream.read(), is((int) 'G'));
    }

    @Test
    void v2LocalRejectsTruncatedFixedHeader() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "20:FF:00" // incomplete two-byte length
        ))));
    }

    @Test
    void v2LocalRejectsTruncatedProtocolBlock() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "20:FF:00:04:DE:AD:C0" // only three of four declared bytes
        ))));
    }

    @Test
    void invalidV2Family() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
            "21:41:00:00" // family 0x4
        ))));
    }

    @Test
    void invalidV2Protocol() {
        assertThrows(RequestException.class, () -> ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
            "21:23:00:00" // protocol 0x3
        ))));
    }

    @Test
    void v2MaxLengthAddresses() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
            "21:" + // version 2, command 1 = PROXY
            "31:" + // family 3 = UNIX, protocol 1 = TCP
            "00:D8:" + // length (108 * 2 == 216, in hex 216 = 0xD8)
            // source address. A sequence of the 4 bytes of "/foo" repeated 27 times = 108 bytes
            "2F:66:6F:6F:".repeat(27) +
            // destination address. A sequence of the 4 bytes of "/bar" repeated 27 times == 108 bytes
            "2F:62:61:72:".repeat(27).substring(0, "2F:62:61:72:".length() * 27 - 1)
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNIX));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(((UnixDomainSocketAddress) data.sourceSocketAddress()).getPath().toString(), is((File.separator + "foo").repeat(27)));
        assertThat(((UnixDomainSocketAddress) data.destSocketAddress()).getPath().toString(), is((File.separator + "bar").repeat(27)));
    }

    @Test
    void v2UnknownFamilySkipAddressWithoutTlv() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "01:" + // family 0 = UNKNOWN, protocol 1 = TCP
                "00:0C:" + // IPv4 family address base size = 12 = 0x0C
                // source address. IPv4 255.0.255.0
                "FF:00:FF:00:" +
                // destination address. IPv4 255.0.255.0
                "00:FF:00:FF:" +
                // source port 43707
                "AA:BB:" +
                // destination port 48042
                "BB:AA"
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0)));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0)));
        assertThat(data.destAddress(), is(""));
        assertThat(data.destPort(), is(-1));
    }

    @Test
    void v2UnknownFamilySkipAddressWithTlv() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "02:" + // family 0 = UNKNOWN, protocol 2 = UDP
                "00:10:" + // IPv4 family address base size = 12 = 0x0C, plus 4 extra bytes gives 16 = 0x10
                // source address. IPv4 255.0.255.0
                "FF:00:FF:00:" +
                // destination address. IPv4 255.0.255.0
                "00:FF:00:FF:" +
                // source port 43707
                "AA:BB:" +
                // destination port 48042
                "BB:AA:" +
                // 4 extra TLV bytes. Not a valid TLV, but UNKNOWN handling logic shouldn't care
                "DE:AD:C0:DE"
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.UNKNOWN));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UDP));
        assertThat(data.sourceSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0)));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0)));
        assertThat(data.destAddress(), is(""));
        assertThat(data.destPort(), is(-1));
    }

    @Test
    void v2UnspecifiedTransportIgnoresAddress() throws IOException {
        var inputStream = new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "10:" + // family 1 = IPv4, protocol 0 = UNKNOWN
                "00:0C:" + // declared header length
                "CB:00:71:07:" + // opaque source address bytes
                "C6:33:64:09:" + // opaque destination address bytes
                "30:39:" + // opaque source port bytes
                "01:BB:" + // opaque destination port bytes
                "47")); // first application byte, ASCII 'G'

        var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(inputStream);
        SocketAddress unspecifiedAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0);
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
        assertThat(data.sourceSocketAddress(), is(unspecifiedAddress));
        assertThat(data.sourceAddress(), is(""));
        assertThat(data.sourcePort(), is(-1));
        assertThat(data.destSocketAddress(), is(unspecifiedAddress));
        assertThat(data.destAddress(), is(""));
        assertThat(data.destPort(), is(-1));
        assertThat(data.tlvs().size(), is(0));
        // The parser must consume only the declared PROXY header and leave application data untouched.
        assertThat(inputStream.read(), is((int) 'G'));
    }

    @Test
    void v2Ipv4UdpWithoutTlv() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "11:" + // family 1 = IPv4, protocol 1 = TCP
                "00:0C:" + // IPv4 family address base size = 12 = 0x0C
                // source address. IPv4 255.0.255.0
                "FF:00:FF:00:" +
                // destination address. IPv4 255.0.255.0
                "00:FF:00:FF:" +
                // source port 43707
                "AA:BB:" +
                // destination port 48042
                "BB:AA"
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 255, 0, (byte) 255, 0}), 43707)));
        assertThat(data.sourceAddress(), is("255.0.255.0"));
        assertThat(data.sourcePort(), is(43707));
        assertThat(data.destSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, (byte) 255, 0, (byte) 255}), 48042)));
        assertThat(data.destAddress(), is("0.255.0.255"));
        assertThat(data.destPort(), is(48042));
    }

    @Test
    void v2Ipv4UdpWithUnregisteredTlv() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "11:" + // family 1 = IPv4, protocol 1 = TCP
                "00:13:" + // IPv4 family address base size = 12 = 0x0C, plus 7 TLV bytes = 19 = 0x13
                // source address. IPv4 255.0.255.0
                "FF:00:FF:00:" +
                // destination address. IPv4 255.0.255.0
                "00:FF:00:FF:" +
                // source port 43707
                "AA:BB:" +
                // destination port 48042
                "BB:AA:" +
                // 0xE0 is defined as the minimum allowed custom TLV type value in the HAProxy spec
                "E0:" +
                // TLV length, 4 bytes
                "00:04:" +
                // TLV payload
                "00:01:02:03"
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 255, 0, (byte) 255, 0}), 43707)));
        assertThat(data.sourceAddress(), is("255.0.255.0"));
        assertThat(data.sourcePort(), is(43707));
        assertThat(data.destSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, (byte) 255, 0, (byte) 255}), 48042)));
        assertThat(data.destAddress(), is("0.255.0.255"));
        assertThat(data.destPort(), is(48042));
        assertThat(data.tlvs().size(), is(1));
        final var tlv = (ProxyProtocolV2Data.Tlv.Unregistered) data.tlvs().getFirst();
        assertThat(tlv.type(), is(0xE0));
        Assertions.assertArrayEquals(new byte[] {0, 1, 2, 3}, tlv.value());
    }

    @Test
    void v2UnknownFamilySkipAddressInsufficientTlvBytes() throws IOException {
        try {
            ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                    "21:" + // version 2, command 1 = PROXY
                    "02:" + // family 0 = UNKNOWN, protocol 2 = UDP
                    "00:10:" + // IPv4 family address base size = 12 = 0x0C, plus 4 extra bytes gives 16 = 0x10
                    // source address. IPv4 255.0.255.0:43707
                    "FF:00:FF:00:AA:BB:" +
                    // destination address. IPv4 0.255.0.255:48042
                    "FF:00:FF:00:BB:AA:" +
                    // Length indicates there should be 4 TLV bytes, but we only provide 2 here.
                    "DE:AD"
            )));
            Assertions.fail("Should have thrown");
        } catch (RequestException e) {
            assertThat(e.getMessage().contains("end of data stream reached before proxy protocol header was complete"), is(true));
        }
    }

    @Test
    void v2InsufficientTlvBytesInStream() throws IOException {
        try {
            ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                    "21:" + // version 2, command 1 = PROXY
                    "11:" + // family 1 = IPv4, protocol 1 = TCP
                    "00:13:" + // IPv4 family address base size = 12 = 0x0C, plus 7 TLV bytes = 19 = 0x13
                    // source address. IPv4 255.0.255.0
                    "FF:00:FF:00:" +
                    // destination address. IPv4 255.0.255.0
                    "00:FF:00:FF:" +
                    // source port 43707
                    "AA:BB:" +
                    // destination port 48042
                    "BB:AA:" +
                    // 0xE0 is defined as the minimum allowed custom TLV type value in the HAProxy spec
                    "E0"
                // Length and payload should be here, but are not.
            )));
            Assertions.fail("Should have thrown");
        } catch (RequestException e) {
            assertThat(e.getMessage().contains("end of data reached unexpectedly"), is(true));
        }
    }

    @Test
    void v2InsufficientTlvBytesInLength() throws IOException {
        try {
            ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                    "21:" + // version 2, command 1 = PROXY
                    "11:" + // family 1 = IPv4, protocol 1 = TCP
                    "00:0E:" + // IPv4 family address base size = 12 = 0x0C, plus 2 TLV bytes = 14 = 0x0E
                    // source address. IPv4 255.0.255.0
                    "FF:00:FF:00:" +
                    // destination address. IPv4 255.0.255.0
                    "00:FF:00:FF:" +
                    // source port 43707
                    "AA:BB:" +
                    // destination port 48042
                    "BB:AA:" +
                    // 0xE0 is defined as the minimum allowed custom TLV type value in the HAProxy spec
                    "E0:" +
                    // TLV length, 4 bytes
                    "00:04:" +
                    // TLV payload
                    "00:01:02:03"
            )));
            Assertions.fail("Should have thrown");
        } catch (RequestException e) {
            assertThat(e.getMessage().contains("insufficient remaining TLV bytes to read TLV type and length"), is(true));
        }
    }

    @Test
    void v2TlvClaimsLengthTooLong() throws IOException {
        try {
            ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                    "21:" + // version 2, command 1 = PROXY
                    "11:" + // family 1 = IPv4, protocol 1 = TCP
                    "00:13:" + // IPv4 family address base size = 12 = 0x0C, plus 7 TLV bytes = 19 = 0x13
                    // source address. IPv4 255.0.255.0
                    "FF:00:FF:00:" +
                    // destination address. IPv4 255.0.255.0
                    "00:FF:00:FF:" +
                    // source port 43707
                    "AA:BB:" +
                    // destination port 48042
                    "BB:AA:" +
                    // 0xE0 is defined as the minimum allowed custom TLV type value in the HAProxy spec
                    "E0:" +
                    // TLV length, claims 5 bytes even though there are only 4
                    "00:05:" +
                    // TLV payload
                    "00:01:02:03"
            )));
            Assertions.fail("Should have thrown");
        } catch (RequestException e) {
            assertThat(e.getMessage().contains("TLV length exceeds remaining available header bytes"), is(true));
        }
    }

    @Test
    void v2ShortSslTlv() {
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                        "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                        "21:" + // version 2, command 1 = PROXY
                        "11:" + // family 1 = IPv4, protocol 1 = TCP
                        "00:13:" + // IPv4 address bytes plus a four-byte SSL TLV value
                        "FF:00:FF:00:" +
                        "00:FF:00:FF:" +
                        "AA:BB:" +
                        "BB:AA:" +
                        "20:" + // PP2_TYPE_SSL
                        "00:04:" +
                        "01:00:00:00"))));
    }

    @Test
    void v2NestedSslTlv() throws IOException {
        var source = new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 255, 0, (byte) 255, 0}), 43707);
        var destination = new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {0, (byte) 255, 0, (byte) 255}), 48042);
        var nested = new ProxyProtocolV2Data.Tlv.Ssl(1, 0, List.of());
        var outer = new ProxyProtocolV2Data.Tlv.Ssl(1, 0, List.of(nested));
        var data = new ProxyProtocolHandler.ProxyProtocolV2DataImpl(ProxyProtocolData.Family.IPv4,
                                                                    ProxyProtocolData.Protocol.TCP,
                                                                    ProxyProtocolV2Data.Command.PROXY,
                                                                    source,
                                                                    destination,
                                                                    List.of(outer));
        V2Header header = makeHeader(data);

        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(header.bytes())));
    }

    @Test
    void v2NestedCrcTlv() throws IOException {
        var source = new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 255, 0, (byte) 255, 0}), 43707);
        var destination = new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {0, (byte) 255, 0, (byte) 255}), 48042);
        var nested = new ProxyProtocolV2Data.Tlv.Crc32c(0);
        var outer = new ProxyProtocolV2Data.Tlv.Ssl(1, 0, List.of(nested));
        var data = new ProxyProtocolHandler.ProxyProtocolV2DataImpl(ProxyProtocolData.Family.IPv4,
                                                                    ProxyProtocolData.Protocol.TCP,
                                                                    ProxyProtocolV2Data.Command.PROXY,
                                                                    source,
                                                                    destination,
                                                                    List.of(outer));
        V2Header header = makeHeader(data);

        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(header.bytes())));
    }

    @Test
    void v2InvalidCrcTlvLength() {
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                        "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                        "21:" + // version 2, command 1 = PROXY
                        "11:" + // family 1 = IPv4, protocol 1 = TCP
                        "00:12:" + // IPv4 address bytes plus a three-byte CRC32c TLV value
                        "FF:00:FF:00:" +
                        "00:FF:00:FF:" +
                        "AA:BB:" +
                        "BB:AA:" +
                        "03:" + // PP2_TYPE_CRC32C
                        "00:03:" +
                        "00:00:00"))));
    }

    @Test
    void v2SingleCrcTlv() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "11:" + // family 1 = IPv4, protocol 1 = TCP
                "00:13:" + // IPv4 family address base size = 12 = 0x0C, plus 7 TLV bytes = 19 = 0x13
                // source address. IPv4 255.0.255.0
                "FF:00:FF:00:" +
                // destination address. IPv4 255.0.255.0
                "00:FF:00:FF:" +
                // source port 43707
                "AA:BB:" +
                // destination port 48042
                "BB:AA:" +
                // 0x03 = PP2_TYPE_CRC32C
                "03:" +
                // TLV length, 4 bytes
                "00:04:" +
                // TLV payload
                "B5:2E:83:7B"
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 255, 0, (byte) 255, 0}), 43707)));
        assertThat(data.sourceAddress(), is("255.0.255.0"));
        assertThat(data.sourcePort(), is(43707));
        assertThat(data.destSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, (byte) 255, 0, (byte) 255}), 48042)));
        assertThat(data.destAddress(), is("0.255.0.255"));
        assertThat(data.destPort(), is(48042));
        assertThat(data.tlvs().size(), is(1));
        final var tlv = (ProxyProtocolV2Data.Tlv.Crc32c) data.tlvs().getFirst();
        assertThat(tlv.type(), is(ProxyProtocolV2Data.Tlv.PP2_TYPE_CRC32C));
        assertThat(tlv.checksum(), is(0xB52E837B));
    }

    @Test
    void v2IncorrectCrc() throws IOException {
        try {
            ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                    "21:" + // version 2, command 1 = PROXY
                    "11:" + // family 1 = IPv4, protocol 1 = TCP
                    "00:13:" + // IPv4 family address base size = 12 = 0x0C, plus 7 TLV bytes = 19 = 0x13
                    // source address. IPv4 255.0.255.0
                    "FF:00:FF:00:" +
                    // destination address. IPv4 255.0.255.0
                    "00:FF:00:FF:" +
                    // source port 43707
                    "AA:BB:" +
                    // destination port 48042
                    "BB:AA:" +
                    // 0x03 = PP2_TYPE_CRC32C
                    "03:" +
                    // TLV length, 4 bytes
                    "00:04:" +
                    // TLV payload. This is obviously the wrong CRC32 value.
                    "00:00:00:00"
            )));
            Assertions.fail("Should have thrown");
        } catch (RequestException e) {
            assertThat(e.getMessage().contains("proxy header checksum mismatch"), is(true));
        }
    }

    @Test
    void v2DoubleMatchingCrcTlvs() throws IOException {
        final var data = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
            "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                "21:" + // version 2, command 1 = PROXY
                "11:" + // family 1 = IPv4, protocol 1 = TCP
                "00:1A:" + // IPv4 family address base size = 12 = 0x0C, plus 7*2 TLV bytes = 26 = 0x1A
                // source address. IPv4 255.0.255.0
                "FF:00:FF:00:" +
                // destination address. IPv4 255.0.255.0
                "00:FF:00:FF:" +
                // source port 43707
                "AA:BB:" +
                // destination port 48042
                "BB:AA:" +
                // 0x03 = PP2_TYPE_CRC32C
                "03:" +
                // TLV length, 4 bytes
                "00:04:" +
                // TLV payload
                "C6:69:E9:4D:" +
                // 0x03 = PP2_TYPE_CRC32C
                "03:" +
                // TLV length, 4 bytes
                "00:04:" +
                // TLV payload
                "C6:69:E9:4D"
        )));
        assertThat(data.command(), is(ProxyProtocolV2Data.Command.PROXY));
        assertThat(data.family(), is(ProxyProtocolData.Family.IPv4));
        assertThat(data.protocol(), is(ProxyProtocolData.Protocol.TCP));
        assertThat(data.sourceSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {(byte) 255, 0, (byte) 255, 0}), 43707)));
        assertThat(data.sourceAddress(), is("255.0.255.0"));
        assertThat(data.sourcePort(), is(43707));
        assertThat(data.destSocketAddress(), is(new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, (byte) 255, 0, (byte) 255}), 48042)));
        assertThat(data.destAddress(), is("0.255.0.255"));
        assertThat(data.destPort(), is(48042));
        assertThat(data.tlvs().size(), is(2));
        for (var tlv : data.tlvs()) {
            assertThat(tlv.type(), is(ProxyProtocolV2Data.Tlv.PP2_TYPE_CRC32C));
            assertThat(((ProxyProtocolV2Data.Tlv.Crc32c) tlv).checksum(), is(0xC669E94D));
        }
    }

    @Test
    void v2DoubleMismatchingChecksums() throws IOException {
        try {
            ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(hexFormat.parseHex(
                "0D:0A:0D:0A:00:0D:0A:51:55:49:54:0A:" + // V2 prefix
                    "21:" + // version 2, command 1 = PROXY
                    "11:" + // family 1 = IPv4, protocol 1 = TCP
                    "00:1A:" + // IPv4 family address base size = 12 = 0x0C, plus 7*2 TLV bytes = 26 = 0x1A
                    // source address. IPv4 255.0.255.0
                    "FF:00:FF:00:" +
                    // destination address. IPv4 255.0.255.0
                    "00:FF:00:FF:" +
                    // source port 43707
                    "AA:BB:" +
                    // destination port 48042
                    "BB:AA:" +
                    // 0x03 = PP2_TYPE_CRC32C
                    "03:" +
                    // TLV length, 4 bytes
                    "00:04:" +
                    // TLV payload
                    "C6:69:E9:4D:" +
                    // 0x03 = PP2_TYPE_CRC32C
                    "03:" +
                    // TLV length, 4 bytes
                    "00:04:" +
                    // Different checksum
                    "AA:BB:CC:DD"
            )));
            Assertions.fail("Should have thrown");
        } catch (RequestException e) {
            assertThat(e.getMessage().contains("duplicate CRC32c checksum TLVs present with non-matching checksums"), is(true));
        }
    }

    @Test
    void validV2Permutations() throws IOException {
        final var tlvs = new ArrayList<ProxyProtocolV2Data.Tlv>();
        tlvs.add(new ProxyProtocolV2Data.Tlv.Alpn("alpn".getBytes(StandardCharsets.UTF_8)));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Alpn("".getBytes(StandardCharsets.UTF_8)));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Authority("authority"));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Authority(""));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Crc32c(0));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Noop(new byte[] {}));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Noop(new byte[] {0, 0, 0}));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Noop(new byte[] {1, 2}));
        tlvs.add(new ProxyProtocolV2Data.Tlv.UniqueId("uniqueId".getBytes(StandardCharsets.UTF_8)));
        tlvs.add(new ProxyProtocolV2Data.Tlv.UniqueId("".getBytes(StandardCharsets.UTF_8)));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Netns("namespace"));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Netns(""));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Unregistered(0xE0, new byte[0]));
        tlvs.add(new ProxyProtocolV2Data.Tlv.Unregistered(0xE1, new byte[] { 0, 1, 2, 3, 4 }));
        final var rand = new Random(149813745327L);
        byte[] randomBytes;

        for (var family : ProxyProtocolData.Family.values()) {
            SocketAddress src = null;
            SocketAddress dst = null;
            switch (family) {
                case IPv4 -> {
                    randomBytes = new byte[4];
                    rand.nextBytes(randomBytes);
                    var srcAddr = InetAddress.getByAddress(randomBytes);

                    randomBytes = new byte[2];
                    rand.nextBytes(randomBytes);
                    int srcPort = (randomBytes[0] & 0xFF) << 8 | (randomBytes[1] & 0xFF);

                    randomBytes = new byte[4];
                    rand.nextBytes(randomBytes);
                    var dstAddr = InetAddress.getByAddress(randomBytes);

                    randomBytes = new byte[2];
                    rand.nextBytes(randomBytes);
                    int dstPort = (randomBytes[0] & 0xFF) << 8 | (randomBytes[1] & 0xFF);

                    src = new InetSocketAddress(srcAddr, srcPort);
                    dst = new InetSocketAddress(dstAddr, dstPort);
                }
                case IPv6 -> {
                    randomBytes = new byte[16];
                    rand.nextBytes(randomBytes);
                    var srcAddr = InetAddress.getByAddress(randomBytes);

                    randomBytes = new byte[2];
                    rand.nextBytes(randomBytes);
                    int srcPort = (randomBytes[0] & 0xFF) << 8 | (randomBytes[1] & 0xFF);

                    randomBytes = new byte[16];
                    rand.nextBytes(randomBytes);
                    var dstAddr = InetAddress.getByAddress(randomBytes);

                    randomBytes = new byte[2];
                    rand.nextBytes(randomBytes);
                    int dstPort = (randomBytes[0] & 0xFF) << 8 | (randomBytes[1] & 0xFF);

                    src = new InetSocketAddress(srcAddr, srcPort);
                    dst = new InetSocketAddress(dstAddr, dstPort);
                }
                case UNIX -> {
                    src = UnixDomainSocketAddress.of("/foo/bar/baz");
                    dst = UnixDomainSocketAddress.of("abcdefg");
                }
            }

            for (var protocol : ProxyProtocolData.Protocol.values()) {
                for (var command : ProxyProtocolV2Data.Command.values()) {
                    Collections.shuffle(tlvs, rand);
                    assertTlvPermutation(family, protocol, command, src, dst, tlvs);
                    assertTlvPermutation(family, protocol, command, src, dst, List.of());
                }
            }
        }
    }

    private void assertTlvPermutation(
            final ProxyProtocolData.Family family,
            final ProxyProtocolData.Protocol protocol,
            final ProxyProtocolV2Data.Command command,
            final SocketAddress src,
            final SocketAddress dst,
            final List<ProxyProtocolV2Data.Tlv> tlvs) throws IOException {
        final var data = new ProxyProtocolHandler.ProxyProtocolV2DataImpl(family, protocol, command, src, dst, tlvs);
        V2Header header = makeHeader(data);
        var output = (ProxyProtocolV2Data) ProxyProtocolHandler.handleAnyProtocol(new ByteArrayInputStream(header.bytes));

        if (command == ProxyProtocolV2Data.Command.LOCAL) {
            SocketAddress unspecifiedAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0);
            assertThat(output.protocol(), is(ProxyProtocolData.Protocol.UNKNOWN));
            assertThat(output.family(), is(ProxyProtocolData.Family.UNKNOWN));
            assertThat(output.command(), is(ProxyProtocolV2Data.Command.LOCAL));
            assertThat(output.sourceSocketAddress(), is(unspecifiedAddress));
            assertThat(output.sourceAddress(), is(""));
            assertThat(output.sourcePort(), is(-1));
            assertThat(output.destSocketAddress(), is(unspecifiedAddress));
            assertThat(output.destAddress(), is(""));
            assertThat(output.destPort(), is(-1));
            assertThat(output.tlvs().size(), is(0));
            return;
        }

        assertThat(output.protocol(), is(data.protocol()));
        assertThat(output.family(), is(data.family()));
        assertThat(output.command(), is(data.command()));

        if (ProxyProtocolData.Family.UNKNOWN.equals(family)
                || ProxyProtocolData.Protocol.UNKNOWN.equals(protocol)) {
            SocketAddress unspecifiedAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0);
            // UNSPEC family or transport bytes are opaque and must not become trusted endpoint or TLV data.
            assertThat(output.sourceSocketAddress(), is(unspecifiedAddress));
            assertThat(output.sourceAddress(), is(""));
            assertThat(output.sourcePort(), is(-1));
            assertThat(output.destSocketAddress(), is(unspecifiedAddress));
            assertThat(output.destAddress(), is(""));
            assertThat(output.destPort(), is(-1));
            assertThat(output.tlvs().size(), is(0));
            return;
        }

        assertThat(output.sourceSocketAddress(), is(data.sourceSocketAddress()));
        assertThat(output.destSocketAddress(), is(data.destSocketAddress()));
        assertThat(output.sourceAddress(), is(data.sourceAddress()));
        assertThat(output.sourcePort(), is(data.sourcePort()));
        assertThat(output.destAddress(), is(data.destAddress()));
        assertThat(output.destPort(), is(data.destPort()));

        assertThat(output.tlvs().size(), is(data.tlvs().size()));

        for (int i = 0; i < data.tlvs().size(); i++) {
            final var inputTlv = data.tlvs().get(i);
            final var outputTlv = output.tlvs().get(i);

            if (inputTlv instanceof ProxyProtocolV2Data.Tlv.Crc32c && outputTlv instanceof ProxyProtocolV2Data.Tlv.Crc32c outputCrc) {
                assertThat(outputCrc.checksum(), is(header.checksum));
            } else {
                assertThat(outputTlv, is(inputTlv));
            }
        }
    }

    private static void assertBadV1(String header) {
        assertThrows(RequestException.class, () ->
                ProxyProtocolHandler.handleAnyProtocol(
                        new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII))));
    }

    record V2Header(byte[] bytes, int checksum) {}

    V2Header makeHeader(ProxyProtocolHandler.ProxyProtocolV2DataImpl data) {
        try {
            final var baos = new ByteArrayOutputStream();
            final var dataStream = new DataOutputStream(baos);
            baos.writeBytes(ProxyProtocolHandler.V2_PREFIX_1);
            baos.writeBytes(ProxyProtocolHandler.V2_PREFIX_2);
            baos.write((0x02 << 4) | switch (data.command()) {
                case LOCAL -> 0x0;
                case PROXY -> 0x1;
            });
            baos.write((switch (data.family()) {
                case UNKNOWN -> 0x0;
                case IPv4 -> 0x1;
                case IPv6 -> 0x2;
                case UNIX -> 0x3;
            } << 4) | switch (data.protocol()) {
                case UNKNOWN -> 0x0;
                case TCP -> 0x1;
                case UDP -> 0x2;
            });
            // We'll need to come back and write the length once we know it.
            baos.write(0);
            baos.write(0);
            int _exhaustive = switch (data.family()) {
                case UNKNOWN -> 0;
                case IPv4, IPv6 -> {
                    baos.writeBytes(((InetSocketAddress) data.sourceSocketAddress()).getAddress().getAddress());
                    baos.writeBytes(((InetSocketAddress) data.destSocketAddress()).getAddress().getAddress());
                    dataStream.writeShort(((InetSocketAddress) data.sourceSocketAddress()).getPort());
                    dataStream.writeShort(((InetSocketAddress) data.destSocketAddress()).getPort());
                    dataStream.flush();
                    yield 0;
                }
                case UNIX -> {
                    byte[] srcBytes = ((UnixDomainSocketAddress) data.sourceSocketAddress()).getPath().toString().getBytes(StandardCharsets.UTF_8);
                    byte[] fullSrcBytes = new byte[108];
                    System.arraycopy(srcBytes, 0, fullSrcBytes, 0, srcBytes.length);
                    baos.writeBytes(fullSrcBytes);
                    byte[] dstBytes = ((UnixDomainSocketAddress) data.destSocketAddress()).getPath().toString().getBytes(StandardCharsets.UTF_8);
                    byte[] fullDstBytes = new byte[108];
                    System.arraycopy(dstBytes, 0, fullDstBytes, 0, dstBytes.length);
                    baos.writeBytes(fullDstBytes);
                    yield 0;
                }
            };
            final var checksumOffsets = new ArrayList<Integer>();
            for (var tlv : data.tlvs()) {
                baos.write(tlv.type());
                byte[] tlvValue = writeTlvValue(tlv, checksumOffsets, baos.size() + 2);
                dataStream.writeShort(tlvValue.length);
                dataStream.flush();
                baos.writeBytes(tlvValue);
            }

            // Write the length now that we know it.
            byte[] header = baos.toByteArray();
            int addressLength = header.length - 16;
            if (addressLength > 0xFFFF) {
                throw new IllegalArgumentException("Header too big");
            }
            header[14] = (byte) ((addressLength >> 8) & 0xFF);
            header[15] = (byte) (addressLength & 0xFF);

            // Calculate and write the checksum if necessary
            Checksum crc = new CRC32C();
            crc.update(header);
            int checksum = (int) crc.getValue();
            byte[] checksumBytes = new byte[4];
            checksumBytes[0] = (byte) ((checksum >> 24) & 0xFF);
            checksumBytes[1] = (byte) ((checksum >> 16) & 0xFF);
            checksumBytes[2] = (byte) ((checksum >> 8) & 0xFF);
            checksumBytes[3] = (byte) (checksum & 0xFF);
            for (int offset : checksumOffsets) {
                System.arraycopy(checksumBytes, 0, header, offset, checksumBytes.length);
            }

            return new V2Header(header, checksum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    byte[] writeTlvValue(ProxyProtocolV2Data.Tlv tlv, List<Integer> checksumOffsets, int currentOffset) throws IOException {
        return switch (tlv) {
            case ProxyProtocolV2Data.Tlv.Alpn alpn -> alpn.protocol();
            case ProxyProtocolV2Data.Tlv.Authority authority -> authority.hostName().getBytes(StandardCharsets.UTF_8);
            case ProxyProtocolV2Data.Tlv.Crc32c crc32c -> {
                checksumOffsets.add(currentOffset);
                yield new byte[] {0, 0, 0, 0};
            }
            case ProxyProtocolV2Data.Tlv.Netns netns -> netns.namespaceName().getBytes(StandardCharsets.US_ASCII);
            case ProxyProtocolV2Data.Tlv.Noop noop -> noop.bytes();
            case ProxyProtocolV2Data.Tlv.Ssl ssl -> {
                var baos = new ByteArrayOutputStream();
                var dataStream = new DataOutputStream(baos);
                baos.write(ssl.client());
                dataStream.writeInt(ssl.verify());
                dataStream.flush();
                for (var subTlv : ssl.subTlvs()) {
                    baos.write(subTlv.type());
                    byte[] tlvValue = writeTlvValue(subTlv, new ArrayList<>(), 0);
                    dataStream.writeShort(tlvValue.length);
                    dataStream.flush();
                    baos.writeBytes(tlvValue);
                }
                yield baos.toByteArray();
            }
            case ProxyProtocolV2Data.Tlv.SslCipher sslCipher -> sslCipher.cipher().getBytes(StandardCharsets.US_ASCII);
            case ProxyProtocolV2Data.Tlv.SslCn sslCn -> sslCn.commonName().getBytes(StandardCharsets.UTF_8);
            case ProxyProtocolV2Data.Tlv.SslKeyAlg sslKeyAlg -> sslKeyAlg.keyAlgorithm().getBytes(StandardCharsets.US_ASCII);
            case ProxyProtocolV2Data.Tlv.SslSigAlg sslSigAlg -> sslSigAlg.signatureAlgorithm().getBytes(StandardCharsets.US_ASCII);
            case ProxyProtocolV2Data.Tlv.SslVersion sslVersion -> sslVersion.version().getBytes(StandardCharsets.US_ASCII);
            case ProxyProtocolV2Data.Tlv.UniqueId uniqueId -> uniqueId.id();
            case ProxyProtocolV2Data.Tlv.Unregistered unregistered -> unregistered.value();
        };
    }
}
