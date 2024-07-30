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

package io.helidon.common.pki;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of keys. If a key is defined in multiple places (keystore, pem, or explicit), the order of preference is:
 * <ul>
 *     <li>Explicit instance will be used</li>
 *     <li>Keystore will be used</li>
 *     <li>PEM will be used</li>
 * </ul>
 * So if a Private key is defined both explicitly and through PEM, the explicitly defined key would be used.
 */
@Prototype.Blueprint(decorator = KeysBuilderDecorator.class)
@Prototype.Configured
interface KeysBlueprint {
    /**
     * Configure keys from a keystore.
     * Once the config object is built, this option will ALWAYS be empty. All keys from the keystore will be
     * populated to {@link #privateKey()}, {@link #publicKey()}, {@link #publicCert()} etc.
     *
     * @return keystore configuration
     */
    @Option.Configured
    Optional<KeystoreKeys> keystore();

    /**
     * Configure keys from pem file(s).
     * Once the config object is built, this option will ALWAYS be empty. All keys from the keystore will be
     * populated to {@link #privateKey()}, {@link #publicKey()}, {@link #publicCert()} etc.
     *
     * @return pem based definition
     */
    @Option.Configured
    Optional<PemKeys> pem();

    /**
     * The public key of this config if configured.
     *
     * @return the public key of this config or empty if not configured
     */
    Optional<PublicKey> publicKey();

    /**
     * The private key of this config if configured.
     *
     * @return the private key of this config or empty if not configured
     */
    Optional<PrivateKey> privateKey();

    /**
     * The public X.509 Certificate if configured.
     *
     * @return the public certificate of this config or empty if not configured
     */
    Optional<X509Certificate> publicCert();

    /**
     * The X.509 Certificate Chain.
     *
     * @return the certificate chain or empty list if not configured
     */
    @Option.Singular("certChain")
    List<X509Certificate> certChain();

    /**
     * The X.509 Certificates.
     *
     * @return the certificates configured or empty list if none configured
     */
    @Option.Singular
    List<X509Certificate> certs();

}
