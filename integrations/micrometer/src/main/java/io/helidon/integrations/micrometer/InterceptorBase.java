/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.integrations.micrometer;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.enterprise.context.Dependent;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import io.helidon.common.servicesupport.cdi.LookupResult;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import static io.helidon.common.servicesupport.cdi.CdiExtensionBase.getRealClass;
import static io.helidon.common.servicesupport.cdi.LookupResult.lookupAnnotation;

@Dependent
abstract class InterceptorBase<T extends Meter, A extends Annotation> {
    private final MeterRegistry registry;
    private final Class<A> annotationClass;
    private final Function<A, String> nameFunction;
    private final Function<A, String[]> tagsFunction;
    private final MeterLookup<T> meterLookup;
    private final Map<AnnotatedElement, T> elementMeterMap = new ConcurrentHashMap<>();
    private final String meterTypeName;

    @FunctionalInterface
    interface MeterLookup<M extends Meter> {
         M apply(MeterRegistry meterRegistry, String name, String[] tags);
    }

    InterceptorBase(MeterRegistry registry,
            Class<A> annotationClass,
            Function<A, String> nameFunction,
            Function<A, String[]> tagsFunction,
            MeterLookup<T> meterLookup,
            String meterTypeName) {
        this.registry = registry;
        this.annotationClass = annotationClass;
        this.nameFunction = nameFunction;
        this.tagsFunction = tagsFunction;
        this.meterLookup = meterLookup;
        this.meterTypeName = meterTypeName;
    }

    @AroundConstruct
    private Object aroundConstructor(InvocationContext context) throws Throwable {
        return called(context, context.getConstructor());
    }

    @AroundInvoke
    private Object aroundMethod(InvocationContext context) throws Throwable {
        return called(context, context.getMethod());
    }

    /**
     * Returns class for this context. There is no target for constructors.
     *
     * @param context The context.
     * @param element Method or constructor.
     * @param <E>     Method or constructor type.
     * @return The class.
     */
    protected <E extends Member & AnnotatedElement> Class<?> getClass(InvocationContext context, E element) {
        return context.getTarget() != null ? getRealClass(context.getTarget()) : element.getDeclaringClass();
    }

    /**
     * Performs any logic to be run before the intercepted method is invoked and
     * then invokes {@code context.proceed()}, returning the value returned by
     * from {@code context.proceed()}.
     *
     * @param meter          meter being accessed
     * @param annotation     annotation instance for the metric
     * @param context        invocation context for the intercepted method call
     * @return return value from invoking the intercepted method
     * @throws Exception in case of errors invoking the intercepted method or
     *                   performing the pre-invoke processing
     */
    protected abstract Object prepareAndInvoke(T meter,
            A annotation,
            InvocationContext context) throws Exception;

    /**
     * Performs any logic to be run after the intercepted method has run.
     * <p>
     * This method is invoked regardless of whether the intercepted method threw an exception.
     *
     * @param meter          meter being accessed
     * @param annotation     annotation instance for the metric
     * @param context        invocation context for the intercepted method call
     * @param t              any Throwable caught when invoking {@code prepareAndInvoke};
     *                       null if that method threw no exception
     * @throws Exception in case of errors performing the post-call logic
     */
    protected void postInvoke(T meter,
            A annotation,
            InvocationContext context,
            Throwable t) throws Exception {
    }

    private <E extends Member & AnnotatedElement> Object called(InvocationContext context, E element) throws Throwable {
        LookupResult<A> lookupResult = lookupAnnotation(element, annotationClass, getClass(context, element));
        if (lookupResult != null) {

            Throwable throwable = null;
            A annot = lookupResult.getAnnotation();

            Object result = null;
            T meter = getMeterForElement(element, getClass(context, element), lookupResult);

            try {
                return prepareAndInvoke(meter, annot, context);
            } catch (Throwable t) {
                throwable = t;
                throw t;
            } finally {
                postInvoke(meter, annot, context, throwable);
            }
        }
        return context.proceed();
    }

    /**
     * Return the Meter for the given site, either by returning the cached one associated with the site or by looking up the
     * metric in the registry.
     *
     * @param element the annotated element being invoked
     * @param clazz the type of meter
     * @param lookupResult the combination of the annotation and the matching type
     * @param <E> specific type of element
     * @return the meter to be updated for the annotated element being invoked
     */
    private <E extends Member & AnnotatedElement> T getMeterForElement(E element, Class<?> clazz,
            LookupResult<A> lookupResult) {

        return elementMeterMap.computeIfAbsent(element, e -> createMeterForElement(element, clazz, lookupResult));
    }

    /**
     * Retrieves from the registry -- and stores in the site-to-metric map -- the metric coresponding to the specified site.
     *
     * @param element the annotated element being invoked
     * @param clazz the type of metric
     * @param lookupResult the combination of the annotation and the matching type
     * @param <E> specific type of element
     * @return the metric retrieved from the registry and added to the site-to-metric map
     */
    private <E extends Member & AnnotatedElement> T createMeterForElement(E element, Class<?> clazz,
            LookupResult<A> lookupResult) {

        A annot = lookupResult.getAnnotation();

        String[] tags = tagsFunction.apply(annot);
        String meterName = nameFunction.apply(annot);
        T meter = meterLookup.apply(registry, meterName, tags);
        if (meter == null) {
            throw new IllegalStateException(String.format("No %s with name %s found in registry [%s]", meterTypeName,
                    meterName, registry));
        }

        return meter;
    }
}
