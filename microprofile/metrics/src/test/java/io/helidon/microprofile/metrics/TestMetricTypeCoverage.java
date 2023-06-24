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
package io.helidon.microprofile.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;


public class TestMetricTypeCoverage {

    @Test
    public void ensureAllMetricTypesHandled() {

        Set<Class<? extends Metric>> found = new HashSet<>();
        Set<Class<? extends Metric>> typesToCheck = new HashSet<>(List.of(Counter.class,
                                                                          Gauge.class,
                                                                          Histogram.class,
                                                                          Timer.class));


        // We do not use the general anno processing for gauges. There is no annotation for histogram.
         typesToCheck.removeAll(Set.of(Gauge.class, Histogram.class));


        for (Class<? extends Metric> type : typesToCheck) {
            for (MetricAnnotationInfo<?, ?> info : MetricAnnotationInfo.ANNOTATION_TYPE_TO_INFO.values()) {
                if (info.metricType().equals(type)) {
                    found.add(type);
                }
            }
        }
        typesToCheck.removeAll(found);
        assertThat("MetricTypes not handled", typesToCheck, is(empty()));
    }
}
