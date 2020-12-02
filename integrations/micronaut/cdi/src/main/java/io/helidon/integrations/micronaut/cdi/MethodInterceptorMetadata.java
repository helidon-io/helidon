/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.micronaut.cdi;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedMethod;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ExecutableMethod;

// metadata of a single method, holding its executable method and interceptors to execute
class MethodInterceptorMetadata {
    private final Set<Class<? extends MethodInterceptor<?, ?>>> interceptors = new HashSet<>();
    private final ExecutableMethod<?, ?> executableMethod;

    private MethodInterceptorMetadata(ExecutableMethod<?, ?> executableMethod) {
        this.executableMethod = executableMethod;
    }

    /**
     * Create interceptor metadata based on a CDI method and a Micronaut method (optional).
     *
     * @param cdiMethod CDI annotated method
     * @param micronautMethod Micronaut executable method (may be null)
     * @return a new interceptor metadata with a list of Micronaut interceptors to execute
     */
    static MethodInterceptorMetadata create(AnnotatedMethod<?> cdiMethod,
                                            ExecutableMethod<?, ?> micronautMethod) {
        if (micronautMethod == null) {
            return new MethodInterceptorMetadata(CdiExecutableMethod.create(cdiMethod));
        }
        // if cdi method and micronaut method have the same shape, just use micronaut method
        // merge cdi method and micronaut method into a new executable method
        if (compatible(cdiMethod, micronautMethod)) {
            return new MethodInterceptorMetadata(micronautMethod);
        }
        return new MethodInterceptorMetadata(CdiExecutableMethod.create(cdiMethod, micronautMethod));
    }

    // it is enough if micronaut method has all annotations of cdiMethod (it may have more)
    private static boolean compatible(AnnotatedMethod<?> cdiMethod, ExecutableMethod<?, ?> micronautMethod) {
        Set<Annotation> annotations = cdiMethod.getAnnotations();
        for (Annotation annotation : annotations) {
            AnnotationValue<? extends Annotation> miAnnotation = micronautMethod.getAnnotation(annotation.getClass());
            if (miAnnotation == null) {
                return false;
            }
        }
        // micronaut method has all the annotations of the CDI method, we can re-use it
        return true;
    }

    void addInterceptors(Collection<Class<? extends MethodInterceptor<?, ?>>> interceptors) {
        checkInteceptors(interceptors);
        this.interceptors.addAll(interceptors);
    }

    Set<Class<? extends MethodInterceptor<?, ?>>> interceptors() {
        return interceptors;
    }

    ExecutableMethod<?, ?> executableMethod() {
        return executableMethod;
    }

    private static Set<Class<? extends MethodInterceptor<?, ?>>> checkInteceptors(Collection<Class<? extends MethodInterceptor<
            ?, ?>>> interceptors) {
        Set<Class<? extends MethodInterceptor<?, ?>>> result = new HashSet<>();

        interceptors.stream()
                .peek(it -> {
                    if (!MethodInterceptor.class.isAssignableFrom(it)) {
                        throw new MicronautCdiException("Wrong interceptor defined: " + it.getName());
                    }
                })
                .forEach(result::add);

        return result;
    }

}
