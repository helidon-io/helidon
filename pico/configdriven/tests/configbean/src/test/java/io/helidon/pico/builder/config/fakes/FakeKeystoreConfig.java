/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.fakes;

import java.util.List;

import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.builder.Singular;
import io.helidon.pico.builder.config.ConfigBean;

/**
 * aka KeyConfig.Keystore.Builder
 *
 * This is a ConfigBean since it marries up to the backing config.
 */
@ConfigBean
public interface FakeKeystoreConfig {

    String DEFAULT_KEYSTORE_TYPE = "PKCS12";

//    private final StreamHolder keystoreStream = new StreamHolder("keystore");

//    default FakeKeystoreConfigBean trustStore() {
//        return trustStore(true);
//    }

    @ConfiguredOption(key = "trust-store")
    boolean trustStore();

//    /**
//     * Keystore resource definition.
//     *
//     * @param keystore keystore resource, from file path, classpath, URL etc.
//     * @return updated builder instance
//     */
//    @ConfiguredOption(key = "resource", required = true)
//    public KeystoreBuilder keystore(Resource keystore) {
//        this.keystoreStream.stream(keystore);
//        return this;
//    }
//    default DefaultFakeKeystoreConfigBean.Builder keystore(Resource keystore) {
//
//    }

    @ConfiguredOption(key = "type", value = DEFAULT_KEYSTORE_TYPE)
    String keystoreType();

    @ConfiguredOption(key = "passphrase")
    char[] keystorePassphrase();

    @ConfiguredOption(key = "key.alias", value = "1")
    String keyAlias();

    @ConfiguredOption(key = "key.passphrase")
    char[] keyPassphrase();

    @ConfiguredOption(key = "cert.alias")
    @Singular("certAlias")
    List<String> certAliases();

    @ConfiguredOption(key = "cert-chain.alias")
    String certChainAlias();


//    /**
//     * Update this builder from configuration.
//     * The following keys are expected under key {@code keystore}:
//     * <ul>
//     * <li>{@code resource}: resource configuration as understood by {@link io.helidon.common.configurable.Resource}</li>
//     * <li>{@code type}: type of keystore (defaults to PKCS12)</li>
//     * <li>{@code passphrase}: passphrase of keystore, if required</li>
//     * <li>{@code key.alias}: alias of private key, if wanted (defaults to "1")</li>
//     * <li>{@code key.passphrase}: passphrase of private key if differs from keystore passphrase</li>
//     * <li>{@code cert.alias}: alias of public certificate (to obtain public key)</li>
//     * <li>{@code cert-chain.alias}: alias of certificate chain</li>
//     * <li>{@code trust-store}: true if this is a trust store (and we should load all certificates from it), defaults to false</li>
//     * </ul>
//     *
//     * @param config configuration instance
//     * @return updated builder instance
//     */
//    public KeystoreBuilder config(Config config) {
//        Config keystoreConfig = config.get("keystore");
//
//        // the actual resource (file, classpath) with the bytes of the keystore
//        keystoreConfig.get("resource").as(Resource::create).ifPresent(this::keystore);
//
//        // type of keystore
//        keystoreConfig.get("type")
//                .asString()
//                .ifPresent(this::keystoreType);
//        // password of the keystore
//        keystoreConfig.get("passphrase")
//                .asString()
//                .map(String::toCharArray)
//                .ifPresent(this::keystorePassphrase);
//        // private key alias
//        keystoreConfig.get("key.alias")
//                .asString()
//                .ifPresent(this::keyAlias);
//        // private key password
//        keystoreConfig.get("key.passphrase")
//                .asString()
//                .map(String::toCharArray)
//                .ifPresent(this::keyPassphrase);
//        keystoreConfig.get("cert.alias")
//                .asString()
//                .ifPresent(this::certAlias);
//        keystoreConfig.get("cert-chain.alias")
//                .asString()
//                .ifPresent(this::certChainAlias);
//        // whether this is a keystore (with a private key) or a trust store (just trusted public keys/certificates)
//        keystoreConfig.get("trust-store")
//                .asBoolean()
//                .ifPresent(this::trustStore);
//
//        return this;
//    }

}
