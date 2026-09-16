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

package io.helidon.quic;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

final class QuicAddressTokenService implements AutoCloseable {
    static final int MAX_TOKEN_SIZE = 256;
    static final Duration RETRY_TOKEN_LIFETIME = Duration.ofSeconds(10);
    static final Duration NEW_TOKEN_LIFETIME = Duration.ofHours(1);
    static final Duration KEY_ROTATION_INTERVAL = NEW_TOKEN_LIFETIME;

    private static final byte FORMAT_VERSION = 1;
    private static final int KEY_ID_SIZE = Integer.BYTES;
    private static final int NONCE_PREFIX_SIZE = Integer.BYTES;
    private static final int NONCE_SIZE = 12;
    private static final int HEADER_SIZE = 2 + KEY_ID_SIZE + NONCE_SIZE;
    private static final int GCM_TAG_SIZE = 16;
    private static final int GCM_TAG_BITS = GCM_TAG_SIZE * Byte.SIZE;
    private static final int KEY_SIZE = 32;
    private static final int MAX_ADDRESS_SIZE = 16;

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final AtomicReference<KeyRing> keyRing;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long retryLifetimeNanos;
    private final long newTokenLifetimeNanos;
    private final long rotationIntervalNanos;
    private final LongSupplier nanoTime;
    private final SecureRandom random;

    private QuicAddressTokenService(Duration retryLifetime,
                                    Duration newTokenLifetime,
                                    Duration rotationInterval,
                                    LongSupplier nanoTime,
                                    SecureRandom random) {
        this.retryLifetimeNanos = positiveNanos(retryLifetime, "retry token lifetime");
        this.newTokenLifetimeNanos = positiveNanos(newTokenLifetime, "NEW_TOKEN lifetime");
        this.rotationIntervalNanos = positiveNanos(rotationInterval, "key rotation interval");
        if (rotationIntervalNanos < Math.max(retryLifetimeNanos, newTokenLifetimeNanos)) {
            throw new IllegalArgumentException("Key rotation interval must not be shorter than a token lifetime");
        }
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.random = Objects.requireNonNull(random, "random");
        this.keyRing = new AtomicReference<>(new KeyRing(newKey(nanoTime.getAsLong(), null), null));
    }

    static QuicAddressTokenService create() {
        return new QuicAddressTokenService(RETRY_TOKEN_LIFETIME,
                                           NEW_TOKEN_LIFETIME,
                                           KEY_ROTATION_INTERVAL,
                                           System::nanoTime,
                                           new SecureRandom());
    }

    static QuicAddressTokenService create(Duration retryLifetime,
                                           Duration newTokenLifetime,
                                           Duration rotationInterval,
                                           LongSupplier nanoTime,
                                           SecureRandom random) {
        return new QuicAddressTokenService(retryLifetime,
                                           newTokenLifetime,
                                           rotationInterval,
                                           nanoTime,
                                           random);
    }

    static TokenKind kind(ByteBuffer token) {
        Objects.requireNonNull(token, "token");
        if (token.remaining() < 2 || token.get(token.position()) != FORMAT_VERSION) {
            return null;
        }
        return TokenKind.of(token.get(token.position() + 1));
    }

    Optional<byte[]> retryToken(InetSocketAddress peerAddress,
                                QuicVersion version,
                                QuicConnectionId originalDestinationId,
                                QuicConnectionId retrySourceId,
                                QuicConnectionId clientSourceId) {
        Objects.requireNonNull(originalDestinationId, "originalDestinationId");
        Objects.requireNonNull(retrySourceId, "retrySourceId");
        Objects.requireNonNull(clientSourceId, "clientSourceId");
        return mint(TokenKind.RETRY,
                    peerAddress,
                    version,
                    originalDestinationId.bytes(),
                    retrySourceId.bytes(),
                    clientSourceId.bytes());
    }

    Optional<byte[]> newToken(InetSocketAddress peerAddress, QuicVersion version) {
        return mint(TokenKind.NEW_TOKEN, peerAddress, version, EMPTY_BYTES, EMPTY_BYTES, EMPTY_BYTES);
    }

    Optional<ValidatedToken> validate(InetSocketAddress peerAddress,
                                      QuicVersion version,
                                      QuicConnectionId destinationId,
                                      QuicConnectionId clientSourceId,
                                      byte[] token) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(clientSourceId, "clientSourceId");
        Objects.requireNonNull(token, "token");
        if (closed.get() || token.length < HEADER_SIZE + GCM_TAG_SIZE || token.length > MAX_TOKEN_SIZE) {
            return Optional.empty();
        }

        ByteBuffer encoded = ByteBuffer.wrap(token).asReadOnlyBuffer();
        if (encoded.get() != FORMAT_VERSION) {
            return Optional.empty();
        }
        TokenKind kind = TokenKind.of(encoded.get());
        if (kind == null) {
            return Optional.empty();
        }
        int keyId = encoded.getInt();
        byte[] nonce = new byte[NONCE_SIZE];
        encoded.get(nonce);
        long now = nanoTime.getAsLong();
        rotateIfNeeded(now);
        lifecycleLock.readLock().lock();
        try {
            if (closed.get()) {
                return Optional.empty();
            }
            KeyRing ring = keyRing.get();
            if (ring == null) {
                return Optional.empty();
            }
            KeyEpoch key;
            if (ring.current().id() == keyId) {
                key = ring.current();
            } else {
                KeyEpoch previous = ring.previous();
                key = previous != null && previous.id() == keyId ? previous : null;
            }
            if (key == null) {
                return Optional.empty();
            }

            byte[] plaintext;
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key.secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
                cipher.updateAAD(ByteBuffer.wrap(token, 0, HEADER_SIZE));
                plaintext = cipher.doFinal(token, HEADER_SIZE, token.length - HEADER_SIZE);
            } catch (AEADBadTagException e) {
                return Optional.empty();
            } catch (GeneralSecurityException | ProviderException | IllegalStateException e) {
                return Optional.empty();
            }

            ByteBuffer content = ByteBuffer.wrap(plaintext);
            if (content.remaining() < Long.BYTES + Integer.BYTES + 1 + Integer.BYTES + 3) {
                return Optional.empty();
            }
            long issuedAt = content.getLong();
            long age = now - issuedAt;
            long lifetime = kind == TokenKind.RETRY ? retryLifetimeNanos : newTokenLifetimeNanos;
            if (age < 0 || age >= lifetime || content.getInt() != version.versionNumber()) {
                return Optional.empty();
            }
            int addressLength = Byte.toUnsignedInt(content.get());
            if ((addressLength != 4 && addressLength != MAX_ADDRESS_SIZE)
                    || content.remaining() < addressLength + Integer.BYTES + 3) {
                return Optional.empty();
            }
            byte[] encodedAddress = new byte[addressLength];
            content.get(encodedAddress);
            byte[] peerAddressBytes = addressBytes(peerAddress);
            if (peerAddressBytes == null || !MessageDigest.isEqual(encodedAddress, peerAddressBytes)) {
                return Optional.empty();
            }
            int port = content.getInt();
            byte[] originalDestination = readConnectionId(content);
            byte[] retrySource = readConnectionId(content);
            byte[] clientSource = readConnectionId(content);
            if (content.hasRemaining()) {
                return Optional.empty();
            }

            if (kind == TokenKind.NEW_TOKEN) {
                if (port != -1
                        || originalDestination.length != 0
                        || retrySource.length != 0
                        || clientSource.length != 0) {
                    return Optional.empty();
                }
                if (closed.get()) {
                    return Optional.empty();
                }
                return Optional.of(new ValidatedToken(kind, null, null));
            }
            if (port != peerAddress.getPort()
                    || originalDestination.length < 8
                    || retrySource.length == 0
                    || !MessageDigest.isEqual(retrySource, destinationId.bytes())
                    || !MessageDigest.isEqual(clientSource, clientSourceId.bytes())) {
                return Optional.empty();
            }
            if (closed.get()) {
                return Optional.empty();
            }
            return Optional.of(new ValidatedToken(kind,
                                                  PeerConnectionId.create(originalDestination),
                                                  PeerConnectionId.create(retrySource)));
        } catch (RuntimeException e) {
            return Optional.empty();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        lifecycleLock.writeLock().lock();
        try {
            KeyRing ring = keyRing.getAndSet(null);
            if (ring != null) {
                ring.current().destroy();
                if (ring.previous() != null) {
                    ring.previous().destroy();
                }
            }
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private static byte[] addressBytes(InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        return inetAddress == null ? null : inetAddress.getAddress();
    }

    private static byte[] readConnectionId(ByteBuffer content) {
        int length = Byte.toUnsignedInt(content.get());
        if (length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH || content.remaining() < length) {
            throw new IllegalArgumentException("Invalid connection ID length");
        }
        byte[] result = new byte[length];
        content.get(result);
        return result;
    }

    private static void putConnectionId(ByteBuffer target, byte[] connectionId) {
        if (connectionId.length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid connection ID length");
        }
        target.put((byte) connectionId.length);
        target.put(connectionId);
    }

    private static long positiveNanos(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
        return duration.toNanos();
    }

    private Optional<byte[]> mint(TokenKind kind,
                                  InetSocketAddress peerAddress,
                                  QuicVersion version,
                                  byte[] originalDestination,
                                  byte[] retrySource,
                                  byte[] clientSource) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        Objects.requireNonNull(version, "version");
        if (closed.get()) {
            return Optional.empty();
        }
        byte[] address = addressBytes(peerAddress);
        if (address == null) {
            return Optional.empty();
        }
        long now = nanoTime.getAsLong();
        rotateIfNeeded(now);
        lifecycleLock.readLock().lock();
        try {
            if (closed.get()) {
                return Optional.empty();
            }
            KeyRing ring = keyRing.get();
            if (ring == null) {
                return Optional.empty();
            }
            KeyEpoch key = ring.current();
            byte[] nonce = key.nextNonce();
            if (nonce == null) {
                return Optional.empty();
            }

            int plaintextSize = Long.BYTES + Integer.BYTES + 1 + address.length + Integer.BYTES
                    + 3 + originalDestination.length + retrySource.length + clientSource.length;
            ByteBuffer plaintext = ByteBuffer.allocate(plaintextSize);
            plaintext.putLong(now);
            plaintext.putInt(version.versionNumber());
            plaintext.put((byte) address.length);
            plaintext.put(address);
            plaintext.putInt(kind == TokenKind.RETRY ? peerAddress.getPort() : -1);
            putConnectionId(plaintext, originalDestination);
            putConnectionId(plaintext, retrySource);
            putConnectionId(plaintext, clientSource);

            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            header.put(FORMAT_VERSION);
            header.put(kind.marker());
            header.putInt(key.id());
            header.put(nonce);
            byte[] headerBytes = header.array();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key.secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(headerBytes);
            byte[] ciphertext = cipher.doFinal(plaintext.array());
            if (headerBytes.length + ciphertext.length > MAX_TOKEN_SIZE) {
                return Optional.empty();
            }
            ByteBuffer token = ByteBuffer.allocate(headerBytes.length + ciphertext.length);
            token.put(headerBytes);
            token.put(ciphertext);
            if (closed.get()) {
                return Optional.empty();
            }
            return Optional.of(token.array());
        } catch (GeneralSecurityException | ProviderException | IllegalStateException e) {
            return Optional.empty();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private void rotateIfNeeded(long now) {
        KeyRing ring = keyRing.get();
        if (closed.get()
                || ring == null
                || now - ring.current().createdAt() < rotationIntervalNanos) {
            return;
        }
        lifecycleLock.writeLock().lock();
        try {
            ring = keyRing.get();
            if (closed.get()
                    || ring == null
                    || now - ring.current().createdAt() < rotationIntervalNanos) {
                return;
            }
            KeyEpoch current = newKey(now, ring.current());
            keyRing.set(new KeyRing(current, ring.current()));
            if (ring.previous() != null) {
                ring.previous().destroy();
            }
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private KeyEpoch newKey(long now, KeyEpoch previous) {
        int id;
        do {
            id = random.nextInt();
        } while (previous != null && id == previous.id());
        byte[] material = new byte[KEY_SIZE];
        byte[] noncePrefix = new byte[NONCE_PREFIX_SIZE];
        try {
            random.nextBytes(material);
            random.nextBytes(noncePrefix);
            return new KeyEpoch(id,
                                now,
                                new DestroyableSecretKey(material),
                                noncePrefix,
                                new AtomicLong());
        } finally {
            Arrays.fill(material, (byte) 0);
        }
    }

    enum TokenKind {
        RETRY((byte) 1),
        NEW_TOKEN((byte) 2);

        private final byte marker;

        TokenKind(byte marker) {
            this.marker = marker;
        }

        byte marker() {
            return marker;
        }

        private static TokenKind of(byte marker) {
            return switch (marker) {
                case 1 -> RETRY;
                case 2 -> NEW_TOKEN;
                default -> null;
            };
        }
    }

    record ValidatedToken(TokenKind kind,
                          QuicConnectionId originalDestinationId,
                          QuicConnectionId retrySourceId) {
    }

    private record KeyRing(KeyEpoch current, KeyEpoch previous) {
    }

    private record KeyEpoch(int id,
                            long createdAt,
                            DestroyableSecretKey secretKey,
                            byte[] noncePrefix,
                            AtomicLong nonceCounter) {
        private byte[] nextNonce() {
            long counter = nonceCounter.getAndIncrement();
            if (counter < 0) {
                return null;
            }
            ByteBuffer nonce = ByteBuffer.allocate(NONCE_SIZE);
            nonce.put(noncePrefix);
            nonce.putLong(counter);
            return nonce.array();
        }

        private void destroy() {
            Arrays.fill(noncePrefix, (byte) 0);
            secretKey.destroy();
        }
    }

    private static final class DestroyableSecretKey implements SecretKey {
        private static final long serialVersionUID = 1L;

        private final AtomicReference<byte[]> material;

        private DestroyableSecretKey(byte[] material) {
            this.material = new AtomicReference<>(material.clone());
        }

        @Override
        public String getAlgorithm() {
            return "AES";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            byte[] current = material.get();
            if (current == null) {
                throw new IllegalStateException("Key is destroyed");
            }
            return current.clone();
        }

        @Override
        public void destroy() {
            byte[] current = material.getAndSet(null);
            if (current != null) {
                Arrays.fill(current, (byte) 0);
            }
        }

        @Override
        public boolean isDestroyed() {
            return material.get() == null;
        }
    }
}
