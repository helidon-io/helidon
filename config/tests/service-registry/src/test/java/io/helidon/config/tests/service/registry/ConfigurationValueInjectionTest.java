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

package io.helidon.config.tests.service.registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.Configuration;
import io.helidon.config.spi.ConfigNode;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testing.Test(perMethod = true)
class ConfigurationValueInjectionTest {
    private static final String PASSWORD = "secret-value";
    private static final List<String> PASSWORDS = List.of("first-secret", "second-secret");

    @Test
    void charArrayInjectionVariantsAreSupported() {
        CharArraySupplierService supplierService = Services.get(CharArraySupplierService.class);

        Services.set(Config.class, config());
        CharArrayService service = Services.get(CharArrayService.class);

        assertArrayEquals(PASSWORD.toCharArray(), service.password());
        assertOptionalPassword(service.optionalPassword());
        assertPasswords(service.passwords());
        assertArrayEquals(PASSWORD.toCharArray(), supplierService.password());
        assertOptionalPassword(supplierService.optionalPassword());
        assertPasswords(supplierService.passwords());
    }

    private static Config config() {
        ConfigNode.ListNode.Builder passwords = ConfigNode.ListNode.builder();
        PASSWORDS.forEach(passwords::addValue);
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addValue("app.password", PASSWORD)
                .addList("app.passwords", passwords.build())
                .build();
        return Config.just(ConfigSources.create(root));
    }

    private static void assertOptionalPassword(Optional<char[]> password) {
        assertTrue(password.isPresent());
        assertArrayEquals(PASSWORD.toCharArray(), password.orElseThrow());
    }

    private static void assertPasswords(List<char[]> passwords) {
        assertEquals(PASSWORDS.size(), passwords.size());
        for (int i = 0; i < PASSWORDS.size(); i++) {
            assertArrayEquals(PASSWORDS.get(i).toCharArray(), passwords.get(i));
        }
    }

    @Service.Singleton
    static class CharArrayService {
        private final char[] password;
        private final Optional<char[]> optionalPassword;
        private final List<char[]> passwords;

        @Service.Inject
        CharArrayService(@Configuration.Value("app.password") char[] password,
                         @Configuration.Value("app.password") Optional<char[]> optionalPassword,
                         @Configuration.Value("app.passwords") List<char[]> passwords) {
            this.password = password;
            this.optionalPassword = optionalPassword;
            this.passwords = passwords;
        }

        char[] password() {
            return password;
        }

        Optional<char[]> optionalPassword() {
            return optionalPassword;
        }

        List<char[]> passwords() {
            return passwords;
        }
    }

    @Service.Singleton
    static class CharArraySupplierService {
        private final Supplier<char[]> password;
        private final Supplier<Optional<char[]>> optionalPassword;
        private final Supplier<List<char[]>> passwords;

        @Service.Inject
        CharArraySupplierService(@Configuration.Value("app.password") Supplier<char[]> password,
                                 @Configuration.Value("app.password") Supplier<Optional<char[]>> optionalPassword,
                                 @Configuration.Value("app.passwords") Supplier<List<char[]>> passwords) {
            this.password = password;
            this.optionalPassword = optionalPassword;
            this.passwords = passwords;
        }

        char[] password() {
            return password.get();
        }

        Optional<char[]> optionalPassword() {
            return optionalPassword.get();
        }

        List<char[]> passwords() {
            return passwords.get();
        }
    }
}
