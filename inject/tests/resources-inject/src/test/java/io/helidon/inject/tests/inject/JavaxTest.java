/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.inject;

import io.helidon.common.types.TypeName;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.Services;
import io.helidon.inject.codegen.InjectCodegenTypes;
import io.helidon.inject.service.Qualifier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.basicTestableConfig;
import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

/**
 * Javax to Jakarta related tests.
 */
class JavaxTest {
    private static final InjectionConfig CONFIG = basicTestableConfig();

    private Services services;

    @BeforeEach
    void setUp() {
        this.services = testableServices(CONFIG).services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    /**
     * Uses {@code inject.mapApplicationToSingletonScope}.
     * This also verifies that the qualifiers were mapped over properly from javax as well.
     */
    @Test
    void applicationScopeToSingletonScopeTranslation() {
        ServiceProvider<AnApplicationScopedService> sp = services.serviceProviders().get(AnApplicationScopedService.class);
        assertThat(sp.toString(),
                   equalTo("AnApplicationScopedService:INIT"));
        assertThat(sp.qualifiers(),
                   contains(Qualifier.create(Default.class)));
        assertThat(sp.scopes(),
                   containsInAnyOrder(InjectCodegenTypes.INJECTION_SINGLETON,
                                      TypeName.create(ApplicationScoped.class)));
    }

}
