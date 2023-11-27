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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;

/**
 * Resources from a java keystore (PKCS12, JKS etc.).
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(KeystoreKeysBlueprint.CustomMethods.class)
interface KeystoreKeysBlueprint {
    /**
     * Default keystore type.
     */
    String DEFAULT_KEYSTORE_TYPE = "PKCS12";
    /**
     * Default private key alias.
     */
    String DEFAULT_PRIVATE_KEY_ALIAS = "1";

    /**
     * Keystore resource definition.
     *
     * @return keystore resource, from file path, classpath, URL etc.
     */
    @Option.Required
    @Option.Configured("resource")
    Resource keystore();

    /**
     * Set type of keystore.
     * Defaults to {@value #DEFAULT_KEYSTORE_TYPE},
     * expected are other keystore types supported by java then can store keys under aliases.
     *
     * @return keystore type to load the key
     */
    @Option.Configured
    @Option.Default(DEFAULT_KEYSTORE_TYPE)
    String type();

    /**
     * Pass-phrase of the keystore (supported with JKS and PKCS12 keystores).
     *
     * @return keystore password to use
     */
    @Option.Confidential
    @Option.Configured
    Optional<char[]> passphrase();

    /**
     * Alias of the private key in the keystore.
     *
     * @return alias of the key in the keystore
     */
    @Option.Configured("key.alias")
    Optional<String> keyAlias();

    /**
     * Pass-phrase of the key in the keystore (used for private keys).
     * This is (by default) the same as keystore passphrase - only configure
     * if it differs from keystore passphrase.
     *
     * @return pass-phrase of the key
     */
    @Option.Configured("key.passphrase")
    @Option.Confidential
    Optional<char[]> keyPassphrase();

    /**
     * Alias of X.509 certificate of public key.
     * Used to load both the certificate and public key.
     *
     * @return alias under which the certificate is stored in the keystore
     */
    @Option.Configured("cert.alias")
    Optional<String> certAlias();

    /**
     * Alias of an X.509 chain.
     *
     * @return alias of certificate chain in the keystore
     */
    @Option.Configured("cert-chain.alias")
    Optional<String> certChainAlias();

    /**
     * List of aliases used to generate a trusted set of certificates.
     *
     * @return aliases of certificates
     */
    @Option.Singular("certAlias")
    List<String> certAliases();

    /**
     * If you want to build a trust store, call this method to add all
     * certificates present in the keystore to certificate list.
     *
     * @return whether this is a trust store
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean trustStore();

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Keystore passphrase.
         *
         * @param builder builder to update
         * @param passphrase new keystore passphrase
         * @deprecated use {@link #passphrase(String)} instead
         */
        @Deprecated(forRemoval = true, since = "4.0.0")
        @Prototype.BuilderMethod
        static void keystorePassphrase(KeystoreKeys.BuilderBase<?, ?> builder, String passphrase) {
            builder.passphrase(passphrase);
        }
    }
}
