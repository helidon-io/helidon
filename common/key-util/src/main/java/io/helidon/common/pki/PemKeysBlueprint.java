/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.configurable.Resource;

/**
 * PEM files based keys - accepts private key and certificate chain.
 * <p>
 * Private keys are decoded using {@link java.security.PEMDecoder}, including PKCS#8, encrypted PKCS#8, and
 * unencrypted RSA PKCS#1 keys. Other private key formats must be converted to one of these formats or to a PKCS#12
 * keystore (using {@link io.helidon.common.pki.KeystoreKeys.Builder}). A configured passphrase is used for encrypted
 * keys and does not prevent loading an unencrypted key.
 * PEM resources may contain multiple objects; the first matching key is loaded, while certificate resources load
 * all certificates in input order.
 */
@Api.Stable
@Prototype.Configured
@Prototype.Blueprint
interface PemKeysBlueprint {
    /**
     * Read a private key from PEM format from a resource definition.
     *
     * @return key resource (file, classpath, URL etc.)
     */
    @Option.Configured("key.resource")
    Optional<Resource> key();

    /**
     * Passphrase for private key. If the key is encrypted (and in PEM PKCS#8 format), this passphrase will be used to
     * decrypt it.
     *
     * @return passphrase used to encrypt the private key
     */
    @Option.Configured("key.passphrase")
    @Option.Confidential
    Optional<char[]> keyPassphrase();

    /**
     * Read a public key from PEM format from a resource definition.
     *
     * @return public key resource (file, classpath, URL etc.)
     */
    @Option.Configured("public-key.resource")
    Optional<Resource> publicKey();

    /**
     * Load certificate chain from PEM resource.
     *
     * @return resource (e.g. classpath, file path, URL etc.)
     */
    @Option.Configured("cert-chain.resource")
    Optional<Resource> certChain();

    /**
     * Read one or more certificates in PEM format from a resource definition. Used eg: in a trust store.
     *
     * @return key resource (file, classpath, URL etc.)
     */
    @Option.Configured("certificates.resource")
    Optional<Resource> certificates();
}
