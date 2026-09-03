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

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Preview client policy for accepting and using HTTP {@code Alt-Svc} response advertisements.
 * <p>
 * Alternative service handling is opt-in through {@link HttpClientConfig#altSvc()}. Supporting client protocols use
 * this policy only for HTTPS origins and same-host alternative authorities. Clear-text HTTP origins, including an
 * opportunistic transition from HTTP to TLS, and cross-host alternatives are not supported.
 * <p>
 * The client applies its configured TLS settings without additional restrictions when connecting to an alternative
 * service. Unsafe TLS settings, such as trusting all certificates, therefore make alternative service handling
 * correspondingly unsafe. Helidon honors the configured TLS policy and does not require its built-in trust manager.
 */
@Api.Preview
@Prototype.Blueprint(decorator = ClientAltSvcConfigSupport.BuilderDecorator.class)
@Prototype.Configured(root = false)
interface ClientAltSvcConfigBlueprint {
    /**
     * Whether alternative service handling is enabled.
     * <p>
     * This option defaults to {@code true}. Setting it to {@code false} disables alternative service handling even when
     * this configuration is present.
     *
     * @return whether alternative service handling is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Allowed alternative service protocol identifiers.
     * <p>
     * An empty set permits every available client protocol that supports alternative services. A non-empty set is an
     * exact, case-sensitive allow-list of opaque ALPN protocol identifiers; it is not validated against the protocols
     * currently provided by the client. Each Java character in the range {@code U+0000} through {@code U+00FF} maps
     * one-to-one to an identifier byte. Each identifier must contain between {@code 1} and {@code 255} bytes, inclusive,
     * when this policy is enabled.
     *
     * @return allowed alternative service protocol identifiers
     */
    @Option.Configured
    @Option.Singular
    Set<String> protocols();
}
