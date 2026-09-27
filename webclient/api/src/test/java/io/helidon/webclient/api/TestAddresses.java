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
package io.helidon.webclient.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Literal addresses for DNS lookup tests; creating them never queries a resolver.
 */
final class TestAddresses {

    static final InetAddress V4_A = address("v4-a", new byte[] {10, 0, 0, 1});
    static final InetAddress V4_B = address("v4-b", new byte[] {10, 0, 0, 2});
    static final InetAddress V6_A = address("v6-a", new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1});
    static final InetAddress V6_B = address("v6-b", new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 2});

    private TestAddresses() {
    }

    static InetAddress[] ipv6First() {
        return new InetAddress[] {V6_A, V4_A, V6_B, V4_B};
    }

    static InetAddress[] ipv4First() {
        return new InetAddress[] {V4_A, V6_A, V4_B, V6_B};
    }

    private static InetAddress address(String name, byte[] bytes) {
        try {
            return InetAddress.getByAddress(name, bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
