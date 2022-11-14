/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.fake.helidon.config;

/**
 * Indicates whether the server requires authentication of tbe client by the certificate.
 */
public enum FakeClientAuth {

    /**
     * Authentication is required.
     */
    REQUIRE(FakeNettyClientAuth.REQUIRE),

    /**
     * Authentication is optional.
     */
    OPTIONAL(FakeNettyClientAuth.OPTIONAL),

    /**
     * Authentication is not required.
     */
    NONE(FakeNettyClientAuth.NONE);

    private final FakeNettyClientAuth clientAuth;

    FakeClientAuth(FakeNettyClientAuth clientAuth) {
        this.clientAuth = clientAuth;
    }

    FakeNettyClientAuth nettyClientAuth(){
        return clientAuth;
    }

}
