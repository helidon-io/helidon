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

package io.helidon.inject.tests.configbeans;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * aka KeyConfig.Keystore.Builder
 *
 * This is a ConfigBean since it marries up to the backing config.
 */
@Prototype.Configured
@Prototype.Blueprint
interface FakeKeystoreConfigBlueprint {

    String DEFAULT_KEYSTORE_TYPE = "PKCS12";

    @Option.Configured
    boolean trustStore();

    @Option.Configured("type")
    @Option.Default(DEFAULT_KEYSTORE_TYPE)
    String keystoreType();

    @Option.Configured("passphrase")
    char[] keystorePassphrase();

    @Option.Configured("key.alias")
    @Option.Default("1")
    String keyAlias();

    @Option.Configured("key.passphrase")
    char[] keyPassphrase();

    @Option.Configured("cert.alias")
    @Option.Singular("certAlias")
    List<String> certAliases();

    @Option.Configured("cert-chain.alias")
    String certChainAlias();

}
