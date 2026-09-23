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

/**
 * HTTP-version-neutral transport metrics for HTTP endpoints.
 *
 * <p>This module records events published through {@link io.helidon.http.HttpTransportObserver}; it does not install
 * transport instrumentation. The WebServer metrics observer and WebClient transport metrics service integrate physical
 * TCP or Unix-domain connection and TLS handshake events, and HTTP/1 and HTTP/2 protocol selection and exchange events.
 * Each gRPC call contributes an HTTP/2
 * exchange; individual gRPC messages are not separate stream observations. WebSocket upgrade requests contribute HTTP/1
 * exchange events, but WebSocket messages do not. Client observations use role {@code client} and initiator {@code local};
 * server observations use role {@code server} and initiator {@code remote}. QUIC and HTTP/3 publishers are not included.
 *
 * <p>The adapter emits the following meters, identified by name and tags without a metric scope.
 * Timer base units are seconds.
 * <table>
 *     <caption>HTTP transport meters</caption>
 *     <tr><th>Name</th><th>Type</th><th>Tags</th><th>Meaning</th></tr>
 *     <tr><td>{@code helidon.http.connections.opened}</td><td>counter</td><td>role, transport, handshake</td>
 *         <td>Allocated physical connections</td></tr>
 *     <tr><td>{@code helidon.http.connections.established}</td><td>counter</td><td>role, transport, protocol</td>
 *         <td>Physical connections which selected their first usable protocol</td></tr>
 *     <tr><td>{@code helidon.http.connections.active}</td><td>gauge</td><td>role, transport, protocol</td>
 *         <td>Active physical connections</td></tr>
 *     <tr><td>{@code helidon.http.connections.closed}</td><td>counter</td>
 *         <td>role, transport, protocol, outcome</td><td>Closed physical connections</td></tr>
 *     <tr><td>{@code helidon.http.connections.duration}</td><td>timer</td>
 *         <td>role, transport, protocol, outcome</td><td>Physical connection lifetime</td></tr>
 *     <tr><td>{@code helidon.http.handshakes}</td><td>counter</td>
 *         <td>role, transport, handshake, outcome</td><td>Completed transport security handshakes</td></tr>
 *     <tr><td>{@code helidon.http.handshakes.duration}</td><td>timer</td>
 *         <td>role, transport, handshake, outcome</td><td>Transport security handshake duration</td></tr>
 *     <tr><td>{@code helidon.http.streams.opened}</td><td>counter</td>
 *         <td>role, protocol, direction, initiator</td><td>Opened HTTP request and response exchanges</td></tr>
 *     <tr><td>{@code helidon.http.streams.active}</td><td>gauge</td>
 *         <td>role, protocol, direction, initiator</td><td>Active HTTP request and response exchanges</td></tr>
 *     <tr><td>{@code helidon.http.streams.closed}</td><td>counter</td>
 *         <td>role, protocol, direction, initiator, outcome</td><td>Closed HTTP request and response exchanges</td></tr>
 *     <tr><td>{@code helidon.http.streams.duration}</td><td>timer</td>
 *         <td>role, protocol, direction, initiator, outcome</td><td>HTTP request and response exchange duration</td></tr>
 * </table>
 *
 * <p>The recording contract supports values for current and future publishers. Known tag values are roles
 * {@code client|server}; transports {@code tcp|unix|quic}; protocols
 * {@code unknown|http/1.1|http/2|http/3}; handshakes {@code none|tls|quic-tls}; directions {@code bidi|uni};
 * initiators {@code local|remote}; and lower-hyphen outcome names declared by
 * {@link io.helidon.http.HttpTransportObserver}. Additional transport and protocol identifiers must be stable and
 * bounded. Connection or stream identifiers, addresses, paths, SNI names, exception text, and protocol error codes are
 * never used as tags.
 *
 * <p>The adapter owns all exact meter IDs it creates, including any pre-existing meter returned by the registry
 * for one of those IDs. Applications must not pre-register an exact gauge ID because the Metrics API cannot expose and
 * adopt the gauge's backing value. Configured registry wrappers retain their filtering, listeners, factory, and clock.
 * Wrappers which recursively unwrap to the same native registry share meter ownership and active gauge values.
 *
 * <p>First-use meter registration and final meter removal run on a bounded asynchronous dispatcher. At most 1024
 * observed provider actions are admitted during one continuously busy dispatch wave. The admission budget resets only
 * after all provider work becomes idle. Each configured registry retains at most 256 distinct meter series during one
 * ownership epoch; observations beyond either limit are discarded. Cached counter, timer, and gauge updates run
 * directly. Closing an adapter lease is non-blocking; the registry owner must await the final owning lease's completion
 * before closing a caller-owned registry. The adapter never closes the registry. If asynchronous execution is unavailable,
 * pending provider work is permanently abandoned and lease completion still resolves without running provider work on a
 * transport callback.
 */
package io.helidon.http.metrics;
