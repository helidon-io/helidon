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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.Api;

/**
 * Bounded one-shot cache for client QUIC Initial tokens received in {@code NEW_TOKEN} frames.
 * <p>
 * A reconnect-capable owner can share this cache across short-lived client runtimes. The owner remains responsible for
 * scoping an instance to the relevant local endpoint and network generation.
 */
@Api.Internal
public final class QuicClientInitialTokenCache implements AutoCloseable {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Recipient, byte[]> tokens = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<TokenKey, Boolean> seenNewTokens = new LinkedHashMap<>(16, 0.75f, true);
    private final int capacity;
    private boolean closed;

    private QuicClientInitialTokenCache(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Create a bounded token cache.
     *
     * @return token cache
     */
    public static QuicClientInitialTokenCache create() {
        return new QuicClientInitialTokenCache(QuicClientRuntime.MAX_INITIAL_TOKENS);
    }

    /**
     * Create a token cache with an explicit capacity.
     *
     * @param capacity maximum number of available recipients and independently remembered {@code NEW_TOKEN} values,
     *                 or zero to disable retention
     * @return token cache
     */
    public static QuicClientInitialTokenCache create(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Token cache capacity must not be negative");
        }
        return new QuicClientInitialTokenCache(capacity);
    }

    Optional<byte[]> consume(InetSocketAddress peerAddress, QuicVersion version) {
        Recipient recipient = new Recipient(peerHost(peerAddress), peerAddress.getPort(), version);
        lock.lock();
        try {
            if (closed) {
                return Optional.empty();
            }
            byte[] token = tokens.remove(recipient);
            if (token == null) {
                return Optional.empty();
            }
            return Optional.of(token.clone());
        } finally {
            lock.unlock();
        }
    }

    int size() {
        lock.lock();
        try {
            if (closed) {
                return 0;
            }
            return tokens.size();
        } finally {
            lock.unlock();
        }
    }

    void register(InetSocketAddress peerAddress, QuicVersion version, byte[] token) {
        register(peerAddress, version, token, false);
    }

    void registerNewToken(InetSocketAddress peerAddress, QuicVersion version, byte[] token) {
        register(peerAddress, version, token, true);
    }

    private void register(InetSocketAddress peerAddress,
                          QuicVersion version,
                          byte[] token,
                          boolean discardDuplicate) {
        Objects.requireNonNull(token, "token");
        if (token.length == 0) {
            throw new IllegalArgumentException("Empty token");
        }
        if (capacity == 0) {
            return;
        }
        Recipient recipient = new Recipient(peerHost(peerAddress), peerAddress.getPort(), version);
        lock.lock();
        try {
            if (closed) {
                return;
            }
            byte[] cachedToken;
            if (discardDuplicate) {
                TokenKey tokenKey = new TokenKey(recipient, token);
                if (seenNewTokens.get(tokenKey) != null) {
                    return;
                }
                seenNewTokens.put(tokenKey, Boolean.TRUE);
                if (seenNewTokens.size() > capacity) {
                    var seen = seenNewTokens.keySet().iterator();
                    seen.next();
                    seen.remove();
                }
                cachedToken = tokenKey.token;
            } else {
                cachedToken = token.clone();
            }
            tokens.put(recipient, cachedToken);
            if (tokens.size() > capacity) {
                var recipients = tokens.keySet().iterator();
                recipients.next();
                recipients.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            tokens.clear();
            seenNewTokens.clear();
        } finally {
            lock.unlock();
        }
    }

    private static String peerHost(InetSocketAddress peerAddress) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        return peerAddress.getAddress() == null
                ? peerAddress.getHostString()
                : peerAddress.getAddress().getHostAddress();
    }

    private record Recipient(String host, int port, QuicVersion version) {
        private Recipient {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(version, "version");
        }
    }

    private static final class TokenKey {
        private final Recipient recipient;
        private final byte[] token;
        private final int hashCode;

        private TokenKey(Recipient recipient, byte[] token) {
            this.recipient = recipient;
            this.token = token.clone();
            this.hashCode = 31 * recipient.hashCode() + Arrays.hashCode(this.token);
        }

        @Override
        public boolean equals(Object object) {
            return this == object
                    || object instanceof TokenKey that
                    && recipient.equals(that.recipient)
                    && Arrays.equals(token, that.token);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

}
