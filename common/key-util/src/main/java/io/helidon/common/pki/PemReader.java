/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.pki;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.List;

import io.helidon.common.Api;

/**
 * Reads certificates from PEM input.
 *
 * @deprecated use {@link java.security.PEMDecoder} to decode PEM-encoded keys and certificates
 */
@Deprecated(since = "28.0.0", forRemoval = true)
@Api.Stable
public final class PemReader {
    private PemReader() {
    }

    /**
     * Reads all X.509 certificates from an input stream and closes the stream.
     *
     * @param certStream cert input stream
     * @return list of certificates in input order
     * @deprecated use {@link java.security.PEMDecoder#decode(InputStream, Class)} with
     *             {@code X509Certificate.class} for each certificate, managing the input stream's lifecycle explicitly
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public static List<X509Certificate> readCertificates(InputStream certStream) {
        return PkiUtil.readPemCertificates(certStream);
    }
}
