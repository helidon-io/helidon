/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.grpc.metrics.GrpcMetrics;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.AnnotatedMethodList;
import io.helidon.microprofile.grpc.core.RpcMethod;
import io.helidon.microprofile.grpc.server.AnnotatedServiceConfigurer;
import io.helidon.microprofile.grpc.server.GrpcServiceBuilder;
import io.helidon.microprofile.metrics.MetricUtil;
import io.helidon.microprofile.metrics.MetricsCdiExtension;

import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
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

        RpcMethod rpcMethod = annotatedMethod.firstAnnotationOrMetaAnnotation(RpcMethod.class);
        if (rpcMethod != null) {
            Method method = findAnnotatedMethod(annotatedMethod, annotation.annotationType());
            Class<?> annotatedClass = method.getDeclaringClass();
            String grpcMethodName = GrpcServiceBuilder.determineMethodName(annotatedMethod, rpcMethod);
            String metricName = getMetricName(method,
                                              annotatedClass,
                                              MetricUtil.MatchingType.METHOD,
                                              name,
                                              absolute);

            LOGGER.log(Level.FINE, () -> String.format("Adding gRPC '%s' metric interceptor to service '%s' method '%s'",
                                                       annotation.annotationType().getSimpleName(),
                                                       builder.name(),
                                                       grpcMethodName));

            MetricUtil.LookupResult<? extends Annotation> lookupResult
                    = MetricUtil.lookupAnnotation(method, annotation.annotationType(), annotatedClass);
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
