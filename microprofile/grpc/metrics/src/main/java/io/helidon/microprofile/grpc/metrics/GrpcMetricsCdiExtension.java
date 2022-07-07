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
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.microprofile.grpc.core.AnnotatedMethod;
import io.helidon.microprofile.grpc.core.GrpcMethod;
import io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver;
import io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery;
import io.helidon.microprofile.metrics.MetricRegistrationObserver;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.metrics.Metadata;

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
public class GrpcMetricsCdiExtension implements Extension {

//    /**
//     * Determine whether a method is annotated with both a metrics annotation
//     * and an annotation of type {@link io.helidon.microprofile.grpc.core.GrpcMethod}.
//     *
//     * @param configurator  the {@link AnnotatedMethodConfigurator} representing
//     *                      the annotated method
//     *
//     * @return {@code true} if the method is a timed gRPC method
//     */
//    static boolean isRpcMethod(AnnotatedMethodConfigurator<?> configurator, Class<? extends Annotation> type) {
//        AnnotatedMethod method = AnnotatedMethod.create(configurator.getAnnotated().getJavaMember());
//        GrpcMethod rpcMethod = method.firstAnnotationOrMetaAnnotation(GrpcMethod.class);
//        if (rpcMethod != null) {
//            Annotation annotation = method.firstAnnotationOrMetaAnnotation(type);
//            return annotation != null;
//        }
//        return false;
//    }
}
