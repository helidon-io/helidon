/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * User store loaded from configuration.
 */
public class ConfigUserStore implements SecureUserStore {
    private final Map<String, ConfigUser> users = new HashMap<>();

    /**
     * Create an instance from config. Expects key "users" to be the current key.
     * Example:
     * <pre>
     * users: [
     * {
     *   login = "jack"
     *   password = "${CLEAR=password}"
     *   roles = ["user", "admin"]
     * },
     * {
     *   login = "jill"
     *   # master password is "jungle", password is "password"
     *   password = "${GCM=3XQ8A1RszE9JbXl+lUnnsX0gakuqjnTyp8YJWIAU1D3SiM2TaSnxd6U0/LjrdJYv}"
     *   roles = ["user"]
     * }
     * ]
     * </pre>
     *
     * @param config to load this user store from
     * @return {@link io.helidon.security.providers.httpauth.SecureUserStore} instance
     */
    public static SecureUserStore create(Config config) {
        ConfigUserStore store = new ConfigUserStore();

        config.asNodeList().ifPresent(configs -> configs.forEach(config1 -> {
            ConfigUser user = config1.as(ConfigUser::create).get();
            store.users.put(user.login(), user);
        }));

        return store;
    }

    @Override
    public Optional<User> user(String login) {
        return Optional.ofNullable(users.get(login));
    }

    /**
     * A user that is loaded from configuration.
     */
    @Configured
    public static class ConfigUser implements User {

        private final Set<String> roles;
        private final String login;
        private final char[] password;

        private ConfigUser(String login, char[] password, List<String> roles) {
            this.login = login;
            this.password = password;
            this.roles = new LinkedHashSet<>(roles);
        }

        /**
         * Create a new user from configuration.
         * The configuration must be located on the user's node.
         *
         * @param config configuration instance
         * @return a new config user with the configured login, password, and roles
         */
        @ConfiguredOption(key = "login", type = String.class, description = "User's login")
        @ConfiguredOption(key = "password", type = String.class, description = "User's password")
        @ConfiguredOption(key = "roles",
                          type = String.class,
                          kind = ConfiguredOption.Kind.LIST,
                          description = "List of roles the user is in")
        // method must be public so the annotation processor sees it
        public static ConfigUser create(Config config) {
            String login = config.get("login").asString().get();
            char[] password = config.get("password").asString().orElse("").toCharArray();
            List<String> roles = config.get("roles").asList(String.class).orElse(List.of());

            return new ConfigUser(login, password, roles);
        }

        @Override
        public String login() {
            return login;
        }

        @Override
        public boolean isPasswordValid(char[] passwordA) {
            if (passwordA == null) {
                return false;
            }

            char[] passwordB = this.password;

            if (passwordB.length == 0) {
                return passwordA.length == 0;
            }

            int expectedLength = passwordB.length;
            int diff = passwordA.length - expectedLength;

            for (int i = 0; i < passwordA.length; i++) {
                int expectedIndex = ((i - expectedLength) >>> 31) * i;
                diff |= passwordA[i] ^ passwordB[expectedIndex];
            }

            return diff == 0;
        }

        @Override
        public Set<String> roles() {
            return roles;
        }

        @Override
        public String toString() {
            return "User info for \"" + login + "\"";
        }
    }
}
