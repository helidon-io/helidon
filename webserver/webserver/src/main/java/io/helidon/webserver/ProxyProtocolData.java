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
     * The protocol family options.
     */
    enum ProtocolFamily {
        /**
         * TCP version 4.
         */
        TCP4,

        /**
         * TCP version 6.
         */
        TCP6,

        /**
         * Protocol family is unknown.
         */
        UNKNOWN
    }

    /**
     * Protocol family from protocol header.
     *
     * @return protocol family
     */
    ProtocolFamily protocolFamily();

    /**
     * Source address that is either IPv4 or IPv6 depending on {@link #protocolFamily()}}.
     *
     * @return source address
     */
    String sourceAddress();

    /**
     * Destination address that is either IPv4 or IPv6 depending on {@link #protocolFamily()}}.
     *
     * @return source address
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


