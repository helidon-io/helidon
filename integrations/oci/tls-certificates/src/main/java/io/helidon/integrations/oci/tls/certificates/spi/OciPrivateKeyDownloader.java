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

package io.helidon.integrations.oci.tls.certificates.spi;

import java.net.URI;
import java.security.PrivateKey;

/**
 * The contract used for downloading private keys from OCI.
 */
//@Contract
public interface OciPrivateKeyDownloader {

    /**
     * The implementation will download the private key identified by the given ocid from the given vault crypto endpoint.
     *
     * @param keyOcid the key ocid
     * @param vaultCryptoEndpoint the vault crypto endpoint identifying where to go to download the key ocid
     * @return the downloaded private key
     * @throws IllegalStateException if there is any errors loading the key
     */
    PrivateKey loadKey(String keyOcid,
                       URI vaultCryptoEndpoint);

}
