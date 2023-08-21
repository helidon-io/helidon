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
package io.helidon.common.tls;

import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Utility class for TLS.
 */
public class TlsUtils {

    private TlsUtils() {
    }

    /**
     * Parse the Common Name (CN) from the first certificate if present.
     *
     * @param certificates certificates
     * @return  Common Name value
     */
    public static Optional<String> parseCn(Certificate[] certificates) {
        if (certificates.length >= 1) {
            Certificate certificate = certificates[0];
            X509Certificate cert = (X509Certificate) certificate;
            Principal principal = cert.getSubjectX500Principal();
            int i = 0;
            String[] segments = principal.getName().split("=|,");
            while (i + 1 < segments.length) {
                if ("CN".equals(segments[i])) {
                    return Optional.of(segments[i + 1]);
                }
                i += 2;
            }
            return Optional.of("Unknown CN");
        }
        return Optional.empty();
    }
}
