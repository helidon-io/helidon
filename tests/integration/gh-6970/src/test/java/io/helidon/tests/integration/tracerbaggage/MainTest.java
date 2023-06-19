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
package io.helidon.tests.integration.tracerbaggage;

import io.helidon.config.Config;
import io.helidon.tracing.HeaderConsumer;
import io.helidon.tracing.HeaderProvider;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class MainTest {

    public static final String KEY = "fubar";
    public static final String VALUE = "1";

    public static final String ANOTHER_KEY = "another";
    public static final String ANOTHER_VALUE = "value";

    @BeforeAll
    static void prepareTracer() {
        Config config = Config.create();

        TracerBuilder.create(config.get("tracing"))
                .serviceName("helidon-service")
                .registerGlobal(true)
                .build();


    }

    /**
     * Test for: https://github.com/helidon-io/helidon/issues/6970
     */
    @Test
    void baggageCanaryMinimal() {
        Tracer tracer = Tracer.global();
        Span span = tracer.spanBuilder("baggageCanaryMinimal").start();
        // Set baggage and confirm that it's known in the span
        span.baggage(KEY, VALUE);
        span.baggage(ANOTHER_KEY, ANOTHER_VALUE);
        assertThat(span.baggage(KEY).orElse(null), is(VALUE));
        assertThat(span.baggage(ANOTHER_KEY).orElse(null), is(ANOTHER_VALUE));

        // Inject the span (context) into the consumer
        HeaderConsumer consumer = HeaderConsumer
                .create(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        tracer.inject(span.context(), HeaderProvider.empty(), consumer);

        // Check baggage propagated
        List<String> result = new ArrayList<>();
        consumer.keys().forEach(result::add);
        assertThat(result, hasItem(containsString(KEY)));
        assertThat(result, hasItem(containsString(ANOTHER_KEY)));

        //Check baggage value
        String fubar = result.stream().filter(e -> e.contains(KEY)).findFirst().orElseThrow();
        assertThat(consumer.get(fubar).orElseThrow(), is(VALUE));
        //Check another baggage
        String another = result.stream().filter(e -> e.contains(ANOTHER_KEY)).findFirst().orElseThrow();
        assertThat(consumer.get(another).orElseThrow(), is(ANOTHER_VALUE));
        span.end();
    }

}
