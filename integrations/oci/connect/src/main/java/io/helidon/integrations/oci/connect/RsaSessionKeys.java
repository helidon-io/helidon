/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.connect;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;

class RsaSessionKeys implements SessionKeys {
    private static final LazyValue<KeyPairGenerator> KEY_PAIR_GENERATOR = LazyValue.create(() -> {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator;
        } catch (NoSuchAlgorithmException e) {
            throw new OciApiException("Failed to initialize KeyPairGenerator for RSA with 2048 key", e);
        }
    });

    private final AtomicReference<KeyPair> currentPair = new AtomicReference<>();

    private RsaSessionKeys(KeyPair initialKeys) {
        this.currentPair.set(initialKeys);
    }

    static RsaSessionKeys create() {
        return new RsaSessionKeys(KEY_PAIR_GENERATOR.get().generateKeyPair());
    }

    @Override
    public KeyPair keyPair() {
        return currentPair.get();
    }

    @Override
    public KeyPair refresh() {
        KeyPair keyPair = currentPair.get();

        currentPair.compareAndSet(keyPair, KEY_PAIR_GENERATOR.get().generateKeyPair());

        return currentPair.get();
    }
}
