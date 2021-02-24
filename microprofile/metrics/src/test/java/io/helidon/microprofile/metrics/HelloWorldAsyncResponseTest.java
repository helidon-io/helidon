/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MediaType;

import io.helidon.microprofile.metrics.InterceptorSyntheticSimplyTimed.FinishCallback;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@HelidonTest
public class HelloWorldAsyncResponseTest extends HelloWorldTest {

    @Test
    public void test() {
        String result = webTarget
                .path("helloworld/slow")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .get(String.class);

        assertThat("Mismatched string result", result, is(HelloWorldResource.SLOW_RESPONSE));

        Tag[] tags = {
                new Tag("class", HelloWorldResource.class.getName()),
                new Tag("method", "slowMessage_" + AsyncResponse.class.getName())
        };

        SimpleTimer simpleTimer = syntheticSimpleTimerRegistry().simpleTimer("REST.request", tags);
        assertThat(simpleTimer, is(notNullValue()));
        Duration minDuration = Duration.ofSeconds(HelloWorldResource.SLOW_DELAY_SECS);
        assertThat(simpleTimer.getElapsedTime().compareTo(minDuration), is(greaterThan(0)));
    }
}
