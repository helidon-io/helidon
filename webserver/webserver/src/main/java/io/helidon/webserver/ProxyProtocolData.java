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

/**
 * Proxy protocol data parsed by {@link ProxyProtocolHandler}.
 */
public interface ProxyProtocolData {

    /**
     * Protocol family.
     */
    enum Family {
        /**
         * Unknown family.
         */
        UNKNOWN,

        /**
         * IP version 4.
         */
        IPv4,

        /**
         * IP version 6.
         */
        IPv6,

        /**
         * Unix.
         */
        UNIX;

        static Family fromString(String s) {
            return switch (s) {
                case "TCP4" -> IPv4;
                case "TCP6" -> IPv6;
                case "UNIX" -> UNIX;
                case "UNKNOWN" -> UNKNOWN;
                default -> throw new IllegalArgumentException("Unknown family " + s);
            };
        }
    }

    /**
     * Protocol type.
     */
    enum Protocol {
        /**
         * Unknown protocol.
         */
        UNKNOWN,

        /**
         * TCP streams protocol.
         */
        TCP,

        /**
         * UDP datagram protocol.
         */
        UDP;

        static Protocol fromString(String s) {
            return switch (s) {
                case "TCP4", "TCP6" -> TCP;
                case "UDP" -> UDP;
                case "UNKNOWN" -> UNKNOWN;
                default -> throw new IllegalArgumentException("Unknown protocol " + s);
            };
        }
    }

    /**
     * Family from protocol header.
     *
     * @return family
     */
    Family family();

    /**
     * Protocol from protocol header.
     *
     * @return protocol
     */
    Protocol protocol();

    /**
     * Source address that is either IP4 or IP6 depending on {@link #family()}.
     *
     * @return source address or {@code ""} if not provided
     */
    String sourceAddress();

    /**
     * Destination address that is either IP4 or IP46 depending on {@link #family()}.
     *
     * @return source address or (@code ""} if not provided
     */
    String destAddress();

    /**
     * Source port number.
     *
     * @return source port.
     */
    int sourcePort();

    /**
     * Destination port number.
     *
     * @return port number.
     */
    int destPort();
}


