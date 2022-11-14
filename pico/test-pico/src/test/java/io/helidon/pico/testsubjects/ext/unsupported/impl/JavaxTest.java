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

package io.helidon.pico.testsubjects.ext.unsupported.impl;

import java.util.LinkedHashSet;

import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.ExtendedServices;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.testsubjects.ext.unsupported.AnApplicationScopedService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Javax => Jakarta related tests.
 */
public class JavaxTest {
    private PicoServices picoServices;
    private Services services;
    private ExtendedServices extendedServices;

    @BeforeEach
    public void init() {
        PicoServices picoServices = PicoServices.picoServices().get();
        ((Resetable) picoServices).reset();
        this.picoServices = picoServices;
        this.services = picoServices.services();
        assert (services instanceof ExtendedServices);
        this.extendedServices = (ExtendedServices) services;
    }

    /**
     * Uses {@link io.helidon.pico.tools.processor.Options#TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE}.
     * This also verifies that the qualifiers were mapped over properly from javax as well.
     */
    @Test
    public void applicationScopeToSingletonScopeTranslation() {
        ServiceProvider<AnApplicationScopedService> sp = services.lookupFirst(AnApplicationScopedService.class);
        assertEquals(
                "AnApplicationScopedService$$picoActivator:io.helidon.pico.testsubjects.ext.unsupported.AnApplicationScopedService:INIT",
                     ServiceProvider.toDescription(sp));
        assertEquals("[DefaultQualifierAndValue(typeName=jakarta.enterprise.inject.Default)]",
                     sp.serviceInfo().qualifiers().toString());
        LinkedHashSet expected = new LinkedHashSet();
        expected.add(Singleton.class.getName());
        expected.add(ApplicationScoped.class.getName());
        assertEquals(expected, sp.serviceInfo().scopeTypeNames());
    }

}
