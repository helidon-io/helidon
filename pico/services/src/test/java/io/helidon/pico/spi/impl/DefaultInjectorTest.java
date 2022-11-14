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

package io.helidon.pico.spi.impl;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.pico.ActivationResult;
import io.helidon.pico.ActivationStatus;
import io.helidon.pico.InjectionException;
import io.helidon.pico.Injector;
import io.helidon.pico.PicoServices;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultInjectorTest {
    PicoServices picoServices;

    @BeforeEach
    public void init() {
        Optional<PicoServices> picoServices = PicoServices.picoServices();
        ((DefaultPicoServices)picoServices.get()).reset();
        this.picoServices = picoServices.get();
    }

    @Test
    public void notADefaultServiceProvider() {
        Injector injector = picoServices.injector().get();
        assertNotNull(injector);
        assertThrows(InjectionException.class, () -> injector.activateInject(this, null));

        AtomicReference<ActivationResult<DefaultInjectorTest>> resultHolder = new AtomicReference<>();
        injector.activateInject(this, resultHolder);
        assertNotNull(resultHolder.get());
        Assertions.assertEquals(ActivationStatus.FAILURE, resultHolder.get().finishingStatus());
        assertEquals("io.helidon.pico.spi.InjectionException: this provider only supports the default ServiceProvider targets",
                resultHolder.get().error().toString());

        ActivationResult<?> result = injector.deactivate(this);
        assertEquals(ActivationStatus.FAILURE, result.finishingStatus());
        assertEquals("io.helidon.pico.spi.InjectionException: this provider only supports the default ServiceProvider targets",
                result.error().toString());
    }

}
