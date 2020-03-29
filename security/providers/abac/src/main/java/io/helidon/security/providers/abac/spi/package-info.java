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

/**
 * Attribute based access control (ABAC) security provider's SPI.
 * Implement {@link io.helidon.security.providers.abac.spi.AbacValidator} and expose it
 * as a java service using {@link io.helidon.security.providers.abac.spi.AbacValidatorService}.
 *
 * @see io.helidon.security.providers.abac.spi.AbacValidator
 * @see io.helidon.security.providers.abac.spi.AbacValidatorService
 */
package io.helidon.security.providers.abac.spi;
