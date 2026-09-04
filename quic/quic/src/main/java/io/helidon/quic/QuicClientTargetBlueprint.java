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
import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Immutable transport and TLS identity of a QUIC client target.
 */
@Prototype.Blueprint(decorator = QuicClientTargetSupport.Decorator.class, createEmptyPublic = false)
@Api.Incubating
interface QuicClientTargetBlueprint {

    /**
     * Remote transport address.
     *
     * <p>The address must be resolved and its port must be between {@code 1} and {@code 65535}.
     *
     * @return resolved remote address with a positive port
     */
    InetSocketAddress peerAddress();

    /**
     * Peer identity used by TLS certificate validation.
     *
     * <p>The name must not be blank. Unless the connection supplies an explicit SNI override, a valid DNS name is also
     * sent as SNI. An IP literal, or a name that is not valid for SNI, is not sent as SNI.
     *
     * @return TLS peer name
     */
    String tlsPeerName();

    /**
     * Peer port used by TLS identity checks.
     *
     * <p>A builder value of {@code 0}, including the default, is replaced with the remote transport port when the target
     * is built. The resulting target value is always between {@code 1} and {@code 65535}.
     *
     * @return effective TLS peer port
     */
    @Option.DefaultInt(0)
    int tlsPeerPort();

    /**
     * Ordered application protocols advertised through ALPN.
     *
     * <p>The list must be non-empty and contain unique, non-empty protocol names. Each Java character maps directly to
     * one opaque protocol byte using ISO-8859-1, so characters outside the byte range are rejected. Each name must fit
     * the ALPN one-byte length limit of {@code 255} bytes and the complete encoded list must not exceed
     * {@code 65535} bytes.
     *
     * @return immutable validated protocol list
     */
    @Option.Singular
    List<String> applicationProtocols();
}
