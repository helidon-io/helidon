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
package io.helidon.metrics.microprofile.cdi;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.metrics.microprofile.feature.MpMetricsFeature;
import io.helidon.microprofile.servicecommon.HelidonRestCdiExtension;

import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * MicroProfile metrics CDI portable extension.
 *
 * </p>
 */
public class MetricsCdiExtension extends HelidonRestCdiExtension<MpMetricsFeature> {

    /**
     * All annotation types for metrics. (There is no annotation for histograms.)
     */
    static final Set<Class<? extends Annotation>> ALL_METRIC_ANNOTATIONS = Set.of(
            Counted.class, Timed.class, Gauge.class);

    /**
     * Config key for controlling the optional REST.request metrics collection.
     */
    static final String REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME = "rest-request.enabled";

    /**
     * Metric name used for the synthetic timers for measuring REST requests (if enabled), including successful
     * REST requests and unsuccessful ones with mapped exceptions.
     */
    static final String SYNTHETIC_REST_REQUEST_METRIC_NAME = "REST.request";

    /**
     * Metric name for the synthetic timers for measuring REST requests (if enabled) that are unsuccessful, meaning
     * they trigger unmapped exceptions.
     */
    static final String SYNTHETIC_COUNTER_METRIC_UNMAPPED_EXCEPTION_NAME =
            SYNTHETIC_REST_REQUEST_METRIC_NAME + ".unmappedException.total";

    /**
     * Metadata for synthetic timers tracking successful REST requests (or ones with mapped exceptions).
     */
    static final Metadata SYNTHETIC_TIMER_METADATA = Metadata.builder()
            .withName(SYNTHETIC_REST_REQUEST_METRIC_NAME)
            .withDescription("""
                                     The number of invocations and total response time of this RESTful resource method since the \
                                     start of the server. The metric will not record the elapsed time nor count of a REST \
                                     request if it resulted in an unmapped exception. Also tracks the highest recorded time \
                                     duration and the 50th, 75th, 95th, 98th, 99th and 99.9th percentile.""")
            .withUnit(MetricUnits.NANOSECONDS)
            .build();

    /**
     * Metadata for synthetic counters tracking REST requests resulting in unmapped exceptions.
     */
    static final Metadata SYNTHETIC_COUNTER_UNMAPPED_EXCEPTION_METADATA = Metadata.builder()
            .withName(SYNTHETIC_COUNTER_METRIC_UNMAPPED_EXCEPTION_NAME)
            .withDescription("""
                                     The total number of unmapped exceptions that occur from this RESTful resouce method since \
                                     the start of the server.""")
            .withUnit(MetricUnits.NONE)
            .build();


    private static final System.Logger LOGGER = System.getLogger(MetricsCdiExtension.class.getName());

    private static final Map<Class<? extends Annotation>, AnnotationLiteral<?>> INTERCEPTED_METRIC_ANNOTATIONS =
            Map.of(
                    Counted.class, InterceptorCounted.binding(),
                    Timed.class, InterceptorTimed.binding());

    private static final List<Class<? extends Annotation>> JAX_RS_ANNOTATIONS
            = Arrays.asList(GET.class, PUT.class, POST.class, HEAD.class, OPTIONS.class, DELETE.class, PATCH.class);

    /**
     * Annotations which apply to any element (type, executable (constructor or method), or field).
     */
    private static final Set<Class<? extends Annotation>> METRIC_ANNOTATIONS_ON_ANY_ELEMENT =
            new HashSet<>(ALL_METRIC_ANNOTATIONS) {
                {
                    remove(Gauge.class);
                }
            };

    private static final Function<Config, MpMetricsFeature> FEATURE_FACTORY =
            (Config helidonConfig) -> MpMetricsFeature.builder().config(helidonConfig).build();

    /**
     * Common initialization for concrete implementations.
     */
    public MetricsCdiExtension() {
        super(LOGGER, FEATURE_FACTORY, "mp.metrics");
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> processManagedBean) {
    }
}
