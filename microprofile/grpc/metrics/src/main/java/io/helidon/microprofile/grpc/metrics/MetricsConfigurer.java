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
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import io.helidon.microprofile.metrics.MetricsCdiExtension;
import io.helidon.servicecommon.restcdi.AnnotationLookupResult;
import io.helidon.servicecommon.restcdi.AnnotationSiteType;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static io.helidon.microprofile.metrics.MetricUtil.getMetricName;

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

    /**
     * Captures information and logic for dealing with the various metrics annotations. This allows the metrics handling to be
     * largely data-driven rather than requiring separate code for each type of metric.
     *
     * @param <A> the type of the specific metrics annotation
     */
    private static class MetricAnnotationInfo<A extends Annotation> {
        private final Class<A> annotationClass;
        private final Supplier<GrpcMetrics> gRpcMetricsSupplier;
        private final Function<A, String> annotationNameFunction;
        private final Function<A, Boolean> annotationAbsoluteFunction;
        private final Function<A, String> annotationDescriptorFunction;
        private final Function<A, String> annotationDisplayNameFunction;
        private final Function<A, Boolean> annotationReusableFunction;
        private final Function<A, String> annotationUnitsFunction;


        MetricAnnotationInfo(
                Class<A> annotationClass,
                Supplier<GrpcMetrics> gRpcMetricsSupplier,
                Function<A, String> annotationNameFunction,
                Function<A, Boolean> annotationAbsoluteFunction,
                Function<A, String> annotationDescriptorFunction,
                Function<A, String> annotationDisplayNameFunction,
                Function<A, Boolean> annotationReusableFunction,
                Function<A, String> annotationUnitsFunction) {
            this.annotationClass = annotationClass;
            this.gRpcMetricsSupplier = gRpcMetricsSupplier;
            this.annotationNameFunction = annotationNameFunction;
            this.annotationAbsoluteFunction = annotationAbsoluteFunction;
            this.annotationDescriptorFunction = annotationDescriptorFunction;
            this.annotationDisplayNameFunction = annotationDisplayNameFunction;
            this.annotationReusableFunction = annotationReusableFunction;
            this.annotationUnitsFunction = annotationUnitsFunction;
        }

        A annotationOnMethod(AnnotatedMethod am) {
            return am.getAnnotation(annotationClass);
        }

        String name(AnnotatedMethod am) {
            return annotationNameFunction.apply(am.getAnnotation(annotationClass));
        }

        boolean absolute(AnnotatedMethod am) {
            return annotationAbsoluteFunction.apply(am.getAnnotation(annotationClass));
        }

        String displayName(AnnotatedMethod am) {
            return annotationDisplayNameFunction.apply(am.getAnnotation(annotationClass));
        }

        String description(AnnotatedMethod am) {
            return annotationDescriptorFunction.apply(am.getAnnotation(annotationClass));
        }

        boolean reusable(AnnotatedMethod am) {
            return annotationReusableFunction.apply(am.getAnnotation(annotationClass));
        }

        String units(AnnotatedMethod am) {
            return annotationUnitsFunction.apply(am.getAnnotation(annotationClass));
        }
    }

    private static final Map<Class<? extends Annotation>, MetricAnnotationInfo<?>> METRIC_ANNOTATION_INFO = Map.of(
            Counted.class, new MetricAnnotationInfo<Counted>(
                    Counted.class,
                    GrpcMetrics::counted,
                    Counted::name,
                    Counted::absolute,
                    Counted::description,
                    Counted::displayName,
                    Counted::reusable,
                    Counted::unit),
            Metered.class, new MetricAnnotationInfo<Metered>(
                    Metered.class,
                    GrpcMetrics::metered,
                    Metered::name,
                    Metered::absolute,
                    Metered::description,
                    Metered::displayName,
                    Metered::reusable,
                    Metered::unit),
            Timed.class, new MetricAnnotationInfo<Timed>(
                    Timed.class,
                    GrpcMetrics::timed,
                    Timed::name,
                    Timed::absolute,
                    Timed::description,
                    Timed::displayName,
                    Timed::reusable,
                    Timed::unit),
            ConcurrentGauge.class, new MetricAnnotationInfo<ConcurrentGauge>(
                    ConcurrentGauge.class,
                    GrpcMetrics::concurrentGauge,
                    ConcurrentGauge::name,
                    ConcurrentGauge::absolute,
                    ConcurrentGauge::description,
                    ConcurrentGauge::displayName,
                    ConcurrentGauge::reusable,
                    ConcurrentGauge::unit),
            SimplyTimed.class, new MetricAnnotationInfo<SimplyTimed>(
                    SimplyTimed.class,
                    GrpcMetrics::simplyTimed,
                    SimplyTimed::name,
                    SimplyTimed::absolute,
                    SimplyTimed::description,
                    SimplyTimed::displayName,
                    SimplyTimed::reusable,
                    SimplyTimed::unit)
    );

    // for testing
    static Set<Class<? extends Annotation>> metricsAnnotationsSupported() {
        return Collections.unmodifiableSet(METRIC_ANNOTATION_INFO.keySet());
    }

    @Override
    public void accept(Class<?> serviceClass, Class<?> annotatedClass, ServiceDescriptor.Builder builder) {

        AnnotatedMethodList methodList = AnnotatedMethodList.create(serviceClass);

        METRIC_ANNOTATION_INFO.forEach((annotationClass, info) -> methodList.withAnnotation(annotationClass)
                .stream()
                .filter(am -> isServiceAnnotated(serviceClass, am, annotationClass))
                .forEach(annotatedMethod -> {
                    Annotation anno = info.annotationOnMethod(annotatedMethod);
                    addMetric(builder,
                            annotatedMethod,
                            info.gRpcMetricsSupplier.get(),
                            anno,
                            info.name(annotatedMethod),
                            info.absolute(annotatedMethod));
                }));
    }

    private boolean isServiceAnnotated(Class<?> cls, AnnotatedMethod annotatedMethod, Class<? extends Annotation> annotation) {
        Method method = annotatedMethod.declaredMethod();
        return method.getDeclaringClass().equals(cls) && method.isAnnotationPresent(annotation);
    }

    private void addMetric(ServiceDescriptor.Builder builder,
                             AnnotatedMethod annotatedMethod,
                             GrpcMetrics interceptor,
                             Annotation annotation,
                             String name,
                             boolean absolute) {

        GrpcMethod rpcMethod = annotatedMethod.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
        if (rpcMethod != null) {
            Method method = findAnnotatedMethod(annotatedMethod, annotation.annotationType());
            Class<?> annotatedClass = method.getDeclaringClass();
            String grpcMethodName = GrpcServiceBuilder.determineMethodName(annotatedMethod, rpcMethod);
            String metricName = getMetricName(method,
                                              annotatedClass,
                                              AnnotationSiteType.METHOD,
                                              name,
                                              absolute);

            LOGGER.log(Level.FINE, () -> String.format("Adding gRPC '%s' metric interceptor to service '%s' method '%s'",
                                                       annotation.annotationType().getSimpleName(),
                                                       builder.name(),
                                                       grpcMethodName));

            AnnotationLookupResult<? extends Annotation> lookupResult
                    = AnnotationLookupResult.lookupAnnotation(method, annotation.annotationType(), annotatedClass);

            MetricAnnotationInfo<?> mInfo = METRIC_ANNOTATION_INFO.get(annotation.annotationType());
            if (mInfo != null && mInfo.annotationClass.isInstance(annotation)) {
                interceptor = interceptor.description(mInfo.description(annotatedMethod))
                        .displayName(mInfo.displayName(annotatedMethod))
                        .reusable(mInfo.reusable(annotatedMethod))
                        .units(mInfo.units(annotatedMethod));
            }

            MetricsCdiExtension.registerMetric(method, annotatedClass, lookupResult);
            builder.intercept(grpcMethodName, interceptor.nameFunction(new ConstantNamingFunction(metricName)));
        }
    }

    private Method findAnnotatedMethod(AnnotatedMethod annotatedMethod, Class<? extends Annotation> type) {
        Method method = annotatedMethod.declaredMethod();
        if (!method.isAnnotationPresent(type)) {
            method = annotatedMethod.method();
        }
        return method;
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
