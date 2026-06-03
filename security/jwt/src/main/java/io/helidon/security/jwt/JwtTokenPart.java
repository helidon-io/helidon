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

package io.helidon.security.jwt;

enum JwtTokenPart {
    JWT_HEADER("JWT header"),
    JWT_PAYLOAD("JWT payload"),
    JWT_SIGNATURE("JWT signature"),
    JWE_ENCRYPTED_KEY("JWE encrypted key"),
    JWE_INITIALIZATION_VECTOR("JWE initialization vector"),
    JWE_PAYLOAD("JWE payload"),
    JWE_AUTHENTICATION_TAG("JWE authentication tag");

    private final String description;

    JwtTokenPart(String description) {
        this.description = description;
    }

    String text() {
        return description;
    }
}
