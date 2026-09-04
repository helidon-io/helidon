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
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class QuicPublicApiSupport {
    private static final long MAX_APPLICATION_ERROR_CODE = (1L << 62) - 1;

    private QuicPublicApiSupport() {
    }

    static List<String> applicationProtocols(List<String> protocols) {
        Objects.requireNonNull(protocols, "applicationProtocols");
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("At least one application protocol is required");
        }
        var copy = List.copyOf(protocols);
        var unique = new HashSet<String>(copy.size());
        long listLength = 0;
        for (String protocol : copy) {
            Objects.requireNonNull(protocol, "applicationProtocol");
            if (protocol.isEmpty()) {
                throw new IllegalArgumentException("Application protocol must not be empty");
            }
            for (int index = 0; index < protocol.length(); index++) {
                if (protocol.charAt(index) > 0xFF) {
                    throw new IllegalArgumentException("Application protocol contains a character outside the byte range: "
                                                               + protocol);
                }
            }
            int encodedLength = protocol.length();
            if (encodedLength > 255) {
                throw new IllegalArgumentException("Application protocol exceeds the 255-byte ALPN limit: " + protocol);
            }
            listLength += 1L + encodedLength;
            if (listLength > 65_535) {
                throw new IllegalArgumentException("Application protocol list exceeds the 65535-byte ALPN limit");
            }
            if (!unique.add(protocol)) {
                throw new IllegalArgumentException("Duplicate application protocol: " + protocol);
            }
        }
        return copy;
    }

    static long applicationErrorCode(long errorCode) {
        if (errorCode < 0 || errorCode > MAX_APPLICATION_ERROR_CODE) {
            throw new IllegalArgumentException("Application error code is outside the QUIC variable-length integer range: "
                                                       + errorCode);
        }
        return errorCode;
    }

    static Executor defaultExecutor() {
        return DefaultExecutorHolder.INSTANCE;
    }

    static void positiveNanosDuration(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(name + " must fit in signed-long nanoseconds: " + duration, e);
        }
    }

    static void resolvedBindAddress(Optional<InetSocketAddress> address, String name) {
        Objects.requireNonNull(address, name);
        address.ifPresent(value -> {
            if (value.isUnresolved()) {
                throw new IllegalArgumentException(name + " must be resolved: " + value);
            }
        });
    }

    private static final class DefaultExecutorHolder {
        private static final ExecutorService INSTANCE =
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                            .name("helidon-quic-", 0)
                                                            .factory());

        private DefaultExecutorHolder() {
        }
    }
}
