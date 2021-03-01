/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.interceptor.Interceptor;

import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.GrpcMethod;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * A CDI extension for gRPC metrics.
 * <p>
 * This extension will process annotated types that are gRPC methods and
 * ensure that those methods re properly intercepted with a gRPC metrics
 * {@link io.grpc.ServerInterceptor}.
 * <p>
 * If a method is discovered that is annotated with both a metrics annotation and a gRPC
 * method type annotation they metrics annotation will be effectively removed from the CDI
 * bean so that normal Helidon metrics interceptors do not also intercept that method.
 */
public class GrpcMetricsCdiExtension
        implements Extension {

    static final int OBSERVER_PRIORITY = Interceptor.Priority.APPLICATION;

    static final EnumMap<MetricType, Class<? extends Annotation>> METRICS_ANNOTATIONS;

    static {
        Map<MetricType, Class<? extends Annotation>> map = Map.of(
                MetricType.CONCURRENT_GAUGE, ConcurrentGauge.class,
                MetricType.COUNTER, Counted.class,
                MetricType.METERED, Metered.class,
                MetricType.SIMPLE_TIMER, SimplyTimed.class,
                MetricType.TIMER, Timed.class
        );
        METRICS_ANNOTATIONS = new EnumMap<>(map);
    }


    /**
     * Observer {@link ProcessAnnotatedType} events and process any method
     * annotated with a gRPC method annotation and a metric annotation.
     *
     * @param pat  the {@link ProcessAnnotatedType} to observer
     */
    private void registerMetrics(@Observes
                                 @WithAnnotations({Counted.class, Timed.class, Metered.class, ConcurrentGauge.class,
                                         SimplyTimed.class, Grpc.class})
                                 @Priority(OBSERVER_PRIORITY)
                                 ProcessAnnotatedType<?> pat) {
        METRICS_ANNOTATIONS.values().forEach(type ->
               pat.configureAnnotatedType()
                  .methods()
                  .stream()
                  .filter(method -> isRpcMethod(method, type))
                  .forEach(method -> method.remove(ann -> type.isAssignableFrom(ann.getClass()))));
    }

    /**
     * Determine whether a method is annotated with both a metrics annotation
     * and an annotation of type {@link io.helidon.microprofile.grpc.core.GrpcMethod}.
     *
     * @param configurator  the {@link AnnotatedMethodConfigurator} representing
     *                      the annotated method
     *
     * @return {@code true} if the method is a timed gRPC method
     */
    private boolean isRpcMethod(AnnotatedMethodConfigurator<?> configurator, Class<? extends Annotation> type) {
        AnnotatedMethod method = AnnotatedMethod.create(configurator.getAnnotated().getJavaMember());
        GrpcMethod rpcMethod = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        if (rpcMethod != null) {
            Annotation annotation = method.firstAnnotationOrMetaAnnotation(type);
            return annotation != null;
        }
        return false;
    }
}
