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

/**
 * Vault integration SPI.
 * <p>
 * The following SPI classes exist:
 * <ul>
 *     <li>{@link io.helidon.integrations.vault.spi.SecretsEngineProvider} - Secret method provider, such as "Cubbyhole", "PKI"</li>
 *     <li>{@link io.helidon.integrations.vault.spi.AuthMethodProvider} - Authentication method provider, such as "Token"</li>
 * </ul>
 */
package io.helidon.integrations.vault.spi;
