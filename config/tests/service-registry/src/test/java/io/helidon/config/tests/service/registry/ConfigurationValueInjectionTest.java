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

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.Configuration;
import io.helidon.config.MapConfigSource;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@Testing.Test(perMethod = true)
class ConfigurationValueInjectionTest {

    @Test
    void charArraySupplierIsResolvedWhenRequested() {
        CharArraySupplierService service = Services.get(CharArraySupplierService.class);

        Services.set(Config.class,
                     Config.just(MapConfigSource.create(Map.of("app.password", "secret-value"))));

        assertArrayEquals("secret-value".toCharArray(), service.password());
    }

    @Service.Singleton
    static class CharArraySupplierService {
        private final Supplier<char[]> password;

        @Service.Inject
        CharArraySupplierService(@Configuration.Value("app.password") Supplier<char[]> password) {
            this.password = password;
        }

        char[] password() {
            return password.get();
        }
    }
}
