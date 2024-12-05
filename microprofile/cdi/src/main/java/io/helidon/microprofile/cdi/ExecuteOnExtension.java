/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.helidon.common.LazyValue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;

/**
 * CDI extension to support the {@link ExecuteOn} annotation.
 */
public class ExecuteOnExtension implements Extension {

    enum MethodType {
        BLOCKING,
        NON_BLOCKING
    };

    private final LazyValue<Map<Method, AnnotatedMethod<?>>> methodMap = LazyValue.create(ConcurrentHashMap::new);
    private final LazyValue<Map<Method, MethodType>> methodType = LazyValue.create(ConcurrentHashMap::new);

    void registerMethods(BeanManager bm, @Observes ProcessSyntheticBean<?> event) {
        registerMethods(bm.createAnnotatedType(event.getBean().getBeanClass()));
    }

    void registerMethods(@Observes ProcessManagedBean<?> event) {
        registerMethods(event.getAnnotatedBeanClass());
    }

    private void registerMethods(AnnotatedType<?> type) {
        for (AnnotatedMethod<?> annotatedMethod : type.getMethods()) {
            if (annotatedMethod.isAnnotationPresent(ExecuteOn.class)) {
                Method method = annotatedMethod.getJavaMember();
                methodMap.get().put(method, annotatedMethod);
                methodType.get().put(method, findMethodType(method));
            }
        }
    }

    void validateAnnotations(BeanManager bm, @Observes @Initialized(ApplicationScoped.class) Object event) {
        methodMap.get().forEach((method, annotatedMethod) -> validateExecutor(bm, annotatedMethod));
    }


    private static MethodType findMethodType(Method method) {
        Class<?> returnType = method.getReturnType();
        if (CompletionStage.class.isAssignableFrom(returnType)
                || CompletableFuture.class.isAssignableFrom(returnType)) {
            return MethodType.NON_BLOCKING;
        }
        if (Future.class.equals(returnType)) {
            throw new DeploymentException("Future is not supported as return type of ExecuteOn method");
        }
        return MethodType.BLOCKING;
    }

    private static void validateExecutor(BeanManager bm, AnnotatedMethod<?> method) {
        ExecuteOn executeOn = method.getAnnotation(ExecuteOn.class);
        if (executeOn.value() == ExecuteOn.ThreadType.EXECUTOR) {
            String executorName = executeOn.executorName();
            Set<Bean<?>> beans = bm.getBeans(ExecutorService.class, NamedLiteral.of(executorName));
            if (beans.isEmpty()) {
                throw new IllegalArgumentException("Unable to resolve named executor service '"
                        + executeOn.value() + "' at "
                        + method.getJavaMember().getDeclaringClass().getName() + "::"
                        + method.getJavaMember().getName());
            }
        }
    }

    ExecuteOn getAnnotation(Method method) {
        AnnotatedMethod<?> annotatedMethod = methodMap.get().get(method);
        if (annotatedMethod != null) {
            return annotatedMethod.getAnnotation(ExecuteOn.class);
        }
        throw new IllegalArgumentException("Unable to map method " + method);
    }

    MethodType getMethodType(Method method) {
        return methodType.get().get(method);
    }

    void registerInterceptors(@Observes BeforeBeanDiscovery discovery, BeanManager bm) {
        discovery.addAnnotatedType(bm.createAnnotatedType(ExecuteOnInterceptor.class),
                ExecuteOnInterceptor.class.getName());
    }

    void clearMethodMap() {
        methodMap.get().clear();
        methodType.get().clear();
    }
}
