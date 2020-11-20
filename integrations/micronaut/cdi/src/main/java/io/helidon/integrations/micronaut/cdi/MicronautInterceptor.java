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

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;

/**
 * A CDI interceptor that invokes all Micronaut interceptors.
 * DO NOT USE DIRECTLY. Usage is computed by this CDI extension.
 */
// interceptor binding is defined in code of extension, not on annotation
@MicronautIntercepted
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
@Internal
public class MicronautInterceptor {
    private static final Logger LOGGER = Logger.getLogger(MicronautInterceptor.class.getName());

    private final ApplicationContext context;
    private final MicronautCdiExtension extension;

    @Inject
    MicronautInterceptor(ApplicationContext context, MicronautCdiExtension extension) {
        this.context = context;
        this.extension = extension;
    }

    /**
     * Interceptor method that call Micronaut interceptors for a CDI bean.
     *
     * @param cdiCtx invocation context
     * @return response of the method
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @AroundInvoke
    public Object invoke(InvocationContext cdiCtx) {

        Method javaMethod = cdiCtx.getMethod();
        MethodInterceptorMetadata meta = extension.getInterceptionMetadata(javaMethod);

        Set<Class<? extends MethodInterceptor<?, ?>>> interceptorClasses = meta.interceptors();
        Set<MethodInterceptor<?, ?>> interceptors = new TreeSet<>(Comparator.comparingInt(MethodInterceptor::getOrder));

        for (Class<? extends MethodInterceptor> aClass : interceptorClasses) {
            // we need to find the bean for each invocation, as this may be a prototype bean
            interceptors.add(context.findBean(aClass)
                                     .orElseThrow(() -> new MicronautCdiException("Cannot create bean class for interceptor "
                                                                                          + aClass.getName())));
        }

        ExecutableMethod<?, ?> executableMethod = meta.executableMethod();
        Iterator<MethodInterceptor<?, ?>> remaining = interceptors.iterator();
        io.micronaut.aop.MethodInvocationContext context = MicronautMethodInvocationContext
                .create(cdiCtx, executableMethod, interceptors, remaining);

        // There is always at least one interceptor, as otherwise this class would never be registered with CDI
        MethodInterceptor<?, ?> next = remaining.next();
        LOGGER.finest(() -> "Micronaut interceptor: " + next.getClass().getName());
        return next.intercept(context);
    }
}
