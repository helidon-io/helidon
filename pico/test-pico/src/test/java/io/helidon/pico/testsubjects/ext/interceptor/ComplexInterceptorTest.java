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

package io.helidon.pico.testsubjects.ext.interceptor;

import java.io.File;
import java.util.List;

import io.helidon.pico.PicoException;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.Interceptor;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.testsubjects.interceptor.IA;
import io.helidon.pico.testsubjects.interceptor.IB;
import io.helidon.pico.testsubjects.interceptor.NamedInterceptor;
import io.helidon.pico.testsupport.BasicSingletonServiceProvider;
import io.helidon.pico.testsupport.TestablePicoServices;
import io.helidon.pico.testsupport.TestablePicoServicesConfig;
import io.helidon.pico.testsupport.TestableServices;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.CommonUtils;

import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Also see DefaultInterceptorCreatorTest
 */
public class ComplexInterceptorTest {

    TestableServices services;
    TestablePicoServices picoServices;
    TestablePicoServicesConfig config = new TestablePicoServicesConfig();

    @BeforeEach
    public void setUp() {
        this.services = new TestableServices(config);
        this.picoServices = new TestablePicoServices(services);
    }

    @Test
    public void createInterceptorSource() {
        TypeName interceptorTypeName = DefaultTypeName.create(XImpl$$picoInterceptor.class);
        String path = TypeTools.getFilePath(interceptorTypeName);
        File file = new File("./target/generated-sources/annotations", path);
        assertTrue(file.exists(), file.getPath());
        String java = CommonUtils.loadStringFromFile(file.getPath());
        assertEquals(CommonUtils.loadStringFromResource("expected/ximpl-interceptor-with-names.java_")
                             .trim(), java);
    }

    @Test
    public void runtimeWithNoInterception() throws Exception {
        List<ServiceProvider<IA>> iaProviders = picoServices.services().lookup(IA.class);
        assertEquals(2, iaProviders.size(), iaProviders.toString());
        assertEquals(
                "XImpl$$picoInterceptor$$picoActivator:io.helidon.pico.testsubjects.ext.interceptor"
                        + ".XImpl$$picoInterceptor:INIT",
                ServiceProvider.toDescription(iaProviders.get(0)));
        assertEquals(
                "XImpl$$picoActivator:io.helidon.pico.testsubjects.ext.interceptor.XImpl:INIT",
                     ServiceProvider.toDescription(iaProviders.get(1)));
        List<ServiceProvider<IB>> ibProviders = picoServices.services().lookup(IB.class);
        assertEquals(iaProviders, ibProviders);

        ServiceProvider<XImpl> ximplProvider = picoServices.services().lookupFirst(XImpl.class);
        assertEquals(iaProviders.get(0), ximplProvider);

        XImpl x = ximplProvider.get();
        x.methodIA1();
        x.methodIA2();
        x.methodIB("test");
        long val = x.methodX("a", 2, true);
        assertEquals(101, val);
        assertEquals("methodY", x.methodY());
        assertEquals("methodZ", x.methodZ());
        PicoException pe = assertThrows(PicoException.class, x::close);
        assertEquals(
                "forced: service provider: XImpl$$picoActivator:io.helidon.pico.testsubjects.ext.interceptor"
                        + ".XImpl:ACTIVE",
                pe.getMessage());
        RuntimeException re = assertThrows(RuntimeException.class, x::throwRuntimeException);
        assertEquals("forced", re.getMessage());
    }

    @Test
    public void runtimeWithInterception() throws Exception {
        config.setValue(PicoServicesConfig.KEY_BIND_APPLICATION, false);
        setUp();
        services.bind(picoServices, BasicSingletonServiceProvider
                              .createBasicServiceProvider(NamedInterceptor.class,
                                                          DefaultServiceInfo.builder()
                                                                  .serviceTypeName(NamedInterceptor.class.getName())
                                                                  .named(Named.class.getName())
                                                                  .externalContractTypeImplemented(Interceptor.class)
                                                                  .build()));

        assertEquals(0, NamedInterceptor.ctorCount.get());

        List<ServiceProvider<IA>> iaProviders = picoServices.services().lookup(IA.class);
        assertEquals(2, iaProviders.size(), iaProviders.toString());
        assertEquals(
                "XImpl$$picoInterceptor$$picoActivator:io.helidon.pico.testsubjects.ext.interceptor"
                        + ".XImpl$$picoInterceptor:INIT",
                ServiceProvider.toDescription(iaProviders.get(0)));
        assertEquals(
                "XImpl$$picoActivator:io.helidon.pico.testsubjects.ext.interceptor.XImpl:INIT",
                     ServiceProvider.toDescription(iaProviders.get(1)));
        List<ServiceProvider<IB>> ibProviders = picoServices.services().lookup(IB.class);
        assertEquals(iaProviders, ibProviders);

        ServiceProvider<XImpl> ximplProvider = picoServices.services().lookupFirst(XImpl.class);
        assertEquals(iaProviders.get(0), ximplProvider);

        assertEquals(0, NamedInterceptor.ctorCount.get());
        XImpl xIntercepted = ximplProvider.get();
        assertEquals(1, NamedInterceptor.ctorCount.get());

        xIntercepted.methodIA1();
        xIntercepted.methodIA2();
        xIntercepted.methodIB("test");
        long val = xIntercepted.methodX("a", 2, true);
        assertEquals(202, val, "should have been intercepted");
        assertEquals("methodY", xIntercepted.methodY());
        assertEquals("methodZ", xIntercepted.methodZ());
        PicoException pe = assertThrows(PicoException.class, xIntercepted::close);
        assertEquals(
                "forced: service provider: XImpl$$picoActivator:io.helidon.pico.testsubjects.ext.interceptor"
                        + ".XImpl:ACTIVE",
                pe.getMessage());
        RuntimeException re = assertThrows(RuntimeException.class, xIntercepted::throwRuntimeException);
        assertEquals("forced", re.getMessage());
        assertEquals(1, NamedInterceptor.ctorCount.get());
    }

}
