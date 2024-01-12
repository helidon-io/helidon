/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.qualified.providers;

import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class QualifiedProvidersTest {
    @Test
    public void testQualifiedProvidersNoApp() {
        InjectionServices injectionServices = InjectionServices.create(InjectionConfig.builder()
                                                                               .useApplication(false)
                                                                               .build());
        try {
            testServices(injectionServices.services());
        } finally {
            InjectionTestingSupport.shutdown(injectionServices);
        }
    }

    @Test
    public void testQualifiedProvidersWithApp() {
        InjectionServices injectionServices = InjectionServices.create();
        try {
            testServices(injectionServices.services());
        } finally {
            InjectionTestingSupport.shutdown(injectionServices);
        }
    }

    private void testServices(Services services) {
        TheService theService = services.get(TheService.class);

        assertThat(theService.first(), is("first"));
        assertThat(theService.second(), is(49));
        assertThat(theService.firstContract().name(), is("first"));
        assertThat(theService.secondContract().name(), is("second"));
    }
}
