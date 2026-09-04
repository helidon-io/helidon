/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

/**
 * Standalone QUIC transport and application-stream API.
 *
 * <p>{@link io.helidon.quic.QuicClient} and {@link io.helidon.quic.QuicServer} own UDP transport resources and publish
 * handshake-complete {@link io.helidon.quic.QuicSession} instances. Application protocols exchange data through the
 * protocol-neutral stream interfaces in this package. Connect, accept, read, write, and stream-open operations block
 * and are intended for virtual threads.
 *
 * <p>Types marked {@link io.helidon.common.Api.Internal} remain implementation details used by Helidon's HTTP/3
 * integration. Standalone application protocols should use the incubating client, server, session, and stream API
 * rather than low-level schedulers or transport state.
 *
 * @spec https://www.rfc-editor.org/rfc/rfc8999.html
 *       RFC 8999: Version-Independent Properties of QUIC
 * @spec https://www.rfc-editor.org/rfc/rfc9000.html
 *       RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * @spec https://www.rfc-editor.org/rfc/rfc9001.html
 *       RFC 9001: Using TLS to Secure QUIC
 * @spec https://www.rfc-editor.org/rfc/rfc9002.html
 *       RFC 9002: QUIC Loss Detection and Congestion Control
 * @spec https://www.rfc-editor.org/rfc/rfc9369.html
 *       RFC 9369: QUIC Version 2
 */
package io.helidon.quic;
