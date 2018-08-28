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

package io.helidon.security.provider.httpauth;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;

/**
 * User store loaded from configuration.
 */
public class ConfigUserStore implements UserStore {
    private final Map<String, User> users = new HashMap<>();

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
     *   password = "${AES=3XQ8A1RszE9JbXl+lUnnsX0gakuqjnTyp8YJWIAU1D3SiM2TaSnxd6U0/LjrdJYv}"
     *   roles = ["user"]
     * }
     * ]
     * </pre>
     *
     * @param config to load this user store from
     * @return {@link UserStore} instance
     */
    public static ConfigUserStore fromConfig(Config config) {
        ConfigUserStore store = new ConfigUserStore();

        config.nodeList().ifPresent(configs -> configs.forEach(config1 -> {
            User user = config1.map(ConfigUser::fromConfig);
            store.users.put(user.getLogin(), user);
        }));

        return store;
    }

    @Override
    public Optional<User> getUser(String login) {
        return Optional.ofNullable(users.get(login));
    }

    private static class ConfigUser implements User {
        private final Set<String> roles = new LinkedHashSet<>();
        private String login;
        private char[] password;

        static ConfigUser fromConfig(Config config) {
            ConfigUser cu = new ConfigUser();

            cu.login = config.get("login").asString();
            cu.password = config.get("password").asString("").toCharArray();
            cu.roles.addAll(config.get("roles").asStringList(CollectionsHelper.listOf()));

            return cu;
        }

        @Override
        public String getLogin() {
            return login;
        }

        @Override
        public char[] getPassword() {
            return password;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }

        @Override
        public String toString() {
            return "User info for \"" + login + "\"";
        }
    }
}
