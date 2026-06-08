/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

final class TlsMaterialSupport {
    private TlsMaterialSupport() {
    }

    static Tls toTls(TlsMaterial material) {
        Objects.requireNonNull(material, "material");

        TlsConfig.Builder builder = Tls.builder()
                .trustAll(material.trustAll());

        material.privateKey().ifPresent(builder::privateKey);
        if (!material.privateKeyCertChain().isEmpty()) {
            builder.privateKeyCertChain(material.privateKeyCertChain());
        }
        if (!material.trust().isEmpty()) {
            builder.trust(material.trust());
        }
        material.secureRandom().ifPresent(builder::secureRandom);
        material.secureRandomAlgorithm().ifPresent(builder::secureRandomAlgorithm);
        material.secureRandomProvider().ifPresent(builder::secureRandomProvider);
        material.keyManagerFactoryAlgorithm().ifPresent(builder::keyManagerFactoryAlgorithm);
        material.keyManagerFactoryProvider().ifPresent(builder::keyManagerFactoryProvider);
        material.trustManagerFactoryAlgorithm().ifPresent(builder::trustManagerFactoryAlgorithm);
        material.trustManagerFactoryProvider().ifPresent(builder::trustManagerFactoryProvider);
        material.internalKeystoreType().ifPresent(builder::internalKeystoreType);
        material.internalKeystoreProvider().ifPresent(builder::internalKeystoreProvider);
        material.revocation().ifPresent(builder::revocation);

        return builder.build();
    }
}
