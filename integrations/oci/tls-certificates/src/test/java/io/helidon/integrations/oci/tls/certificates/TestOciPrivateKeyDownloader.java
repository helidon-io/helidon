/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.net.URI;
import java.security.PrivateKey;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.pki.Keys;
import io.helidon.config.Config;
import io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;
import io.helidon.service.registry.Service;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciPrivateKeyDownloader implements OciPrivateKeyDownloader {

    static volatile int callCount;

    private final Supplier<DefaultOciPrivateKeyDownloader> realDownloader;

    TestOciPrivateKeyDownloader(Supplier<DefaultOciPrivateKeyDownloader> realDownloader) {
        this.realDownloader = realDownloader;
    }

    @Override
    public PrivateKey loadKey(String keyOcid,
                              URI vaultCryptoEndpoint) {
        callCount++;

        try {
            if (OciTestUtils.ociRealUsage()) {
                return realDownloader.get().loadKey(keyOcid, vaultCryptoEndpoint);
            } else {
                Objects.requireNonNull(keyOcid);
                Objects.requireNonNull(vaultCryptoEndpoint);

                Keys keys = Keys.builder()
                        .config(Config.create().get("test-keys"))
                        .build();
                return keys.privateKey().orElseThrow();
            }
        } catch (Exception e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw e;
        }
    }

}
