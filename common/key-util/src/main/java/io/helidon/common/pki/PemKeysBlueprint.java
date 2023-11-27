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

package io.helidon.common.pki;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;

/**
 * PEM files based keys - accepts private key and certificate chain.
 * <p>
 * If you have "standard" linux/unix private key, you must run "
 * {@code openssl pkcs8 -topk8 -in ./id_rsa -out ./id_rsa.p8}" on it to work with this builder for password protected
 * file; or "{@code openssl pkcs8 -topk8 -in ./id_rsa -out ./id_rsa_nocrypt.p8 -nocrypt}" for unprotected file.
 * <p>
 * The only supported format is PKCS#8. If you have a different format, you must transform it to PKCS8 PEM format (to
 * use this builder), or to PKCS#12 keystore format (and use {@link io.helidon.common.pki.KeystoreKeys.Builder}).
 */
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
