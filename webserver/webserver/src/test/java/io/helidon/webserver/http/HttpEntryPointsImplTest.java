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

package io.helidon.webserver.http;

import java.util.List;
import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.webserver.http.spi.ProtocolUpgradeHandler;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpEntryPointsImplTest {
    @Test
    void entryPointInterceptorsDoNotCreateProtocolUpgradePolicyHandler() {
        HttpEntryPoint.Interceptor interceptor = (ctx, chain, request, response) -> chain.proceed(request, response);
        HttpEntryPointsImpl entryPoints = new HttpEntryPointsImpl(List.of(), List.of(serviceInstance(interceptor)));

        Handler handler = entryPoints.handler(mock(ServiceDescriptor.class),
                                             Set.of(),
                                             List.<Annotation>of(),
                                             mock(TypedElementInfo.class),
                                             (req, res) -> res.send("endpoint"));

        assertThat(handler instanceof ProtocolUpgradeHandler, is(false));
    }

    @Test
    void noInterceptorsUseEndpointHandlerDirectly() {
        HttpEntryPointsImpl entryPoints = new HttpEntryPointsImpl(List.of(), List.of());

        Handler handler = entryPoints.handler(mock(ServiceDescriptor.class),
                                             Set.of(),
                                             List.<Annotation>of(),
                                             mock(TypedElementInfo.class),
                                             (req, res) -> res.send("endpoint"));

        assertThat(handler instanceof ProtocolUpgradeHandler, is(false));
    }

    @SuppressWarnings("unchecked")
    private static ServiceInstance<HttpEntryPoint.Interceptor> serviceInstance(HttpEntryPoint.Interceptor interceptor) {
        ServiceInstance<HttpEntryPoint.Interceptor> instance = mock(ServiceInstance.class);
        when(instance.get()).thenReturn(interceptor);
        when(instance.weight()).thenReturn(100.0);
        return instance;
    }

}
