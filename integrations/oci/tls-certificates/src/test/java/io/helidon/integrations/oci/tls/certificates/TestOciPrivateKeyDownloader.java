/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.pki.PemKeys;
import io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;
import io.helidon.service.registry.Service;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciPrivateKeyDownloader implements OciPrivateKeyDownloader {

    static volatile int callCount;

    @Override
    public PrivateKey loadKey(String keyOcid,
                              URI vaultCryptoEndpoint) {
        callCount++;

        try {
            Objects.requireNonNull(keyOcid);
            Objects.requireNonNull(vaultCryptoEndpoint);

            try (InputStream keyStream = TestOciPrivateKeyDownloader.class.getClassLoader()
                    .getResourceAsStream("test-keys/serverKey.pem")) {
                Objects.requireNonNull(keyStream);
                String keyPem = new String(keyStream.readAllBytes(), StandardCharsets.US_ASCII);
                PemKeys pemKeys = PemKeys.builder()
                        .key(Resource.create("test private key", keyPem))
                        .build();
                return Keys.builder()
                        .pem(pemKeys)
                        .build()
                        .privateKey()
                        .orElseThrow();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (Exception e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw e;
        }
    }

}
