/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.grpc.metrics.GrpcMetrics;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.AnnotatedMethodList;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.grpc.server.AnnotatedServiceConfigurer;
import io.helidon.microprofile.grpc.server.GrpcServiceBuilder;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * A {@link AnnotatedServiceConfigurer} that adds a
 * {@link io.helidon.grpc.metrics.GrpcMetrics gRPC metrics interceptor}
 * to an annotated gRPC service.
 * <p>
 * Metric interceptors are only added to services where the method on the
 * service class is annotated with a metric annotation. Any metric annotations
 * on super classes or interfaces will be ignored.
 */
public class MetricsConfigurer
        implements AnnotatedServiceConfigurer {

    private static final Logger LOGGER = Logger.getLogger(MetricsConfigurer.class.getName());

    static final Map<Class<? extends Annotation>, MetricInfo> METRIC_ANNOTATION_INFO = Map.of(
            Counted.class, MetricInfo.create(GrpcMetrics::counted, Counter.class),
            Metered.class, MetricInfo.create(GrpcMetrics::metered, Meter.class),
            Timed.class, MetricInfo.create(GrpcMetrics::timed, Timer.class),
            ConcurrentGauge.class, MetricInfo.create(GrpcMetrics::concurrentGauge,
                                                     org.eclipse.microprofile.metrics.ConcurrentGauge.class),
            SimplyTimed.class, MetricInfo.create(GrpcMetrics::simplyTimed, SimpleTimer.class)
    );

    @Override
    public void accept(Class<?> serviceClass, Class<?> annotatedClass, ServiceDescriptor.Builder builder) {

        AnnotatedMethodList methodList = AnnotatedMethodList.create(serviceClass);

        // For each annotated method:
        //     Retrieve all discoveries of metric annotations on that method, and for each:
        //          Get the metric registration for it (which has the metric metadata).
        //          Add the interceptor for the annotation associated with the annotation site that was discovered.
        methodList.stream()
                .filter(am -> GrpcMetricAnnotationDiscoveryObserver.isDiscovered(am.method()))
                .forEach(annotatedMethod ->
                                 GrpcMetricAnnotationDiscoveryObserver.discoveries(annotatedMethod.method())
                                         .forEach((annotationClass, discovery) -> {
                                             if (isServiceAnnotated(serviceClass,
                                                                    annotatedMethod.declaredMethod(),
                                                                    annotationClass)) {
                                                 processMetricAnnotationSite(
                                                         builder,
                                                         annotatedMethod,
                                                         METRIC_ANNOTATION_INFO
                                                                 .get(annotationClass)
                                                                 .grpcMetricsSupplier
                                                                 .get(),
                                                         discovery.annotation(),
                                                         GrpcMetricRegistrationObserver.metadata(discovery)
                                                 );
                                             }
                                         })
                );
    }

    static boolean isServiceAnnotated(Class<?> cls,
                                              Method method,
                                              Class<? extends Annotation> annotation) {
        return method.getDeclaringClass().equals(cls) && method.isAnnotationPresent(annotation);
    }

    record MetricInfo(Supplier<GrpcMetrics> grpcMetricsSupplier,
                      Class<? extends Metric> metricClass) {
        private static MetricInfo create(Supplier<GrpcMetrics> grpcMetricsSupplier, Class<? extends Metric> metricClass) {
            return new MetricInfo(grpcMetricsSupplier, metricClass);
        }
    }



    // Package-private for testing.

    private void processMetricAnnotationSite(ServiceDescriptor.Builder builder,
                                             AnnotatedMethod annotatedMethod,
                                             GrpcMetrics interceptor,
                                             Annotation annotation,
                                             Metadata metadata) {

        GrpcMethod rpcMethod = annotatedMethod.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        if (rpcMethod != null) {
            String grpcMethodName = GrpcServiceBuilder.determineMethodName(annotatedMethod, rpcMethod);

            LOGGER.log(Level.FINE, () -> String.format("Adding gRPC '%s' metric interceptor to service '%s' method '%s'",
                                                       annotation.annotationType().getSimpleName(),
                                                       builder.name(),
                                                       grpcMethodName));

            if (METRIC_ANNOTATION_INFO.containsKey(annotation.annotationType())
                    && annotation.annotationType().isInstance(annotation)) {
                String candidateDescription = metadata.getDescription();
                if (candidateDescription != null && !candidateDescription.trim().isEmpty()) {
                    interceptor = interceptor.description(candidateDescription.trim());
                }
                String candidateDisplayName = metadata.getDisplayName();
                if (candidateDisplayName != null && !candidateDisplayName.trim().isEmpty()) {
                    interceptor = interceptor.displayName(candidateDisplayName.trim());
                }
                interceptor = interceptor
                        .units(metadata.getUnit());
            }

            builder.intercept(grpcMethodName, interceptor.nameFunction(new ConstantNamingFunction(metadata.getName())));
        }
    }

    /**
     * A {@link GrpcMetrics.NamingFunction} that returns a constant name.
     */
    private static class ConstantNamingFunction
            implements GrpcMetrics.NamingFunction {
        private final String name;

        private ConstantNamingFunction(String name) {
            this.name = name;
        }

        @Override
        public String createName(ServiceDescriptor service, String methodName, MetricType metricType) {
            return name;
        }
    }
}
