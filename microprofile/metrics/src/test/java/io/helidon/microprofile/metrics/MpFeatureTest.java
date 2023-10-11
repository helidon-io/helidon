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
package io.helidon.microprofile.metrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddConfig(key = "metrics.permit-all", value = "true")
public class MpFeatureTest {
    
    @Inject
    private WebTarget webTarget;
    
    @Test
    void testEndpoint() {
        MetricRegistry metricRegistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.APPLICATION_SCOPE);
        Counter counter = metricRegistry.counter("endpointCounter");
        counter.inc(4);

        String metricsResponse = webTarget.path("/metrics")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .get(String.class);

        Pattern pattern = Pattern.compile(".*^endpointCounter_total\\{.*?mp_scope=\"application\".*?}\\s*(\\S*).*?",
                                          Pattern.DOTALL + Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(metricsResponse);

        assertThat("/metrics response", matcher.matches(), is(true));
        assertThat("Captured groups", matcher.groupCount(), is(1));

        /*
         Prometheus expresses even counters as decimal values (e.g., 4.0).
         */
        assertThat("Captured counter value", Double.parseDouble(matcher.group(1)), is(4.0D));
    }


}
