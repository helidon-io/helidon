/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.metrics.api.RegistryFactory;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

class TestPrometheusOutputWithTags {

    @Test
    void testTimerOutputWithTags() throws InterruptedException, IOException {

        MetricRegistry registry = RegistryFactory.create().getRegistry(MetricRegistry.Type.APPLICATION);
        Tag[] tags = new Tag[] {new Tag("class", getClass().getName()),
                                new Tag("method", "anyMethod")};
        MetricID metricID = new MetricID("mytimer", tags);
        Timer timer = registry.timer("myTimer", tags);
        Timer.Context timerContext = timer.time();

        TimeUnit.SECONDS.sleep(1);
        timerContext.stop();

        HelidonTimer hTimer = (HelidonTimer) timer;
        StringBuilder sb = new StringBuilder();
        hTimer.prometheusData(sb, metricID, false, false);

        LineNumberReader reader = new LineNumberReader(new StringReader(sb.toString()));

        String line;
        while ((line = reader.readLine()) != null) {
            for (String time : new String[] {"one", "five", "fifteen"}) {
                if (line.startsWith("application_mytimer_" + time + "_min_rate_per_second")) {
                    assertThat("Tag portions of OpenMetrics output", line, allOf(
                            containsString("class=\"" + getClass().getName() + "\""),
                            containsString("method=\"anyMethod\"")));
                }
            }
        }
    }

    @Test
    void testHistogramOutputWithTags() throws IOException {
        MetricRegistry registry = RegistryFactory.create().getRegistry(MetricRegistry.Type.APPLICATION);
        Tag[] tags = new Tag[] {new Tag("class", getClass().getName()),
                new Tag("method", "anyMethod")};
        MetricID metricID = new MetricID("myhisto", tags);
        Histogram histogram = registry.histogram("myhisto", tags);

        Stream.of(1,4,9)
                .forEach(histogram::update);

        HelidonHistogram hHistogram = (HelidonHistogram) histogram;
        StringBuilder sb = new StringBuilder();
        hHistogram.prometheusData(sb, metricID, false, false);

        LineNumberReader reader = new LineNumberReader(new StringReader(sb.toString()));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("application_myhisto_")) {
                assertThat("Tag portions of OpenMetrics output", line, allOf(
                        containsString("class=\"" + getClass().getName() + "\""),
                        containsString("method=\"anyMethod\"")));
            }
        }
    }
}
