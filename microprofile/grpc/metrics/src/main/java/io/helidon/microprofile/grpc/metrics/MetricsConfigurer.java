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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.servicesupport.cdi.LookupResult;
import io.helidon.common.servicesupport.cdi.MatchingType;
import io.helidon.grpc.metrics.GrpcMetrics;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.AnnotatedMethodList;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.grpc.server.AnnotatedServiceConfigurer;
import io.helidon.microprofile.grpc.server.GrpcServiceBuilder;
import io.helidon.microprofile.metrics.MetricsCdiExtension;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
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

    @Override
    public void accept(Class<?> serviceClass, Class<?> annotatedClass, ServiceDescriptor.Builder builder) {

        AnnotatedMethodList methodList = AnnotatedMethodList.create(serviceClass);

        methodList.withAnnotation(Timed.class)
                .stream()
                .filter(am -> isServiceAnnotated(serviceClass, am, Timed.class))
                .forEach(annotatedMethod -> addTimer(builder, annotatedMethod));

        methodList.withAnnotation(Counted.class)
                .stream()
                .filter(am -> isServiceAnnotated(serviceClass, am, Counted.class))
                .forEach(annotatedMethod -> addCounter(builder, annotatedMethod));

        methodList.withAnnotation(Metered.class)
                .stream()
                .filter(am -> isServiceAnnotated(serviceClass, am, Metered.class))
                .forEach(annotatedMethod -> addMeter(builder, annotatedMethod));
    }

    private boolean isServiceAnnotated(Class<?> cls, AnnotatedMethod annotatedMethod, Class<? extends Annotation> annotation) {
        Method method = annotatedMethod.declaredMethod();
        return method.getDeclaringClass().equals(cls) && method.isAnnotationPresent(annotation);
    }

    private void addTimer(ServiceDescriptor.Builder builder, AnnotatedMethod annotatedMethod) {
        Timed timed = annotatedMethod.getAnnotation(Timed.class);
        addMetric(builder, annotatedMethod, GrpcMetrics.timed(), timed, timed.name(), timed.absolute());
    }

    private void addCounter(ServiceDescriptor.Builder builder, AnnotatedMethod annotatedMethod) {
        Counted counted = annotatedMethod.getAnnotation(Counted.class);
        addMetric(builder, annotatedMethod, GrpcMetrics.counted(), counted, counted.name(), counted.absolute());
    }

    private void addMeter(ServiceDescriptor.Builder builder, AnnotatedMethod annotatedMethod) {
        Metered metered = annotatedMethod.getAnnotation(Metered.class);
        addMetric(builder, annotatedMethod, GrpcMetrics.metered(), metered, metered.name(), metered.absolute());
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
                                              MatchingType.METHOD,
                                              name,
                                              absolute);

            LOGGER.log(Level.FINE, () -> String.format("Adding gRPC '%s' metric interceptor to service '%s' method '%s'",
                                                       annotation.annotationType().getSimpleName(),
                                                       builder.name(),
                                                       grpcMethodName));

            LookupResult<? extends Annotation> lookupResult
                    = LookupResult.lookupAnnotation(method, annotation.annotationType(), annotatedClass);

            if (annotation instanceof Metered) {
                Metered metered = (Metered) annotation;
                String displayName = metered.displayName().trim();
                interceptor = interceptor.description(metered.description());
                interceptor = interceptor.displayName(displayName.isEmpty() ? metricName : displayName);
                interceptor = interceptor.reusable(metered.reusable());
                interceptor = interceptor.units(metered.unit());
            } else if (annotation instanceof Gauge) {
                Gauge gauge = (Gauge) annotation;
                String displayName = gauge.displayName().trim();
                interceptor = interceptor.description(gauge.description());
                interceptor = interceptor.displayName(displayName.isEmpty() ? metricName : displayName);
                interceptor = interceptor.units(gauge.unit());
            } else if (annotation instanceof Timed) {
                Timed timed = (Timed) annotation;
                String displayName = timed.displayName().trim();
                interceptor = interceptor.description(timed.description());
                interceptor = interceptor.displayName(displayName.isEmpty() ? metricName : displayName);
                interceptor = interceptor.reusable(timed.reusable());
                interceptor = interceptor.units(timed.unit());
            } else if (annotation instanceof Counted) {
                Counted counted = (Counted) annotation;
                String displayName = counted.displayName().trim();
                interceptor = interceptor.description(counted.description());
                interceptor = interceptor.displayName(displayName.isEmpty() ? metricName : displayName);
                interceptor = interceptor.reusable(counted.reusable());
                interceptor = interceptor.units(counted.unit());
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
