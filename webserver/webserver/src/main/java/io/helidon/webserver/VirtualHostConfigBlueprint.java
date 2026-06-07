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

package io.helidon.webserver;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.tls.Tls;

/**
 * TLS material for one listener virtual host selected by SNI.
 * <p>
 * A virtual host contributes certificate and key material only. Request routing still comes from the listener that owns
 * the virtual host.
 */
@Prototype.Configured
@Prototype.Blueprint
interface VirtualHostConfigBlueprint {
    /**
     * Exact DNS host name or narrow wildcard pattern such as {@code *.example.com}.
     *
     * @return virtual-host pattern
     */
    @Option.Configured
    String host();

    /**
     * Required enabled TLS configuration for this virtual host; virtual hosts do not inherit the listener TLS configuration.
     *
     * @return virtual-host TLS
     */
    @Option.Configured
    @Option.Required
    Tls tls();
}
