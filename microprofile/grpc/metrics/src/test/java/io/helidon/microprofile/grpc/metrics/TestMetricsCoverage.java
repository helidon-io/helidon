/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.grpc.metrics;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.inject.Inject;
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
        assertThat("Metrics annotations not covered by test beans",
                extension.annotationClassesNotCoveredByTestBeans(), is(empty()));
    }

    @Test
    public void checkThatAllMetricsAnnotationsWereEncountered() {

        Set<Class<? extends Annotation>> metricsAnnotationsUnused =
                new HashSet<>(MetricsConfigurer.METRIC_ANNOTATION_INFO.keySet());
        metricsAnnotationsUnused.removeAll(extension.metricsAnnotationsUsed());

        assertThat("Known annotations not covered by CoverageTestBeanBase subclasses", metricsAnnotationsUnused,
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
        var metricTypesSupportedByGrpc = new HashSet<>(List.of(MetricType.values()));
        metricTypesSupportedByGrpc.removeAll(Set.of(MetricType.GAUGE, MetricType.HISTOGRAM, MetricType.INVALID));

        var metricTypesAbsentFromMetricsConfigurer = new HashSet<>(metricTypesSupportedByGrpc);

        // Remove metrics types represented in the MetricsConfigurer's annotation info.
        MetricsConfigurer.METRIC_ANNOTATION_INFO.forEach((annotationClass, metricInfo) -> {
            metricTypesAbsentFromMetricsConfigurer.remove(MetricType.from(metricInfo.metricClass()));
        });

        assertThat("Metrics types not supported in MetricsConfigurer but should be",
                metricTypesAbsentFromMetricsConfigurer, is(empty()));
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

        MetricsConfigurer.METRIC_ANNOTATION_INFO.values()
                                                        .stream()
                                                        .map(MetricsConfigurer.MetricInfo::metricClass)
                                                        .map(MetricType::from)
                                                        .toList()
                                                        .forEach(incorrectlySkippedMetricTypes::remove);
        assertThat("At least one MicroProfile metric with an annotation exists that is not present in "
                        + "MetricsConfigurer.METRIC_ANNOTATION_INFO",
                incorrectlySkippedMetricTypes, is(empty()));
    }
}
