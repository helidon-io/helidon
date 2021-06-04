/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * <h2>Security</h2>
 *
 * Supports security for web (and possibly other) resources including:
 * <ul>
 * <li>Authentication: authenticate a request</li>
 * <li>Authorization: authorize a request to a resource, possibly using ABAC or RBAC.</li>
 * <li>Outbound security: propagating security on outbound calls.</li>
 * <li>Audit: auditing security operations</li>
 * </ul>
 * And security for any resource type when using programmatic approach. Starting point:
 * {@link io.helidon.security.Security} and {@link io.helidon.security.SecurityContext}.
 *
 * Various security aspects are pluggable, using {@link io.helidon.security.spi.SecurityProvider providers}
 * to extend functionality.
 *
 * <h2>Bootstrapping</h2>
 *
 * You have two way to do things with security - either load it from configuration or create a fully configured instance
 * using a builder. Both approaches should allow the same behavior.
 * <p>
 * To create security using builder:<br>
 * <code>{@link io.helidon.security.Security Security}.{@link io.helidon.security.Security#builder() builder()}
 * .{@link io.helidon.security.Security.Builder#build() build()}</code>
 * <p>
 * Or using configuration:<br>
 * <code>{@link io.helidon.security.Security#create(io.helidon.config.Config)}</code>
 * <p>
 * Configuration example (Google login for users and http-signatures for service):<br>
 * <pre><code>
 * security:
 *   provider-policy:
 *     # Composite policy when using more than one provider
 *     type: "COMPOSITE"
 *     authentication:
 *       # This is a frontend service - only allow google authentication
 *       - name: "google-login"
 *     outbound:
 *       # Propagate the goole token and this service's identity to backend
 *       - name: "google-login"
 *       - name: "http-signatures"
 *   providers:
 *     # Google login button support - authentication and identity propagation provider
 *     - google-login:
 *         client-id: "your-google-application-id"
 *     # Attribute based access control authorization provider
 *     - abac:
 *     # HTTP signatures - authentication and identity propagation provider (for service identity)
 *     - http-signatures:
 *         outbound:
 *         - name: "backend"
 *           hosts: ["localhost"]
 *           signature:
 *             key-id: "frontend"
 *             # password may be encrypted when using secure filter for Helidon config
 *             hmac.secret: "..."
 * </code></pre>
 */
package io.helidon.security;
