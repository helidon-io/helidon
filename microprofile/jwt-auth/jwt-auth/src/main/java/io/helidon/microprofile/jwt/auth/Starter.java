/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.jwt.auth;

import java.net.URI;
import java.security.PublicKey;
import java.util.Optional;

import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.common.OptionalHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

/**
 * TODO javadoc.
 */
public class Starter {
    public static final String CONFIG_PUBLIC_KEY = "mp.jwt.verify.publickey";
    public static final String CONFIG_PUBLIC_KEY_PATH = "mp.jwt.verify.publickey.location";
    public static final String CONFIG_ISSUER = "mp.jwt.verify.issuer";

    public static void main(String[] args) {
        Config config = Config.create();

        Optional<PublicKey> key = OptionalHelper.from(
                config.get(CONFIG_PUBLIC_KEY)
                        .value()
                        .map(Starter::loadPkcs8))
                .or(() -> config.get(CONFIG_PUBLIC_KEY_PATH)
                        .value()
                        .map(Starter::loadPkcs8FromUri))
                .asOptional();
    }

    private static PublicKey loadPkcs8FromUri(String uri) {
        return KeyConfig.pemBuilder()
                .key(Resource.from(URI.create(uri)))
                .build()
                .getPublicKey()
                .orElseGet(() -> {
                    return KeyConfig.pemBuilder()
                            .key(Resource.from(uri))
                            .build()
                            .getPublicKey()
                            .orElseThrow(() -> new DeploymentException("Failed to load public key from URI: " + uri));
                });
    }

    private static PublicKey loadPkcs8(String stringContent) {
        return KeyConfig.pemBuilder()
                .key(Resource.fromContent("public key from PKCS8", stringContent))
                .build()
                .getPublicKey()
                .orElseThrow(() -> new DeploymentException("Failed to load public key from string content"));
    }
}
