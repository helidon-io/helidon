/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.util.stream.IntStream;

import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class HelloWorldTest.
 */
public class HelloWorldTest extends MetricsMpServiceTest {

    @BeforeEach
    public void registerCounter() {
        registerCounter("helloCounter");
    }

    @Test
    public void testMetrics() {
        IntStream.range(0, 5).forEach(
                i -> client.target(baseUri())
                        .path("helloworld").request().accept(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));
        assertThat(getCounter("helloCounter").getCount(), is(5L));
    }

    @Test
    public void testSyntheticSimpleTimer() {
        IntStream.range(0, 6).forEach(
                i -> client.target(baseUri())
                        .path("helloworld/withArgs").request(MediaType.TEXT_PLAIN_TYPE)
                        .put(Entity.text("Joe")).readEntity(String.class));

        SimpleTimer syntheticSimpleTimer = getSyntheticSimpleTimer();
        assertThat(syntheticSimpleTimer.getCount(), Is.is(6L));
    }

    private static SimpleTimer getSyntheticSimpleTimer() {
        Tag[] tags = new Tag[] {new Tag("class", HelloWorldResource.class.getName()),
                new Tag("method", "messageWithArg_java.lang.String")};
        SimpleTimer syntheticSimpleTimer = registry.simpleTimer(
                MetricsCdiExtension.SYNTHETIC_SIMPLE_TIMER_METADATA, tags);
        return syntheticSimpleTimer;
    }

    @AfterEach
    public void checkMetricsUrl() {
        JsonObject app = client.target(baseUri())
                .path("metrics").request().accept(MediaType.APPLICATION_JSON_TYPE)
                .get(JsonObject.class).getJsonObject("application");
        assertThat(app.getJsonNumber("helloCounter").intValue(), is(5));
    }
}
