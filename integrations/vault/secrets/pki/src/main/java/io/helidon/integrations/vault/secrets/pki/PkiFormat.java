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

package io.helidon.integrations.vault.secrets.pki;

/**
 * Format of certificate or CRL.
 */
public enum PkiFormat {
    /**
     * PEM encoded.
     */
    PEM("pem"),
    /**
     * DER binary encoded.
     */
    DER("der"),
    /**
     * PEM with private key (for
     * {@link io.helidon.integrations.vault.secrets.pki.PkiSecretsRx#issueCertificate(IssueCertificate.Request)}.
     * When used, the certificate response is bundled with private key.
     */
    PEM_BUNDLE("pem_bundle");

    private final String vaultType;

    PkiFormat(String vaultType) {
        this.vaultType = vaultType;
    }

    String vaultType() {
        return this.vaultType;
    }
}
