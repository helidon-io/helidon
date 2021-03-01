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
package io.helidon.microprofile.grpc.metrics;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddExtension(GrpcMetricsCoverageTestCdiExtension.class)
@AddBean(CoverageTestBeanCounted.class)
public class TestMetricsCoverage {

    @Inject
    GrpcMetricsCoverageTestCdiExtension extension;

    @Test
    public void checkThatAllMetricsAnnotationsAreCoveredByTestBeans() {
        assertThat("Some metrics annotations are not covered by test beans",
                extension.annotationClassesNotCoveredByTestBeans(), is(empty()));
    }

    @Test
    public void checkThatAllMetricsWereRemovedFromGrpcMethods() {

        Map<AnnotatedMethod<?>, Set<Class<? extends Annotation>>> leftoverAnnotations =
                extension.remainingTestBeanMethodAnnotations();

        assertThat("Metrics annotations unexpectedly remain on method", leftoverAnnotations.keySet(), is(empty()));
    }

    @Test
    public void checkThatAllMetricsAnnotationsWereEncountered() {

        Set<Class<? extends Annotation>> metricsAnnotationsUnused =
                new HashSet<>(GrpcMetricsCdiExtension.METRICS_ANNOTATIONS.values());
        metricsAnnotationsUnused.removeAll(extension.metricsAnnotationsUsed());

        assertThat("The CoverageTestBeanBase subclasses seem not to cover all known annotations", metricsAnnotationsUnused,
                is(empty()));
    }

    /**
     * Checks that {@code MetricsConfigurer} deals with all metrics annotations that are known to the main Helidon metrics CDI
     * extension.
     * <p>
     *     The {@code MetricsConfigurer} class has been changed to be data-driven. The
     *     {@code metricInfo} map contains all the metric-specific code (mostly as method references) so adding support for a new
     *     metrics (if more are added) happens on that one table.
     * </p>
     * <p>
     *     This test makes sure that the map created there contains an entry for every type of metric annotation known to
     *     Helidon
     *     Microprofile metrics.
     * </p>
     */
    @Test
    public void checkForAllMetricsInMetricInfo() {
        Set<Class<? extends Annotation>> metricsAnnotations =
                new HashSet<>(GrpcMetricsCdiExtension.METRICS_ANNOTATIONS.values());

        metricsAnnotations.removeAll(MetricsConfigurer.metricsAnnotationsSupported());
        assertThat("One or more metrics annotations seem not supported in MetricsConfigurer but should be",
                metricsAnnotations, is(empty()));
    }

    /**
     * Makes sure that all metrics types that have corresponding annotations we process are present in the type-to-annotation
     * EnumMap.
     */
    @Test
    public void checkForAllMetricTypesMappedToAnnotationType() {
        Set<MetricType> ignoredMetricTypes = Set.of(MetricType.INVALID, // ignore this
                MetricType.GAUGE, // we don't process gauge annotations
                MetricType.HISTOGRAM // there is no histogram annotation for the histogram metric type
                );

        Set<MetricType> incorrectlySkippedMetricTypes = new HashSet<>(Arrays.asList(MetricType.values()));
        incorrectlySkippedMetricTypes.removeAll(ignoredMetricTypes);

        incorrectlySkippedMetricTypes.removeAll(GrpcMetricsCdiExtension.METRICS_ANNOTATIONS.keySet());
        assertThat("At least one MicroProfile metric with an annotation exists that is not present in "
                        + "GrpcMetricsCdiExtension.METRICS_ANNOTATIONS",
                incorrectlySkippedMetricTypes, is(empty()));
    }
}
