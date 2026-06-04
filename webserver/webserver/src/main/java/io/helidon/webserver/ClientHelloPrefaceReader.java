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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.uri.UriHost;

/**
 * Reads the TLS ClientHello bytes that appear before the selected TLS engine owns the connection.
 * <p>
 * WebServer uses this only for listener SNI virtual hosts: it captures the bytes that must be replayed into the TLS
 * engine after host selection, and extracts a normalized DNS SNI host without completing or otherwise interpreting TLS.
 */
final class ClientHelloPrefaceReader {
    private static final int MAX_PREFACE_BYTES = 64 * 1024;
    private static final int MAX_TLS_PLAINTEXT_RECORD_BYTES = 16 * 1024;
    private static final int TLS_HANDSHAKE_CONTENT_TYPE = 22;
    private static final int CLIENT_HELLO_HANDSHAKE_TYPE = 1;
    private static final int SNI_EXTENSION_TYPE = 0;
    private static final int SNI_HOST_NAME_TYPE = 0;
    private static final ReadWaiter BLOCKING_READ_WAITER = () -> { };

    private ClientHelloPrefaceReader() {
    }

    static ClientHelloPreface read(ReadableByteChannel channel) throws IOException {
        return read(channel, BLOCKING_READ_WAITER);
    }

    static ClientHelloPreface read(SocketChannel channel, Duration timeout) throws IOException {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(timeout);

        boolean blocking = channel.isBlocking();
        try (Selector selector = Selector.open()) {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            return read(channel, new DeadlineWaiter(selector, timeout));
        } finally {
            if (channel.isOpen()) {
                channel.configureBlocking(blocking);
            }
        }
    }

    private static ClientHelloPreface read(ReadableByteChannel channel, ReadWaiter readWaiter) throws IOException {
        ByteBuffer replay = ByteBuffer.allocate(512);
        ByteBuffer handshake = ByteBuffer.allocate(512);
        int requiredHandshakeBytes = -1;

        while (requiredHandshakeBytes == -1 || handshake.position() < requiredHandshakeBytes) {
            int recordStart = replay.position();
            replay = readFully(channel, replay, 5, readWaiter);

            int contentType = unsigned(replay.get(recordStart));
            if (contentType != TLS_HANDSHAKE_CONTENT_TYPE) {
                throw new IllegalArgumentException("TLS ClientHello expected a handshake record");
            }

            int recordLength = unsignedShort(replay, recordStart + 3);
            if (recordLength == 0) {
                throw new IllegalArgumentException("TLS ClientHello record cannot be empty");
            }
            if (recordLength > MAX_TLS_PLAINTEXT_RECORD_BYTES) {
                throw new IllegalArgumentException("TLS ClientHello record is too large");
            }
            replay = readFully(channel, replay, recordLength, readWaiter);

            ByteBuffer recordBody = replay.duplicate();
            recordBody.position(recordStart + 5);
            recordBody.limit(recordStart + 5 + recordLength);
            handshake = ensureRemaining(handshake, recordLength);
            handshake.put(recordBody);

            if (handshake.position() >= 4 && requiredHandshakeBytes == -1) {
                if (unsigned(handshake.get(0)) != CLIENT_HELLO_HANDSHAKE_TYPE) {
                    throw new IllegalArgumentException("TLS ClientHello expected a client_hello handshake message");
                }
                requiredHandshakeBytes = 4 + unsignedMedium(handshake, 1);
                if (requiredHandshakeBytes > MAX_PREFACE_BYTES) {
                    throw new IllegalArgumentException("TLS ClientHello is too large");
                }
            }
        }

        replay.flip();
        handshake.flip();
        ByteBuffer clientHello = handshake.slice();
        clientHello.limit(requiredHandshakeBytes);
        return new ClientHelloPreface(replay.slice(), sniHost(clientHello));
    }

    private static ByteBuffer readFully(ReadableByteChannel channel,
                                        ByteBuffer buffer,
                                        int bytes,
                                        ReadWaiter readWaiter) throws IOException {
        buffer = ensureRemaining(buffer, bytes);
        int target = buffer.position() + bytes;
        int oldLimit = buffer.limit();
        try {
            buffer.limit(target);
            while (buffer.position() < target) {
                int read = channel.read(buffer);
                if (read == -1) {
                    throw new IllegalArgumentException("TLS ClientHello input ended before the preface was complete");
                }
                if (read == 0) {
                    readWaiter.awaitReadable();
                }
            }
        } finally {
            buffer.limit(oldLimit);
        }
        return buffer;
    }

    private static Optional<String> sniHost(ByteBuffer clientHello) {
        int end = 4 + unsignedMedium(clientHello, 1);
        require(clientHello, end);

        int position = 4;
        position += 2; // legacy_version
        position += 32; // random
        require(clientHello, position + 1);
        position += 1 + unsigned(clientHello.get(position)); // session_id
        require(clientHello, position + 2);
        position += 2 + unsignedShort(clientHello, position); // cipher_suites
        require(clientHello, position + 1);
        position += 1 + unsigned(clientHello.get(position)); // compression_methods

        if (position == end) {
            return Optional.empty();
        }

        require(clientHello, position + 2);
        int extensionsLength = unsignedShort(clientHello, position);
        position += 2;
        int extensionsEnd = position + extensionsLength;
        require(clientHello, extensionsEnd);
        if (extensionsEnd > end) {
            throw new IllegalArgumentException("TLS ClientHello extensions exceed the handshake message");
        }

        Optional<String> sniHost = Optional.empty();
        boolean sniExtensionSeen = false;
        while (position < extensionsEnd) {
            require(clientHello, position + 4);
            int type = unsignedShort(clientHello, position);
            int length = unsignedShort(clientHello, position + 2);
            position += 4;
            int extensionEnd = position + length;
            require(clientHello, extensionEnd);
            if (extensionEnd > extensionsEnd) {
                throw new IllegalArgumentException("TLS ClientHello extension exceeds the extensions block");
            }
            if (type == SNI_EXTENSION_TYPE) {
                if (sniExtensionSeen) {
                    throw new IllegalArgumentException("TLS ClientHello SNI extension cannot be duplicated");
                }
                sniExtensionSeen = true;
                sniHost = sniExtension(clientHello, position, extensionEnd);
            }
            position = extensionEnd;
        }
        if (position != extensionsEnd) {
            throw new IllegalArgumentException("TLS ClientHello extensions length is invalid");
        }
        return sniHost;
    }

    private static Optional<String> sniExtension(ByteBuffer clientHello, int position, int extensionEnd) {
        require(clientHello, position + 2);
        int listLength = unsignedShort(clientHello, position);
        position += 2;
        if (listLength == 0) {
            throw new IllegalArgumentException("TLS ClientHello SNI server name list cannot be empty");
        }
        int listEnd = position + listLength;
        require(clientHello, listEnd);
        if (listEnd != extensionEnd) {
            throw new IllegalArgumentException("TLS ClientHello SNI extension length is invalid");
        }

        String host = null;
        while (position < listEnd) {
            require(clientHello, position + 3);
            int nameType = unsigned(clientHello.get(position));
            int nameLength = unsignedShort(clientHello, position + 1);
            position += 3;
            int nameEnd = position + nameLength;
            require(clientHello, nameEnd);
            if (nameEnd > listEnd) {
                throw new IllegalArgumentException("TLS ClientHello SNI server name exceeds the server name list");
            }
            if (nameType == SNI_HOST_NAME_TYPE) {
                if (host != null) {
                    throw new IllegalArgumentException("TLS ClientHello SNI host name cannot be duplicated");
                }
                if (nameLength == 0) {
                    throw new IllegalArgumentException("TLS ClientHello SNI host name cannot be empty");
                }
                byte[] bytes = new byte[nameLength];
                ByteBuffer duplicate = clientHello.duplicate();
                duplicate.position(position);
                duplicate.get(bytes);
                String asciiHost = asciiString(bytes);
                if (asciiHost.endsWith(".")) {
                    throw new IllegalArgumentException("TLS ClientHello SNI host name must not end with a dot");
                }
                UriHost uriHost = UriHost.create(asciiHost);
                if (uriHost.kind() != UriHost.Kind.DNS) {
                    throw new IllegalArgumentException("TLS ClientHello SNI host name must be a DNS name");
                }
                host = uriHost.value();
            }
            position = nameEnd;
        }
        return Optional.ofNullable(host);
    }

    private static String asciiString(byte[] bytes) {
        try {
            return StandardCharsets.US_ASCII.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("TLS ClientHello SNI host name must be ASCII", e);
        }
    }

    private static ByteBuffer ensureRemaining(ByteBuffer buffer, int additional) {
        if (additional > MAX_PREFACE_BYTES || buffer.position() > MAX_PREFACE_BYTES - additional) {
            throw new IllegalArgumentException("TLS ClientHello is too large");
        }
        if (buffer.remaining() >= additional) {
            return buffer;
        }
        int needed = buffer.position() + additional;
        int capacity = buffer.capacity();
        while (capacity < needed) {
            capacity = Math.min(MAX_PREFACE_BYTES, capacity * 2);
            if (capacity < needed && capacity == MAX_PREFACE_BYTES) {
                throw new IllegalArgumentException("TLS ClientHello is too large");
            }
        }
        ByteBuffer expanded = ByteBuffer.allocate(capacity);
        buffer.flip();
        expanded.put(buffer);
        return expanded;
    }

    private static void require(ByteBuffer buffer, int needed) {
        if (needed > buffer.limit()) {
            throw new IllegalArgumentException("TLS ClientHello is malformed");
        }
    }

    private static int unsigned(ByteBuffer buffer, int index) {
        return unsigned(buffer.get(index));
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private static int unsignedShort(ByteBuffer buffer, int index) {
        return (unsigned(buffer, index) << 8) | unsigned(buffer, index + 1);
    }

    private static int unsignedMedium(ByteBuffer buffer, int index) {
        return (unsigned(buffer, index) << 16) | (unsigned(buffer, index + 1) << 8) | unsigned(buffer, index + 2);
    }

    /**
     * ClientHello bytes consumed before TLS engine selection.
     *
     * @param replayBuffer bytes to replay into the selected TLS engine
     * @param sniHost normalized DNS SNI host from the ClientHello, if one was present
     */
    record ClientHelloPreface(ByteBuffer replayBuffer, Optional<String> sniHost) {
    }

    @FunctionalInterface
    private interface ReadWaiter {
        void awaitReadable() throws IOException;
    }

    private static final class DeadlineWaiter implements ReadWaiter {
        private static final long NANOS_PER_MILLI = 1_000_000;

        private final Selector selector;
        private final long deadlineNanos;

        private DeadlineWaiter(Selector selector, Duration timeout) {
            this.selector = selector;
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        @Override
        public void awaitReadable() throws IOException {
            while (true) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new IllegalArgumentException("TLS ClientHello input timed out before the preface was complete");
                }
                long timeoutMillis = Math.max(1, (remainingNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI);
                int selected = selector.select(timeoutMillis);
                selector.selectedKeys().clear();
                if (selected != 0) {
                    return;
                }
            }
        }
    }
}
