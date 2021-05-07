/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.security.providers.httpauth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Testing user.
 */
class TestUser implements SecureUserStore.User {
    private final String username;
    private final char[] password;
    private Collection<String> roles;

    TestUser(String username, char[] password) {
        this.username = username;
        this.password = password;
        this.roles = Collections.emptyList();
    }

    TestUser(String username, char[] password, Collection<String> roles) {
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    @Override
    public Collection<String> roles() {
        return roles;
    }

    @Override
    public String login() {
        return username;
    }

    @Override
    public boolean isPasswordValid(char[] passwordFromRequest) {
        return Arrays.equals(passwordFromRequest, password);
    }

    @Override
    public Optional<String> digestHa1(String realm, HttpDigest.Algorithm algorithm) {
        return Optional.of(DigestToken.ha1(algorithm, realm, login(), password));
    }

}
