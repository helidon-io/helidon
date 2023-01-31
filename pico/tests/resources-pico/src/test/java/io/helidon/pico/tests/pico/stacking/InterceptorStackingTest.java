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

package io.helidon.pico.tests.pico.stacking;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.testing.PicoTestingSupport;
import io.helidon.pico.types.DefaultTypeName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class InterceptorStackingTest {

    Config config = PicoTestingSupport.basicTesableConfig();
    PicoServices picoServices;
    Services services;

    @BeforeEach
    void setUp() {
        setUp(config);
    }

    void setUp(
            Config config) {
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void interceptorStacking() {
        List<ServiceProvider<Intercepted>> allIntercepted = services.lookupAll(Intercepted.class);
        List<String> desc = allIntercepted.stream().map(ServiceProvider::description).collect(Collectors.toList());
        // order matters here
        assertThat(desc, contains(
                "MostOuterInterceptedImpl:INIT",
                "OuterInterceptedImpl:INIT",
                "InterceptedImpl:INIT",
                "TestingSingleton:INIT"));

        List<String> injections = allIntercepted.stream().map(sp -> {
            Intercepted inner = sp.get().getInner();
            return DefaultTypeName.createFromTypeName(sp.serviceInfo().serviceTypeName()).className() + " injected with "
                    + (inner == null ? null : inner.getClass().getSimpleName());
        }).collect(Collectors.toList());
        // order matters here
        assertThat(injections,
                   contains("MostOuterInterceptedImpl injected with OuterInterceptedImpl",
                            "OuterInterceptedImpl injected with InterceptedImpl",
                            "InterceptedImpl injected with null",
                            "TestingSingleton injected with MostOuterInterceptedImpl"));
        assertThat(services.lookup(Intercepted.class).get().sayHello("arg"),
                   equalTo("MostOuterInterceptedImpl:OuterInterceptedImpl:InterceptedImpl:arg"));
    }

}
