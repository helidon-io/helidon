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

package io.helidon.pico.testsubjects.ext.stacking.impl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.testsubjects.ext.stacking.Intercepted;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InterceptorStackingTest {

    private Services services;

    @BeforeEach
    public void init() {
        PicoServices picoServices = PicoServices.picoServices().get();
        assert (picoServices instanceof Resetable);
        ((Resetable) picoServices).reset();
        this.services = picoServices.services();
    }

    @Test
    public void interceptorStacking() {
        List<ServiceProvider<Intercepted>> allIntercepted = services.lookup(Intercepted.class);
        assertEquals(
                "[MostOuterInterceptedImpl$$picoActivator:io.helidon.pico.testsubjects.ext.stacking"
                        + ".MostOuterInterceptedImpl:INIT, OuterInterceptedImpl$$picoActivator:io.helidon.pico"
                        + ".testsubjects.ext.stacking.OuterInterceptedImpl:INIT, "
                        + "InterceptedImpl$$picoActivator:io.helidon.pico.testsubjects.ext.stacking"
                        + ".InterceptedImpl:INIT, "
                        + "TestingSingleton$$picoActivator:io.helidon.pico.testsubjects.ext.tbox"
                        + ".TestingSingleton:INIT]",
                     ServiceProvider.toDescriptions(allIntercepted).toString());
        List<String> injections = allIntercepted.stream().map(sp -> {
            Intercepted inner = sp.get().getInner();
            return sp.serviceInfo().serviceTypeName() + " injected with "
                    + (Objects.isNull(inner) ? null : inner.getClass());
        }).collect(Collectors.toList());
        assertEquals("[io.helidon.pico.testsubjects.ext.stacking.MostOuterInterceptedImpl injected with class io.helidon"
                        + ".pico.testsubjects.ext.stacking.OuterInterceptedImpl, io.helidon.pico.testsubjects.ext"
                        + ".stacking.OuterInterceptedImpl injected with class io.helidon.pico.testsubjects.ext"
                        + ".stacking.InterceptedImpl, io.helidon.pico.testsubjects.ext.stacking.InterceptedImpl "
                        + "injected with null, io.helidon.pico.testsubjects.ext.tbox.TestingSingleton injected with "
                        + "class io.helidon.pico.testsubjects.ext.stacking.MostOuterInterceptedImpl]",
                injections.toString());
        assertEquals("MostOuterInterceptedImpl:OuterInterceptedImpl:InterceptedImpl:arg",
                     services.lookupFirst(Intercepted.class).get().sayHello("arg"));
    }

}
