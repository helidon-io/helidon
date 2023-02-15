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

package io.helidon.pico.tests.pico.interceptor;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.Interceptor;
import io.helidon.pico.PicoException;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.testing.BasicSingletonServiceProvider;
import io.helidon.pico.tests.plain.interceptor.IA;
import io.helidon.pico.tests.plain.interceptor.IB;
import io.helidon.pico.tests.plain.interceptor.NamedInterceptor;

import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.PicoServicesConfig.KEY_PERMITS_DYNAMIC;
import static io.helidon.pico.PicoServicesConfig.KEY_USES_COMPILE_TIME_APPLICATIONS;
import static io.helidon.pico.PicoServicesConfig.KEY_USES_COMPILE_TIME_MODULES;
import static io.helidon.pico.PicoServicesConfig.NAME;
import static io.helidon.pico.testing.PicoTestingSupport.basicTesableConfig;
import static io.helidon.pico.testing.PicoTestingSupport.bind;
import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static io.helidon.pico.testing.PicoTestingSupport.toDescriptions;
import static io.helidon.pico.tests.pico.TestUtils.loadStringFromResource;
import static io.helidon.pico.tools.TypeTools.toFilePath;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplexInterceptorTest {

    Config config = basicTesableConfig();
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
    void createInterceptorSource() throws Exception {
        TypeName interceptorTypeName = DefaultTypeName.create(XImpl$$picoInterceptor.class);
        String path = toFilePath(interceptorTypeName);
        File file = new File("./target/generated-sources/annotations", path);
        assertThat(file.exists(), is(true));
        String java = Files.readString(file.toPath());
        assertThat(loadStringFromResource("expected/ximpl-interceptor._java_"),
                   equalTo(java));
    }

    @Test
    void runtimeWithNoInterception() throws Exception {
        List<ServiceProvider<IA>> iaProviders = services.lookupAll(IA.class);
        assertThat(toDescriptions(iaProviders),
                   contains("XImpl$$picoInterceptor:INIT", "XImpl:INIT"));

        List<ServiceProvider<IB>> ibProviders = services.lookupAll(IB.class);
        assertThat(iaProviders, equalTo(ibProviders));

        ServiceProvider<XImpl> ximplProvider = services.lookupFirst(XImpl.class);
        assertThat(iaProviders.get(0), is(ximplProvider));

        XImpl x = ximplProvider.get();
        x.methodIA1();
        x.methodIA2();
        x.methodIB("test");
        long val = x.methodX("a", 2, true);
        assertThat(val, equalTo(101L));
        assertThat(x.methodY(), equalTo("methodY"));
        assertThat(x.methodZ(), equalTo("methodZ"));
        PicoException pe = assertThrows(PicoException.class, x::close);
        assertThat(pe.getMessage(),
                   equalTo("forced: service provider: XImpl:ACTIVE"));
        RuntimeException re = assertThrows(RuntimeException.class, x::throwRuntimeException);
        assertThat(re.getMessage(), equalTo("forced"));
    }

    @Test
    void runtimeWithInterception() throws Exception {
        // disable application and modules to affectively start with an empty registry.
        Config config = Config.create(
                ConfigSources.create(
                        Map.of(NAME + "." + KEY_PERMITS_DYNAMIC, "true",
                               NAME + "." + KEY_USES_COMPILE_TIME_APPLICATIONS, "false",
                               NAME + "." + KEY_USES_COMPILE_TIME_MODULES, "true"),
                        "config-1"));
        tearDown();
        setUp(config);
        bind(picoServices, BasicSingletonServiceProvider
                              .create(NamedInterceptor.class,
                                      DefaultServiceInfo.builder()
                                              .serviceTypeName(NamedInterceptor.class.getName())
                                              .addQualifier(DefaultQualifierAndValue.createNamed(Named.class.getName()))
                                              .addExternalContractsImplemented(Interceptor.class.getName())
                                              .build()));
        assertThat(NamedInterceptor.ctorCount.get(), equalTo(0));

        List<ServiceProvider<IA>> iaProviders = picoServices.services().lookupAll(IA.class);
        assertThat(toDescriptions(iaProviders),
                   contains("XImpl$$picoInterceptor:INIT", "XImpl:INIT"));

        List<ServiceProvider<IB>> ibProviders = services.lookupAll(IB.class);
        assertThat(iaProviders, equalTo(ibProviders));

        ServiceProvider<XImpl> ximplProvider = services.lookupFirst(XImpl.class);
        assertThat(iaProviders.get(0), is(ximplProvider));

        assertThat(NamedInterceptor.ctorCount.get(), equalTo(0));
        XImpl xIntercepted = ximplProvider.get();
        assertThat(NamedInterceptor.ctorCount.get(), equalTo(1));

        xIntercepted.methodIA1();
        xIntercepted.methodIA2();
        xIntercepted.methodIB("test");
        long val = xIntercepted.methodX("a", 2, true);
        assertThat(val, equalTo(202L));
        assertThat(xIntercepted.methodY(), equalTo("methodY"));
        assertThat(xIntercepted.methodZ(), equalTo("methodZ"));
        PicoException pe = assertThrows(PicoException.class, xIntercepted::close);
        assertThat(pe.getMessage(),
                   equalTo("forced: service provider: XImpl:ACTIVE"));
        RuntimeException re = assertThrows(RuntimeException.class, xIntercepted::throwRuntimeException);
        assertThat(re.getMessage(), equalTo("forced"));

        assertThat(NamedInterceptor.ctorCount.get(), equalTo(1));
    }

}
