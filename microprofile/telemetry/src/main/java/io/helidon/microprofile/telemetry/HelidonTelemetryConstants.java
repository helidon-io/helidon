/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

/**
 * Common constants used in several classes.
 */
class HelidonTelemetryConstants {

    //Private constructor for a utility class.
    private HelidonTelemetryConstants() {
    }

    static final String HTTP_STATUS_CODE = "http.status_code";
    static final String HTTP_METHOD = "http.method";
    static final String HTTP_SCHEME = "http.scheme";

    // The following are for maintaining compatibility with MicroProfile Telemetry 1.x.
    @Deprecated(since = "4.4.0", forRemoval = true)
    static final String NET_HOST_NAME = "net.host.name";

    @Deprecated(since = "4.4.0", forRemoval = true)
    static final String NET_HOST_PORT = "net.host.port";

    @Deprecated(since = "4.4.0", forRemoval = true)
    static final String NET_PEER_NAME = "net.peer.name";

    @Deprecated(since = "4.4.0", forRemoval = true)
    static final String NET_PEER_PORT = "net.peer.port";
    // end of compatibility
}
