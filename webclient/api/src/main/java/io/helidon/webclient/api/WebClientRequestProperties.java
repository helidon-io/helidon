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

import io.helidon.common.Api;

/**
 * Internal WebClient request property names shared between protocol implementations.
 */
@Api.Internal
public final class WebClientRequestProperties {
    /**
     * Marks an explicit connection as a protocol-probe connection owned by WebClient internals.
     */
    public static final String PROTOCOL_PROBE_CONNECTION =
            "io.helidon.webclient.internal.protocol-probe-connection";

    private WebClientRequestProperties() {
    }
}
