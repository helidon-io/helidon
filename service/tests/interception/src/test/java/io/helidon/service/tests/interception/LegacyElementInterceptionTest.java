/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.tests.interception;

import java.util.List;
import java.util.Set;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LegacyElementInterceptionTest {
    private static final TypeName CONTRACT = TypeName.create("compatibility.LegacyContract");
    private static final TypeName SERVICE = TypeName.create("compatibility.LegacyService");
    private static final TypedElementInfo METHOD = TypedElementInfo.builder()
            .kind(ElementKind.METHOD)
            .elementName("message")
            .typeName(TypeName.create(String.class))
            .build();

    @Test
    void testMissingEnclosingTypeMatchesContract() throws Exception {
        // Older generated descriptors omitted the enclosing type from method metadata.
        assertInterception(METHOD, 1);
    }

    @Test
    void testExplicitEnclosingTypeDoesNotFallBackToContract() throws Exception {
        assertInterception(TypedElementInfo.builder(METHOD).enclosingType(SERVICE).build(), 0);
    }

    @Test
    void testDifferentMethodDoesNotMatchContractInterceptor() throws Exception {
        assertInterception(TypedElementInfo.builder(METHOD).elementName("otherMessage").build(), 0);
    }

    private static void assertInterception(TypedElementInfo element, int expectedCalls) throws Exception {
        var interceptor = new CountingInterceptor();
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                           .discoverServices(false)
                                                           .discoverServicesFromServiceLoader(false)
                                                           .putServiceInstance(new InterceptorDescriptor(), interceptor)
                                                           .build());
        try {
            var metadata = manager.registry().get(InterceptionMetadata.class);
            var invoker = metadata.createInvoker(new Object(),
                                                 new LegacyDescriptor(),
                                                 Set.of(),
                                                 List.of(),
                                                 element,
                                                 _ -> "target",
                                                 Set.of());

            assertThat(invoker.invoke(), is("target"));
            assertThat("Contract-qualified interceptor invocations", interceptor.calls, is(expectedCalls));
        } finally {
            manager.shutdown();
        }
    }

    private static class LegacyDescriptor implements ServiceDescriptor<Object> {
        @Override
        public TypeName serviceType() {
            return SERVICE;
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create(LegacyDescriptor.class);
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(CONTRACT));
        }
    }

    private static class InterceptorDescriptor implements ServiceDescriptor<CountingInterceptor> {
        @Override
        public TypeName serviceType() {
            return TypeName.create(CountingInterceptor.class);
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create(InterceptorDescriptor.class);
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(Interception.Interceptor.class),
                          ResolvedType.create(Interception.ElementInterceptor.class));
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return Set.of(Qualifier.createNamed(CONTRACT.fqName() + "." + METHOD.signature().text()));
        }
    }

    private static class CountingInterceptor implements Interception.ElementInterceptor {
        private int calls;

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            calls++;
            return chain.proceed(args);
        }
    }
}
