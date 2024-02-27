/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface RevocationConfigBlueprint {

    /**
     * Flag indicating whether this revocation config is enabled.
     *
     * @return enabled flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enabled();

    /**
     * Prefer CRL over OCSP.
     * Default value is {@code false}.
     *
     * @return whether to prefer CRL over OCSP
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean preferCrlOverOcsp();

    /**
     * Only check the revocation status of end-entity certificates.
     * Default value is {@code false}.
     *
     * @return whether to check only end-entity certificates
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean checkOnlyEndEntity();

    /**
     * Enable fallback to the less preferred checking option.
     *
     * @return whether to allow fallback to the less preferred checking option
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean fallbackEnabled();

    /**
     * Allow revocation check to succeed if the revocation status cannot be
     * determined for one of the following reasons:
     * <ul>
     *  <li>The CRL or OCSP response cannot be obtained because of a
     *      network error.
     *  <li>The OCSP responder returns one of the following errors
     *      specified in section 2.3 of RFC 2560: internalError or tryLater.
     * </ul>
     *
     * @return whether soft fail is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean softFailEnabled();

    /**
     * The URI that identifies the location of the OCSP responder. This
     * overrides the {@code ocsp.responderURL} security property and any
     * responder specified in a certificate's Authority Information Access
     * Extension, as defined in RFC 5280.
     *
     * @return OCSP responder URI
     */
    @Option.Configured
    Optional<URI> ocspResponderUri();

}
