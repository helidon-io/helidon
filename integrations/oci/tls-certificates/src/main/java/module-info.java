/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * Helidon Integrations of OCI Certificates Service.
 */
module io.helidon.integrations.oci.tls.certificates {
    requires static io.helidon.builder.api;
    requires static io.helidon.config.metadata;
    requires static jakarta.annotation;
    requires static jakarta.inject;

    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.common.pki;
    requires io.helidon.common.tls;
    requires io.helidon.config;
    requires io.helidon.faulttolerance;
    requires io.helidon.integrations.oci.sdk.runtime;
    requires io.helidon.inject.api;
    requires io.helidon.inject.runtime;
    requires io.helidon.scheduling;

    requires oci.java.sdk.common;
    requires oci.java.sdk.certificates;
    requires oci.java.sdk.keymanagement;

    uses io.helidon.common.tls.spi.TlsManagerProvider;
    uses io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;
    uses io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;

    exports io.helidon.integrations.oci.tls.certificates;
    exports io.helidon.integrations.oci.tls.certificates.spi;

    provides io.helidon.common.tls.spi.TlsManagerProvider
            with io.helidon.integrations.oci.tls.certificates.DefaultOciCertificatesTlsManagerProvider;
    provides io.helidon.inject.api.ModuleComponent
            with io.helidon.integrations.oci.tls.certificates.Injection$$Module;
}
