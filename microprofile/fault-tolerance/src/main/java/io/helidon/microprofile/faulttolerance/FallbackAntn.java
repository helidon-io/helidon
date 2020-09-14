/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.reflect.Method;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

/**
 * Class FallbackAntn.
 */
public class FallbackAntn extends MethodAntn implements Fallback {

    /**
     * Constructor.
     *
     * @param beanClass Bean class.
     * @param method The method.
     */
    public FallbackAntn(Class<?> beanClass, Method method) {
        super(beanClass, method);
    }

    @Override
    public void validate() {
        String methodName = fallbackMethod();
        Class<? extends FallbackHandler<?>> value = value();

        // Handler and fallback method not allowed
        if (value != Fallback.DEFAULT.class && !methodName.isEmpty()) {
            throw new FaultToleranceDefinitionException("Fallback annotation cannot declare a "
                    + "handler and a fallback method");
        }

        // Fallback method must be compatible
        Method method = method();
        if (!methodName.isEmpty()) {
            try {
                final Method fallbackMethod = JavaMethodFinder.findMethod(method.getDeclaringClass(),
                        methodName,
                        method.getGenericParameterTypes());
                if (!method.getReturnType().isAssignableFrom(fallbackMethod.getReturnType())) {
                    throw new FaultToleranceDefinitionException("Fallback method " + fallbackMethod.getName()
                            + " in class " + fallbackMethod.getDeclaringClass().getSimpleName()
                            + " incompatible return type " + fallbackMethod.getReturnType()
                            + " with " + method.getReturnType());
                }
            } catch (NoSuchMethodException e) {
                throw new FaultToleranceDefinitionException(e);
            }
        }

        // Handler method must be compatible
        if (value != Fallback.DEFAULT.class) {
            try {
                final Method handleMethod = value.getMethod("handle", ExecutionContext.class);
                if (!handleMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
                    throw new FaultToleranceDefinitionException("Handler method return type "
                            + "is invalid: " + handleMethod.getReturnType());
                }
            } catch (NoSuchMethodException e) {
                throw new FaultToleranceDefinitionException(e);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends FallbackHandler<?>> value() {
        LookupResult<Fallback> lookupResult = lookupAnnotation(Fallback.class);
        final String override = getParamOverride("value", lookupResult.getType());
        try {
            return override != null
                    ? (Class<? extends FallbackHandler<?>>) Class.forName(override)
                    : lookupResult.getAnnotation().value();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String fallbackMethod() {
        LookupResult<Fallback> lookupResult = lookupAnnotation(Fallback.class);
        final String override = getParamOverride("fallbackMethod", lookupResult.getType());
        return override != null ? override : lookupResult.getAnnotation().fallbackMethod();
    }

    @Override
    public Class<? extends Throwable>[] applyOn() {
        LookupResult<Fallback> lookupResult = lookupAnnotation(Fallback.class);
        final String override = getParamOverride("applyOn", lookupResult.getType());
        return override != null ? parseThrowableArray(override) : lookupResult.getAnnotation().applyOn();
    }

    @Override
    public Class<? extends Throwable>[] skipOn() {
        LookupResult<Fallback> lookupResult = lookupAnnotation(Fallback.class);
        final String override = getParamOverride("skipOn", lookupResult.getType());
        return override != null ? parseThrowableArray(override) : lookupResult.getAnnotation().skipOn();
    }
}
