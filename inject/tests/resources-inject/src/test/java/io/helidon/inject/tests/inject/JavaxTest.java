/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.common.types.TypeName;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.testing.InjectionTestingSupport;

import jakarta.enterprise.inject.Default;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * Javax to Jakarta related tests.
 */
class JavaxTest {
    private InjectionServices injectionServices;
    private Services services;

    @BeforeEach
    void setUp() {
        this.injectionServices = InjectionServices.create();
        this.services = injectionServices.services();
    }

    @AfterEach
    void tearDown() {
        InjectionTestingSupport.shutdown(injectionServices);
    }

    /**
     * Uses {@code inject.mapApplicationToSingletonScope}.
     * This also verifies that the qualifiers were mapped over properly from javax as well.
     */
    @Test
    void applicationScopeToSingletonScopeTranslation() {
        List<ServiceInfo> apScopedList = services.lookupServices(Lookup.create(AnApplicationScopedService.class));

        assertThat(apScopedList, hasSize(1));

        ServiceInfo apScoped = apScopedList.getFirst();
        assertThat(apScoped.serviceType(),
                   equalTo(TypeName.create(AnApplicationScopedService.class)));
        assertThat(apScoped.qualifiers(),
                   contains(Qualifier.create(Default.class)));
        assertThat(apScoped.scope(), is(Injection.Singleton.TYPE_NAME));
    }

}
