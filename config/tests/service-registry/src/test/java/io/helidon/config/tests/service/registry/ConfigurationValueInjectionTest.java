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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Default;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.Configuration;
import io.helidon.config.spi.ConfigNode;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testing.Test(perMethod = true)
class ConfigurationValueInjectionTest {
    private static final String PASSWORD = "secret-value";
    private static final List<String> PASSWORDS = List.of("first-secret", "second-secret");
    private static final String INLINE_DEFAULT_PASSWORD = "inline-secret";
    private static final String ANNOTATION_DEFAULT_PASSWORD = "annotation-default-secret";
    private static final String COMPOSITE_PASSWORD = "prefix-" + PASSWORD + "-suffix";
    private static final String INFERRED_PASSWORD_KEY =
            CharArrayResolutionService.class.getCanonicalName() + ".inferredPassword";

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

    @Test
    void supplierCharArrayValuesAreIndependent() {
        Services.set(Config.class, config());
        CharArraySupplierService service = Services.get(CharArraySupplierService.class);

        char[] password = service.password();
        char[] optionalPassword = service.optionalPassword().orElseThrow();
        List<char[]> passwords = service.passwords();

        Arrays.fill(password, '\0');
        Arrays.fill(optionalPassword, '\0');
        passwords.forEach(value -> Arrays.fill(value, '\0'));

        char[] nextPassword = service.password();
        char[] nextOptionalPassword = service.optionalPassword().orElseThrow();
        List<char[]> nextPasswords = service.passwords();

        assertNotSame(password, nextPassword);
        assertArrayEquals(PASSWORD.toCharArray(), nextPassword);
        assertNotSame(optionalPassword, nextOptionalPassword);
        assertArrayEquals(PASSWORD.toCharArray(), nextOptionalPassword);
        assertEquals(passwords.size(), nextPasswords.size());
        for (int i = 0; i < passwords.size(); i++) {
            assertNotSame(passwords.get(i), nextPasswords.get(i));
            assertArrayEquals(PASSWORDS.get(i).toCharArray(), nextPasswords.get(i));
        }
    }

    @Test
    void serviceInstanceCharArrayInjectionVariantsAreSupported() {
        CharArrayServiceInstanceSupplierService supplierService =
                Services.get(CharArrayServiceInstanceSupplierService.class);

        Services.set(Config.class, config());
        CharArrayServiceInstanceService service = Services.get(CharArrayServiceInstanceService.class);

        assertServiceInstancePassword(service.password());
        assertOptionalServiceInstancePassword(service.optionalPassword());
        assertServiceInstancePasswords(service.passwords());
        assertServiceInstancePassword(supplierService.password());
        assertOptionalServiceInstancePassword(supplierService.optionalPassword());
        assertServiceInstancePasswords(supplierService.passwords());
    }

    @Test
    void charArrayResolutionVariantsAreSupported() {
        Services.set(Config.class, config());
        CharArrayResolutionService service = Services.get(CharArrayResolutionService.class);

        assertArrayEquals(PASSWORD.toCharArray(), service.inferredPassword());
        assertArrayEquals(new char[0], service.emptyPassword());
        assertArrayEquals(INLINE_DEFAULT_PASSWORD.toCharArray(), service.inlineDefaultPassword());
        assertArrayEquals(ANNOTATION_DEFAULT_PASSWORD.toCharArray(), service.annotationDefaultPassword());
        assertArrayEquals(COMPOSITE_PASSWORD.toCharArray(), service.compositePassword());
    }

    @Test
    void missingOptionalAndListCharArrayInjectionVariantsAreEmpty() {
        Services.set(Config.class, Config.empty());
        MissingCharArrayService service = Services.get(MissingCharArrayService.class);

        assertTrue(service.optionalPassword().isEmpty());
        assertTrue(service.passwords().isEmpty());
        assertTrue(service.suppliedOptionalPassword().isEmpty());
        assertTrue(service.suppliedPasswords().isEmpty());
        assertTrue(service.optionalServiceInstancePassword().isEmpty());
        assertTrue(service.serviceInstancePasswords().isEmpty());
        assertTrue(service.suppliedOptionalServiceInstancePassword().isEmpty());
        assertTrue(service.suppliedServiceInstancePasswords().isEmpty());
    }

    private static Config config() {
        ConfigNode.ListNode.Builder passwords = ConfigNode.ListNode.builder();
        PASSWORDS.forEach(passwords::addValue);
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addValue("app.password", PASSWORD)
                .addList("app.passwords", passwords.build())
                .addValue("app.empty", "")
                .addValue(INFERRED_PASSWORD_KEY, PASSWORD)
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

    private static void assertServiceInstancePassword(ServiceInstance<char[]> password) {
        assertArrayEquals(PASSWORD.toCharArray(), password.get());
    }

    private static void assertOptionalServiceInstancePassword(Optional<ServiceInstance<char[]>> password) {
        assertTrue(password.isPresent());
        assertServiceInstancePassword(password.orElseThrow());
    }

    private static void assertServiceInstancePasswords(List<ServiceInstance<char[]>> passwords) {
        assertEquals(PASSWORDS.size(), passwords.size());
        for (int i = 0; i < PASSWORDS.size(); i++) {
            assertArrayEquals(PASSWORDS.get(i).toCharArray(), passwords.get(i).get());
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

    @Service.Singleton
    static class CharArrayServiceInstanceService {
        private final ServiceInstance<char[]> password;
        private final Optional<ServiceInstance<char[]>> optionalPassword;
        private final List<ServiceInstance<char[]>> passwords;

        @Service.Inject
        CharArrayServiceInstanceService(
                @Configuration.Value("app.password") ServiceInstance<char[]> password,
                @Configuration.Value("app.password") Optional<ServiceInstance<char[]>> optionalPassword,
                @Configuration.Value("app.passwords") List<ServiceInstance<char[]>> passwords) {
            this.password = password;
            this.optionalPassword = optionalPassword;
            this.passwords = passwords;
        }

        ServiceInstance<char[]> password() {
            return password;
        }

        Optional<ServiceInstance<char[]>> optionalPassword() {
            return optionalPassword;
        }

        List<ServiceInstance<char[]>> passwords() {
            return passwords;
        }
    }

    @Service.Singleton
    static class CharArrayServiceInstanceSupplierService {
        private final Supplier<ServiceInstance<char[]>> password;
        private final Supplier<Optional<ServiceInstance<char[]>>> optionalPassword;
        private final Supplier<List<ServiceInstance<char[]>>> passwords;

        @Service.Inject
        CharArrayServiceInstanceSupplierService(
                @Configuration.Value("app.password") Supplier<ServiceInstance<char[]>> password,
                @Configuration.Value("app.password") Supplier<Optional<ServiceInstance<char[]>>> optionalPassword,
                @Configuration.Value("app.passwords") Supplier<List<ServiceInstance<char[]>>> passwords) {
            this.password = password;
            this.optionalPassword = optionalPassword;
            this.passwords = passwords;
        }

        ServiceInstance<char[]> password() {
            return password.get();
        }

        Optional<ServiceInstance<char[]>> optionalPassword() {
            return optionalPassword.get();
        }

        List<ServiceInstance<char[]>> passwords() {
            return passwords.get();
        }
    }

    @Service.Singleton
    static class CharArrayResolutionService {
        private final char[] inferredPassword;
        private char[] emptyPassword;
        private char[] inlineDefaultPassword;
        private char[] annotationDefaultPassword;
        private char[] compositePassword;

        @Service.Inject
        CharArrayResolutionService(@Configuration.Value char[] inferredPassword) {
            this.inferredPassword = inferredPassword;
        }

        @Service.Inject
        void configure(@Configuration.Value("app.empty") char[] emptyPassword,
                       @Configuration.Value("${app.missing:inline-secret}") char[] inlineDefaultPassword,
                       @Configuration.Value("app.missing.password")
                       @Default.Value(ANNOTATION_DEFAULT_PASSWORD) char[] annotationDefaultPassword,
                       @Configuration.Value("prefix-${app.password}-suffix") char[] compositePassword) {
            this.emptyPassword = emptyPassword;
            this.inlineDefaultPassword = inlineDefaultPassword;
            this.annotationDefaultPassword = annotationDefaultPassword;
            this.compositePassword = compositePassword;
        }

        char[] inferredPassword() {
            return inferredPassword;
        }

        char[] emptyPassword() {
            return emptyPassword;
        }

        char[] inlineDefaultPassword() {
            return inlineDefaultPassword;
        }

        char[] annotationDefaultPassword() {
            return annotationDefaultPassword;
        }

        char[] compositePassword() {
            return compositePassword;
        }
    }

    @Service.Singleton
    static class MissingCharArrayService {
        private final Optional<char[]> optionalPassword;
        private final List<char[]> passwords;
        private final Supplier<Optional<char[]>> suppliedOptionalPassword;
        private final Supplier<List<char[]>> suppliedPasswords;
        private final Optional<ServiceInstance<char[]>> optionalServiceInstancePassword;
        private final List<ServiceInstance<char[]>> serviceInstancePasswords;
        private final Supplier<Optional<ServiceInstance<char[]>>> suppliedOptionalServiceInstancePassword;
        private final Supplier<List<ServiceInstance<char[]>>> suppliedServiceInstancePasswords;

        @Service.Inject
        MissingCharArrayService(
                @Configuration.Value("app.missing.password") Optional<char[]> optionalPassword,
                @Configuration.Value("app.missing.passwords") List<char[]> passwords,
                @Configuration.Value("app.missing.password") Supplier<Optional<char[]>> suppliedOptionalPassword,
                @Configuration.Value("app.missing.passwords") Supplier<List<char[]>> suppliedPasswords,
                @Configuration.Value("app.missing.password")
                Optional<ServiceInstance<char[]>> optionalServiceInstancePassword,
                @Configuration.Value("app.missing.passwords")
                List<ServiceInstance<char[]>> serviceInstancePasswords,
                @Configuration.Value("app.missing.password")
                Supplier<Optional<ServiceInstance<char[]>>> suppliedOptionalServiceInstancePassword,
                @Configuration.Value("app.missing.passwords")
                Supplier<List<ServiceInstance<char[]>>> suppliedServiceInstancePasswords) {
            this.optionalPassword = optionalPassword;
            this.passwords = passwords;
            this.suppliedOptionalPassword = suppliedOptionalPassword;
            this.suppliedPasswords = suppliedPasswords;
            this.optionalServiceInstancePassword = optionalServiceInstancePassword;
            this.serviceInstancePasswords = serviceInstancePasswords;
            this.suppliedOptionalServiceInstancePassword = suppliedOptionalServiceInstancePassword;
            this.suppliedServiceInstancePasswords = suppliedServiceInstancePasswords;
        }

        Optional<char[]> optionalPassword() {
            return optionalPassword;
        }

        List<char[]> passwords() {
            return passwords;
        }

        Optional<char[]> suppliedOptionalPassword() {
            return suppliedOptionalPassword.get();
        }

        List<char[]> suppliedPasswords() {
            return suppliedPasswords.get();
        }

        Optional<ServiceInstance<char[]>> optionalServiceInstancePassword() {
            return optionalServiceInstancePassword;
        }

        List<ServiceInstance<char[]>> serviceInstancePasswords() {
            return serviceInstancePasswords;
        }

        Optional<ServiceInstance<char[]>> suppliedOptionalServiceInstancePassword() {
            return suppliedOptionalServiceInstancePassword.get();
        }

        List<ServiceInstance<char[]>> suppliedServiceInstancePasswords() {
            return suppliedServiceInstancePasswords.get();
        }
    }
}
