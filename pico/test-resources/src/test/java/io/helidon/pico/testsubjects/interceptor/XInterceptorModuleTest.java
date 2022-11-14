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

package io.helidon.pico.testsubjects.interceptor;

import java.util.List;

import io.helidon.pico.PicoException;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Interceptor;
import io.helidon.pico.PicoServiceProviderException;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.testsupport.BasicSingletonServiceProvider;
import io.helidon.pico.testsupport.TestablePicoServices;
import io.helidon.pico.testsupport.TestableServices;

import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XInterceptorModuleTest {

    private TestableServices services;
    private TestablePicoServices picoServices;

    @BeforeEach
    public void setup() {
        services = new TestableServices();
        picoServices = new TestablePicoServices(services);
        services.bind(picoServices, new XInterceptorModule());
    }

    @Test
    public void testIt() throws Exception {
        {
            List<ServiceProvider<Object>> allServices = services.lookup(DefaultServiceInfo.builder().build());
            assertEquals(
                    "[X_pi$$picoActivator, X$$picoActivator, BasicModule]",
                    String.valueOf(ServiceProvider.toIdentities(allServices)));

            ServiceProvider<X> xProvider = services.lookupFirst(X.class);
            assertEquals(X_pi.class.getName(), xProvider.serviceInfo().serviceTypeName());
            assertFalse(((ServiceProviderBindable) xProvider).isIntercepted());

            List<ServiceProvider<X>> list = services.lookup(X.class);
            assertEquals(
                    "[X_pi$$picoActivator:io.helidon.pico.testsubjects.interceptor.X_pi:INIT, X$$picoActivator:io"
                            + ".helidon.pico.testsubjects.interceptor.X:INIT]",
                    String.valueOf(ServiceProvider.toDescriptions(list)));
            assertFalse(((ServiceProviderBindable) list.get(0)).isIntercepted());
            assertTrue(((ServiceProviderBindable) list.get(1)).isIntercepted());
            assertSame(list.get(0), ((ServiceProviderBindable) list.get(1)).interceptor());

            // runtime aspects
            X xIntercepted = xProvider.get();
            assertNotNull(xIntercepted);
            assertSame(xIntercepted, xProvider.get());

            assertEquals(
                    "[X_pi$$picoActivator:io.helidon.pico.testsubjects.interceptor.X_pi:ACTIVE, X$$picoActivator:io"
                            + ".helidon.pico.testsubjects.interceptor.X:ACTIVE]",
                    String.valueOf(ServiceProvider.toDescriptions(list)));

            xIntercepted.methodIA1();
            xIntercepted.methodIA2();
            assertEquals(101L, xIntercepted.methodX("a", 2, false));
            PicoServiceProviderException pe = assertThrows(PicoServiceProviderException.class, xIntercepted::close);
            assertEquals(
                    "forced: service provider: X$$picoActivator:io.helidon.pico.testsubjects.interceptor.X:ACTIVE",
                    pe.getMessage());
            assertEquals(list.get(1), pe.getServiceProvider());
        }

        // now let's add in an interceptor
        System.out.println("with interception...");
        services.reset();
        services.bind(picoServices, new XInterceptorModule());
        services.bind(picoServices, BasicSingletonServiceProvider
                .createBasicServiceProvider(NamedInterceptor.class,
                                            DefaultServiceInfo.builder()
                                                    .serviceTypeName(NamedInterceptor.class.getName())
                                                    .named(Named.class.getName())
                                                    .externalContractTypeImplemented(Interceptor.class)
                                                    .build()));

        {
            ServiceProvider<X> xProvider = services.lookupFirst(X.class);

            assertEquals(0, NamedInterceptor.ctorCount.get());
            X xIntercepted = xProvider.get();
            assertEquals(1, NamedInterceptor.ctorCount.get());

            xIntercepted.methodIA1();
            xIntercepted.methodIA2();
            assertEquals(202L, xIntercepted.methodX("a", 2, false), "should have been doubled by interceptor");
            PicoException pe = assertThrows(PicoException.class, xIntercepted::close);
            assertEquals(
                    "forced: service provider: X$$picoActivator:io.helidon.pico.testsubjects.interceptor.X:ACTIVE",
                    pe.getMessage());
            assertEquals(1, NamedInterceptor.ctorCount.get());

            RuntimeException re = assertThrows(RuntimeException.class, xIntercepted::throwRuntimeException);
            assertEquals("forced", re.getMessage());
        }
    }

}
