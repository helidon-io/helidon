/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.vault.cdi;

import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.KmsCryptoClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KMS crypto client (used for encryption, decryption and signatures) requires additional configuration, that cannot
 * be done automatically by the SDK.
 */
@ApplicationScoped
class CryptoClientProducer {
    private final String cryptoEndpoint;

    @Inject
    CryptoClientProducer(@ConfigProperty(name = "app.vault.cryptographic-endpoint")
                         String cryptoEndpoint) {
        this.cryptoEndpoint = cryptoEndpoint;
    }

    @Produces
    KmsCryptoClientBuilder clientBuilder() {
        return KmsCryptoClient.builder()
                .endpoint(cryptoEndpoint);
    }
}
