/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.tls;

/**
 * Type of client authentication.
 */
public enum TlsClientAuth {
    /**
     * Mutual TLS is required.
     * Server MUST present a certificate trusted by the client, client MUST present a certificate trusted by the server.
     * This implies private key and trust configuration for both server and client.
     */
    REQUIRED,
    /**
     * Mutual TLS is optional.
     * Server MUST present a certificate trusted by the client, client MAY present a certificate trusted by the server.
     * This implies private key configuration at least for server, trust configuration for at least client.
     */
    OPTIONAL,
    /**
     * Mutual TLS is disabled.
     * Server MUST present a certificate trusted by the client, client does not present a certificate.
     * This implies private key configuration for server, trust configuration for client.
     */
    NONE
}
