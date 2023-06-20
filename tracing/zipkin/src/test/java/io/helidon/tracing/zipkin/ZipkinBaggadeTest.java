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
package io.helidon.tracing.zipkin;

import io.helidon.config.Config;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.opentracing.OpenTracing;
import io.helidon.tracing.spi.TracerProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class ZipkinBaggadeTest {

    public static final String KEY = "fubar";
    public static final String VALUE = "1";

    /**
     * Additional Zipkin test for: https://github.com/helidon-io/helidon/issues/6970
     */
    @Test
    void baggageCanaryMinimal() {
        Config config = Config.create();
        io.opentracing.Tracer zipkinTracer = ZipkinTracerBuilder.create(config.get("tracing"))
                .baggage(List.of(KEY))
                .serviceName("helidon-service")
                .build();

        Tracer tracer = OpenTracing.create(zipkinTracer);
        Span span = tracer.spanBuilder("baggageCanaryMinimal").start();
        // Set baggage and confirm that it's known in the span
        span.baggage(KEY, VALUE);
        assertThat(span.baggage(KEY).orElse(null), is(VALUE));

        // Inject the span (context) into the consumer
        HeaderConsumer consumer = HeaderConsumer
                .create(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        tracer.inject(span.context(), HeaderProvider.empty(), consumer);

        // Check baggage propagated
        List<String> result = new ArrayList<>();
        consumer.keys().forEach(result::add);
        assertThat(result, hasItem(containsString(KEY)));

        //Check baggage value
        String fubar = result.stream().filter(e -> e.contains(KEY)).findFirst().orElseThrow();
        assertThat(consumer.get(fubar).orElseThrow(), is(VALUE));
        span.end();
    }

}
