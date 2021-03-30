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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;


public class TestMetricTypeCoverage {

    @Test
    public void ensureAllMetricTypesHandled() {

        Set<MetricType> found = new HashSet<>();
        Set<MetricType> typesToCheck = new HashSet<>(Arrays.asList(MetricType.values()));
        typesToCheck.remove(MetricType.INVALID);
        typesToCheck.remove(MetricType.GAUGE);


        for (MetricType type : typesToCheck) {
            if (!MetricAnnotationInfo.ANNOTATION_TYPE_TO_INFO.containsKey(type)) {
                found.add(type);
            }
        }
        typesToCheck.removeAll(found);
        assertThat("MetricTypes not handled", typesToCheck, is(empty()));
    }
}
