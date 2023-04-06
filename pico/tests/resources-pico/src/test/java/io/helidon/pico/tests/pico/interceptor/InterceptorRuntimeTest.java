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

import java.io.Closeable;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.api.DefaultServiceInfo;
import io.helidon.pico.api.DefaultServiceInfoCriteria;
import io.helidon.pico.api.Interceptor;
import io.helidon.pico.api.PicoException;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceInfoCriteria;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.api.Services;
import io.helidon.pico.testing.ReflectionBasedSingletonServiceProvider;
import io.helidon.pico.tests.plain.interceptor.IB;
import io.helidon.pico.tests.plain.interceptor.InterceptorBasedAnno;
import io.helidon.pico.tests.plain.interceptor.TestNamedInterceptor;

import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.api.DefaultQualifierAndValue.create;
import static io.helidon.pico.api.DefaultQualifierAndValue.createNamed;
import static io.helidon.pico.api.PicoServicesConfig.KEY_PERMITS_DYNAMIC;
import static io.helidon.pico.api.PicoServicesConfig.KEY_USES_COMPILE_TIME_APPLICATIONS;
import static io.helidon.pico.api.PicoServicesConfig.KEY_USES_COMPILE_TIME_MODULES;
import static io.helidon.pico.api.PicoServicesConfig.NAME;
import static io.helidon.pico.testing.PicoTestingSupport.basicTestableConfig;
import static io.helidon.pico.testing.PicoTestingSupport.bind;
import static io.helidon.pico.testing.PicoTestingSupport.resetAll;
import static io.helidon.pico.testing.PicoTestingSupport.testableServices;
import static io.helidon.pico.testing.PicoTestingSupport.toDescription;
import static io.helidon.pico.testing.PicoTestingSupport.toDescriptions;
import static io.helidon.pico.tests.pico.TestUtils.loadStringFromResource;
import static io.helidon.pico.tools.TypeTools.toFilePath;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterceptorRuntimeTest {

    Config config = basicTestableConfig();
    PicoServices picoServices;
    Services services;

    @BeforeEach
    void setUp() {
        setUp(config);
    }

    void setUp(Config config) {
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @AfterEach
    void tearDown() {
        resetAll();
    }

    @Test
    void createNoArgBasedInterceptorSource() throws Exception {
        TypeName interceptorTypeName = DefaultTypeName.create(XImpl$$Pico$$Interceptor.class);
        String path = toFilePath(interceptorTypeName);
        File file = new File("./target/generated-sources/annotations", path);
        assertThat(file.exists(), is(true));
        String java = Files.readString(file.toPath());
        assertEquals(loadStringFromResource("expected/ximpl-interceptor._java_"),
                   java);
    }

    @Test
    void createInterfaceBasedInterceptorSource() throws Exception {
        TypeName interceptorTypeName = DefaultTypeName.create(YImpl$$Pico$$Interceptor.class);
        String path = toFilePath(interceptorTypeName);
        File file = new File("./target/generated-sources/annotations", path);
        assertThat(file.exists(), is(true));
        String java = Files.readString(file.toPath());
        assertEquals(loadStringFromResource("expected/yimpl-interceptor._java_"),
                   java);
    }

    @Test
    void runtimeWithNoInterception() throws Exception {
        ServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(Closeable.class.getName())
                .includeIntercepted(true)
                .build();
        List<ServiceProvider<Closeable>> closeableProviders = services.lookupAll(criteria);
        assertThat("the interceptors should always be weighted higher than the non-interceptors",
                   toDescriptions(closeableProviders),
                   contains("XImpl$$Pico$$Interceptor:INIT", "YImpl$$Pico$$Interceptor:INIT",
                            "XImpl:INIT", "YImpl:INIT"));

        criteria = DefaultServiceInfoCriteria.builder()
                .addContractImplemented(Closeable.class.getName())
                .includeIntercepted(false)
                .build();
        closeableProviders = services.lookupAll(criteria);
        assertThat("the interceptors should always be weighted higher than the non-interceptors",
                   toDescriptions(closeableProviders),
                   contains("XImpl$$Pico$$Interceptor:INIT", "YImpl$$Pico$$Interceptor:INIT"));

        List<ServiceProvider<IB>> ibProviders = services.lookupAll(IB.class);
        assertThat(closeableProviders,
                   equalTo(ibProviders));

        ServiceProvider<XImpl> ximplProvider = services.lookupFirst(XImpl.class);
        assertThat(closeableProviders.get(0),
                   is(ximplProvider));

        XImpl x = ximplProvider.get();
        x.methodIA1();
        x.methodIA2();
        x.methodIB("test");
        String sval = x.methodIB2("test");
        assertThat(sval,
                   equalTo("test"));
        long val = x.methodX("a", 2, true);
        assertThat(val,
                   equalTo(101L));
        assertThat(x.methodY(),
                   equalTo("methodY"));
        assertThat(x.methodZ(),
                   equalTo("methodZ"));
        PicoException pe = assertThrows(PicoException.class, x::close);
        assertThat(pe.getMessage(),
                   equalTo("forced: service provider: XImpl:ACTIVE"));
        RuntimeException re = assertThrows(RuntimeException.class, x::throwRuntimeException);
        assertThat(re.getMessage(),
                   equalTo("forced"));

        // we cannot look up by service type here - we need to instead lookup by one of the interfaces
        ServiceProvider<Closeable> yimplProvider = services
                .lookupFirst(
                        DefaultServiceInfoCriteria.builder()
                                .addContractImplemented(Closeable.class.getName())
                                .qualifiers(Set.of(create(Named.class, "ClassY")))
                                .build());
        assertThat(toDescription(yimplProvider),
                   equalTo("YImpl$$Pico$$Interceptor:INIT"));
        IB ibOnYInterceptor = (IB)yimplProvider.get();
        sval = ibOnYInterceptor.methodIB2("test");
        assertThat(sval,
                   equalTo("test"));
    }

    @Test
    void runtimeWithInterception() throws Exception {
        // disable application and modules to effectively start with an empty registry
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(NAME + "." + KEY_PERMITS_DYNAMIC, "true",
                               NAME + "." + KEY_USES_COMPILE_TIME_APPLICATIONS, "false",
                               NAME + "." + KEY_USES_COMPILE_TIME_MODULES, "true"),
                        "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        tearDown();
        setUp(config);
        bind(picoServices, ReflectionBasedSingletonServiceProvider
                              .create(TestNamedInterceptor.class,
                                      DefaultServiceInfo.builder()
                                              .serviceTypeName(TestNamedInterceptor.class.getName())
                                              .addQualifier(createNamed(TestNamed.class.getName()))
                                              .addQualifier(createNamed(InterceptorBasedAnno.class.getName()))
                                              .addExternalContractsImplemented(Interceptor.class.getName())
                                              .build()));
        assertThat(TestNamedInterceptor.ctorCount.get(),
                   equalTo(0));

        List<ServiceProvider<Closeable>> closeableProviders = picoServices.services().lookupAll(Closeable.class);
        assertThat("the interceptors should always be weighted higher than the non-interceptors",
                   toDescriptions(closeableProviders),
                   contains("XImpl$$Pico$$Interceptor:INIT", "YImpl$$Pico$$Interceptor:INIT"));

        List<ServiceProvider<IB>> ibProviders = services.lookupAll(IB.class);
        assertThat(closeableProviders,
                   equalTo(ibProviders));

        ServiceProvider<XImpl> ximplProvider = services.lookupFirst(XImpl.class);
        assertThat(closeableProviders.get(0),
                   is(ximplProvider));

        assertThat(TestNamedInterceptor.ctorCount.get(),
                   equalTo(0));
        XImpl xIntercepted = ximplProvider.get();
        assertThat(TestNamedInterceptor.ctorCount.get(),
                   equalTo(1));

        xIntercepted.methodIA1();
        xIntercepted.methodIA2();
        xIntercepted.methodIB("test");
        String sval = xIntercepted.methodIB2("test");
        assertThat(sval,
                   equalTo("intercepted:test"));
        long val = xIntercepted.methodX("a", 2, true);
        assertThat(val,
                   equalTo(202L));
        assertThat(xIntercepted.methodY(),
                   equalTo("intercepted:methodY"));
        assertThat(xIntercepted.methodZ(),
                   equalTo("intercepted:methodZ"));
        PicoException pe = assertThrows(PicoException.class, xIntercepted::close);
        assertThat(pe.getMessage(),
                   equalTo("forced: service provider: XImpl:ACTIVE"));
        RuntimeException re = assertThrows(RuntimeException.class, xIntercepted::throwRuntimeException);
        assertThat(re.getMessage(),
                   equalTo("forced"));

        assertThat(TestNamedInterceptor.ctorCount.get(),
                   equalTo(1));

        // we cannot look up by service type here - we need to instead lookup by one of the interfaces
        ServiceProvider<Closeable> yimplProvider = services
                .lookupFirst(
                        DefaultServiceInfoCriteria.builder()
                                .addContractImplemented(Closeable.class.getName())
                                .qualifiers(Set.of(create(Named.class, "ClassY")))
                                .build());
        assertThat(toDescription(yimplProvider),
                   equalTo("YImpl$$Pico$$Interceptor:INIT"));
        IB ibOnYInterceptor = (IB) yimplProvider.get();
        sval = ibOnYInterceptor.methodIB2("test");
        assertThat(sval,
                   equalTo("intercepted:test"));
    }

}
