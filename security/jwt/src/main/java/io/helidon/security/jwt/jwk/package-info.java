/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * JWK (JSON web key) support.
 * Classes in this package allow you to read JWK definitions from Json ({@link io.helidon.security.jwt.jwk.JwkKeys}) and
 * to use them for signatures ({@link io.helidon.security.jwt.jwk.Jwk#sign(byte[])} and {@link
 * io.helidon.security.jwt.jwk.Jwk#verifySignature(byte[], byte[])}).
 *
 * @see io.helidon.security.jwt.jwk.Jwk#create(javax.json.JsonObject)
 * @see io.helidon.security.jwt.jwk.JwkEC
 * @see io.helidon.security.jwt.jwk.JwkRSA
 * @see io.helidon.security.jwt.jwk.JwkOctet
 */
package io.helidon.security.jwt.jwk;
