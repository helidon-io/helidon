/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.jaeger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JaegerDataPropagationProviderTest {

    private final JaegerDataPropagationProvider provider = new JaegerDataPropagationProvider();

    @Test
    void dataPropagationTest() {
        Context context = Context.create();
        Tracer tracer = JaegerTracerBuilder.create().serviceName("test-propagation").build();
        context.register(tracer);
        Span span = tracer.spanBuilder("span").start();
        context.register(span);
        try (Scope scope = span.activate()) {
            context.register(scope);

            Contexts.runInContext(context, () -> {
                assertThat(scope.isClosed(), is(false));
                JaegerDataPropagationProvider.JaegerContext data = provider.data();
                provider.propagateData(data);
                assertThat(data.scope().isClosed(), is(false));
                provider.clearData(data);
                assertThat(data.scope().isClosed(), is(true));
            });
        }
    }
}
