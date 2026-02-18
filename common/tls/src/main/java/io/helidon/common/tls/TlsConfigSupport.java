/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.pki.Keys;

final class TlsConfigSupport {
    private TlsConfigSupport() {
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        @Prototype.RuntimeTypeFactoryMethod("privateKey")
        static Optional<PrivateKey> createPrivateKey(Keys config) {
            return config.privateKey();
        }

        @Prototype.RuntimeTypeFactoryMethod("privateKeyCertChain")
        static List<X509Certificate> createPrivateKeyCertChain(Keys config) {
            return config.certChain();
        }

        @Prototype.RuntimeTypeFactoryMethod("trust")
        static List<X509Certificate> createTrust(Keys config) {
            return config.certs();
        }
    }
}
