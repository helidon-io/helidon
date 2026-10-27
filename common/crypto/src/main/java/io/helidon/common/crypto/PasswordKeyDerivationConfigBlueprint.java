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

package io.helidon.common.crypto;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Settings for {@link PasswordKeyDerivation}. Password and salt are supplied to each derivation operation.
 *
 * @since 28.0.0
 */
@Prototype.Blueprint
@Api.Stable
interface PasswordKeyDerivationConfigBlueprint extends Prototype.Factory<PasswordKeyDerivation> {

    /**
     * Key derivation algorithm. Defaults to {@code PBKDF2WithHmacSHA256}.
     *
     * @return algorithm name
     */
    @Option.Default("PBKDF2WithHmacSHA256")
    String algorithm();

    /**
     * Explicit JCA provider. If omitted, normal JCA provider selection is used.
     * To remove an explicit selection, use {@link PasswordKeyDerivationConfig.Builder#clearProvider()}
     * instead of passing {@code null} to the setter.
     *
     * @return explicit provider, or empty for normal JCA provider selection
     */
    Optional<String> provider();

    /**
     * Number of iterations used in key derivation. Must be positive and explicitly configured.
     *
     * @return iteration count
     */
    @Option.Required
    int numberOfIterations();

    /**
     * Size of the derived key in bits. Must be positive and explicitly configured.
     *
     * @return key size in bits
     */
    @Option.Required
    int keySize();
}
