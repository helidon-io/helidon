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

import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

import jakarta.inject.Singleton;

/**
 * For testing.
 */
@Singleton
public class TestOciPrivateKeyDownloader extends DefaultOciPrivateKeyDownloader {

    static int callCount;

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public TestOciPrivateKeyDownloader() {
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY - 1;
    }

    @Override
    public PrivateKey loadKey(String keyOcid,
                              URI vaultCryptoEndpoint) {
        callCount++;

        try {
            if (OciTestUtils.ociRealUsage()) {
                return super.loadKey(keyOcid, vaultCryptoEndpoint);
            } else {
                Objects.requireNonNull(keyOcid);
                Objects.requireNonNull(vaultCryptoEndpoint);

                KeyConfig keys = KeyConfig.fullBuilder()
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
