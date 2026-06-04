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

package io.helidon.webserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientHelloPrefaceReaderTest {
    private static final String DNS_LABEL_63 = "a".repeat(63);
    private static final String DNS_LABEL_64 = "a".repeat(64);
    private static final String LONGEST_DNS_NAME = DNS_LABEL_63 + "."
            + DNS_LABEL_63 + "."
            + DNS_LABEL_63 + "."
            + "a".repeat(61);
    private static final String TOO_LONG_DNS_NAME = DNS_LABEL_63 + "."
            + DNS_LABEL_63 + "."
            + DNS_LABEL_63 + "."
            + "a".repeat(62);

    @Test
    void readsSniFromSingleRecordClientHello() throws IOException {
        byte[] clientHello = clientHello("Api.Example.COM");

        ClientHelloPrefaceReader.ClientHelloPreface preface = read(record(clientHello));

        assertThat(preface.sniHost(), is(Optional.of("api.example.com")));
        assertThat(preface.replayBuffer().remaining(), is(record(clientHello).length));
    }

    @Test
    void readsShortestSniHostName() throws IOException {
        byte[] clientHello = clientHello("a");

        ClientHelloPrefaceReader.ClientHelloPreface preface = read(record(clientHello));

        assertThat(preface.sniHost(), is(Optional.of("a")));
    }

    @Test
    void readsLongestSniHostName() throws IOException {
        byte[] clientHello = clientHello(LONGEST_DNS_NAME);

        ClientHelloPrefaceReader.ClientHelloPreface preface = read(record(clientHello));

        assertThat(preface.sniHost(), is(Optional.of(LONGEST_DNS_NAME)));
    }

    @Test
    void readsSniFromFragmentedClientHello() throws IOException {
        byte[] clientHello = clientHello("api.example.com");
        byte[] records = concat(record(clientHello, 0, 11), record(clientHello, 11, clientHello.length - 11));

        ClientHelloPrefaceReader.ClientHelloPreface preface = read(records);

        assertThat(preface.sniHost(), is(Optional.of("api.example.com")));
        assertThat(preface.replayBuffer().remaining(), is(records.length));
    }

    @Test
    void returnsEmptyWhenClientHelloHasNoSniExtension() throws IOException {
        ClientHelloPrefaceReader.ClientHelloPreface preface = read(record(clientHelloWithoutSni()));

        assertThat(preface.sniHost(), is(Optional.empty()));
    }

    @Test
    void rejectsNonHandshakeRecord() {
        byte[] record = record(23, new byte[] {1, 2, 3});

        assertThrows(IllegalArgumentException.class, () -> read(record));
    }

    @Test
    void rejectsTruncatedRecord() {
        byte[] record = new byte[] {22, 3, 3, 0, 4, 1, 0};

        assertThrows(IllegalArgumentException.class, () -> read(record));
    }

    @Test
    @Timeout(10)
    void timesOutPartialSocketChannelPreface() throws Exception {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            InetSocketAddress address = (InetSocketAddress) server.getLocalAddress();
            try (SocketChannel client = SocketChannel.open(address);
                    SocketChannel accepted = server.accept()) {
                client.write(ByteBuffer.wrap(new byte[] {22, 3, 3, 0, 4, 1, 0}));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> ClientHelloPrefaceReader.read(accepted, Duration.ofMillis(100)));

                assertThat(exception.getMessage(), containsString("timed out"));
                assertThat(accepted.isBlocking(), is(true));
            }
        }
    }

    @Test
    void rejectsTooLargeClientHello() {
        byte[] handshake = handshakeHeaderOnly(64 * 1024);

        assertThrows(IllegalArgumentException.class, () -> read(record(handshake)));
    }

    @Test
    void rejectsTooLargeTlsRecord() {
        byte[] record = record(22, new byte[16 * 1024 + 1]);

        assertThrows(IllegalArgumentException.class, () -> read(record));
    }

    @Test
    void rejectsMalformedExtensionsLength() {
        ByteArrayOutputStream body = baseClientHelloBody();
        writeShort(body, 8);
        writeShort(body, 0);
        writeShort(body, 0);

        assertThrows(IllegalArgumentException.class, () -> read(record(handshake(body.toByteArray()))));
    }

    @Test
    void rejectsInvalidSniServerNameListLength() {
        byte[] extension = sniExtensionWithListLength(1, new byte[] {0});

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHelloWithExtension(extension))));
    }

    @Test
    void rejectsEmptySniServerNameList() {
        byte[] extension = sniExtensionWithListLength(0, new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHelloWithExtension(extension))));
    }

    @Test
    void rejectsEmptySniHostName() {
        byte[] extension = sniExtensionWithListLength(3, new byte[] {0, 0, 0});

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHelloWithExtension(extension))));
    }

    @Test
    void rejectsDuplicateSniHostName() {
        ByteArrayOutputStream serverNameList = new ByteArrayOutputStream();
        writeServerName(serverNameList, "api.example.com");
        writeServerName(serverNameList, "admin.example.com");
        byte[] extension = sniExtensionWithListLength(serverNameList.size(), serverNameList.toByteArray());

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHelloWithExtension(extension))));
    }

    @Test
    void rejectsMalformedTrailingSniServerName() {
        ByteArrayOutputStream serverNameList = new ByteArrayOutputStream();
        writeServerName(serverNameList, "api.example.com");
        serverNameList.write(0);
        byte[] extension = sniExtensionWithListLength(serverNameList.size(), serverNameList.toByteArray());

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHelloWithExtension(extension))));
    }

    @Test
    void rejectsDuplicateSniExtension() {
        byte[] extension = sniExtension("api.example.com");

        assertThrows(IllegalArgumentException.class,
                     () -> read(record(clientHelloWithExtensions(extension, extension))));
    }

    @Test
    void rejectsMalformedExtensionAfterSni() {
        byte[] extension = sniExtension("api.example.com");
        byte[] malformedExtensionHeader = new byte[] {0, 1, 0};

        assertThrows(IllegalArgumentException.class,
                     () -> read(record(clientHelloWithExtensions(extension, malformedExtensionHeader))));
    }

    @Test
    void rejectsTrailingDotSniHostName() {
        byte[] clientHello = clientHello("api.example.com.");

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHello)));
    }

    @Test
    void rejectsTooLongSniHostName() {
        byte[] clientHello = clientHello(TOO_LONG_DNS_NAME);

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHello)));
    }

    @Test
    void rejectsTooLongSniHostNameLabel() {
        byte[] clientHello = clientHello(DNS_LABEL_64);

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHello)));
    }

    @Test
    void rejectsIpLiteralSniHostName() {
        byte[] clientHello = clientHello("127.0.0.1");

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHello)));
    }

    @Test
    void rejectsNonAsciiSniHostName() {
        byte[] clientHello = clientHello(new byte[] {'a', (byte) 0xFF});

        assertThrows(IllegalArgumentException.class, () -> read(record(clientHello)));
    }

    private static ClientHelloPrefaceReader.ClientHelloPreface read(byte[] bytes) throws IOException {
        return ClientHelloPrefaceReader.read(Channels.newChannel(new ByteArrayInputStream(bytes)));
    }

    private static byte[] clientHello(String host) {
        return clientHello(host.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] clientHello(byte[] hostBytes) {
        return clientHelloWithExtensions(sniExtension(hostBytes));
    }

    private static byte[] sniExtension(String host) {
        return sniExtension(host.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] sniExtension(byte[] hostBytes) {
        ByteArrayOutputStream sni = new ByteArrayOutputStream();
        writeShort(sni, 3 + hostBytes.length);
        writeServerName(sni, hostBytes);

        ByteArrayOutputStream extension = new ByteArrayOutputStream();
        writeShort(extension, 0);
        writeShort(extension, sni.size());
        extension.writeBytes(sni.toByteArray());
        return extension.toByteArray();
    }

    private static void writeServerName(ByteArrayOutputStream stream, String host) {
        writeServerName(stream, host.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeServerName(ByteArrayOutputStream stream, byte[] hostBytes) {
        stream.write(0);
        writeShort(stream, hostBytes.length);
        stream.writeBytes(hostBytes);
    }

    private static byte[] clientHelloWithoutSni() {
        ByteArrayOutputStream body = baseClientHelloBody();
        writeShort(body, 0);
        return handshake(body.toByteArray());
    }

    private static byte[] clientHelloWithExtension(byte[] extension) {
        return clientHelloWithExtensions(extension);
    }

    private static byte[] clientHelloWithExtensions(byte[]... extensions) {
        ByteArrayOutputStream body = baseClientHelloBody();
        int extensionsLength = 0;
        for (byte[] extension : extensions) {
            extensionsLength += extension.length;
        }
        writeShort(body, extensionsLength);
        for (byte[] extension : extensions) {
            body.writeBytes(extension);
        }
        return handshake(body.toByteArray());
    }

    private static byte[] sniExtensionWithListLength(int listLength, byte[] serverNameList) {
        ByteArrayOutputStream sni = new ByteArrayOutputStream();
        writeShort(sni, listLength);
        sni.writeBytes(serverNameList);

        ByteArrayOutputStream extension = new ByteArrayOutputStream();
        writeShort(extension, 0);
        writeShort(extension, sni.size());
        extension.writeBytes(sni.toByteArray());
        return extension.toByteArray();
    }

    private static ByteArrayOutputStream baseClientHelloBody() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0x03);
        body.write(0x03);
        body.writeBytes(new byte[32]);
        body.write(0);
        writeShort(body, 2);
        body.write(0x13);
        body.write(0x01);
        body.write(1);
        body.write(0);
        return body;
    }

    private static byte[] handshake(byte[] body) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(1);
        writeMedium(result, body.length);
        result.writeBytes(body);
        return result.toByteArray();
    }

    private static byte[] handshakeHeaderOnly(int bodyLength) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(1);
        writeMedium(result, bodyLength);
        return result.toByteArray();
    }

    private static byte[] record(byte[] handshake) {
        return record(handshake, 0, handshake.length);
    }

    private static byte[] record(byte[] handshake, int offset, int length) {
        byte[] fragment = new byte[length];
        System.arraycopy(handshake, offset, fragment, 0, length);
        return record(22, fragment);
    }

    private static byte[] record(int type, byte[] data) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(type);
        result.write(0x03);
        result.write(0x03);
        writeShort(result, data.length);
        result.writeBytes(data);
        return result.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.writeBytes(first);
        result.writeBytes(second);
        return result.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream stream, int value) {
        stream.write((value >>> 8) & 0xFF);
        stream.write(value & 0xFF);
    }

    private static void writeMedium(ByteArrayOutputStream stream, int value) {
        stream.write((value >>> 16) & 0xFF);
        stream.write((value >>> 8) & 0xFF);
        stream.write(value & 0xFF);
    }
}
