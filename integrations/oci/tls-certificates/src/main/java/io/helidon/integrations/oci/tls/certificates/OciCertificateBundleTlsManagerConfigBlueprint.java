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

package io.helidon.integrations.oci.tls.certificates;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Blueprint configuration for {@link OciCertificateBundleTlsManager}.
 */
@Prototype.Blueprint
@Prototype.Configured
interface OciCertificateBundleTlsManagerConfigBlueprint extends Prototype.Factory<OciCertificateBundleTlsManager> {

    /**
     * The schedule for checking whether OCI has newer TLS material.
     *
     * @return the schedule for reload
     */
    @Option.Configured
    String schedule();

    /**
     * Whether to reload TLS even when the certificate version and CA certificate are unchanged.
     *
     * @return whether unchanged material should still be reloaded
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean alwaysReload();

    /**
     * The certificate authority OCID.
     *
     * @return certificate authority OCID
     */
    @Option.Configured
    String caOcid();

    /**
     * The OCI-managed certificate OCID.
     *
     * @return certificate OCID
     */
    @Option.Configured
    String certOcid();
}
