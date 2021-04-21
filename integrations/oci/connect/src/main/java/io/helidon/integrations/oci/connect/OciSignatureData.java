/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.connect;

import java.security.interfaces.RSAPrivateKey;

/**
 * Data needed to sign requests.
 */
public interface OciSignatureData {
    /**
     * Key ID to use.
     *
     * @return key ID
     */
    String keyId();

    /**
     * Private key to use to generate signatures.
     *
     * @return private key
     */
    RSAPrivateKey privateKey();

    /**
     * Create a new instance.
     *
     * @param keyId key ID
     * @param privateKey private key
     * @return a new instance of signature data
     */
    static OciSignatureData create(String keyId, RSAPrivateKey privateKey) {
        return new OciSignatureData() {
            @Override
            public String keyId() {
                return keyId;
            }

            @Override
            public RSAPrivateKey privateKey() {
                return privateKey;
            }

            @Override
            public String toString() {
                return keyId;
            }
        };
    }
}
