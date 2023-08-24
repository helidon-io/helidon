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

package io.helidon.integrations.oci.tls.certificates;

import java.net.URI;
import java.security.PrivateKey;
import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.pki.Keys;
import io.helidon.config.Config;

import jakarta.inject.Singleton;

@Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciPrivateKeyDownloader extends DefaultOciPrivateKeyDownloader {

    static int callCount;

    @Override
    public PrivateKey loadKey(String keyOcid,
                              URI vaultCryptoEndpoint) {
        callCount++;

        if (OciTestUtils.ociRealUsage()) {
            return super.loadKey(keyOcid, vaultCryptoEndpoint);
        } else {
            Objects.requireNonNull(keyOcid);
            Objects.requireNonNull(vaultCryptoEndpoint);

            Keys keys = Keys.builder()
                    .config(Config.create().get("test-keys"))
                    .build();
            return keys.privateKey().orElseThrow();
        }
    }

}
