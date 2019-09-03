/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Function;

import javax.enterprise.context.Dependent;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;

import static io.helidon.microprofile.metrics.MetricUtil.getMetricName;
import static io.helidon.microprofile.metrics.MetricUtil.lookupAnnotation;

/**
 * Common methods for interceptors.
 * <p>
 * Concrete subclasses implement:
 * <ul>
 * <li>{@code @Inject}ed constructor which accepts a {@code MetricRegistry} and
 * invokes the constructor for this class, passing:
 * <ul>
 * <li>the registry,
 * <li>the {@code Class} object for the specific metrics annotation it handles,
 * <li>a {@code Function} that accepts an instance of the annotation and returns
 * the name for that instance of the annotation,
 * <li>a {@code Function} that accepts an instance of the annotation and returns
 * whether the name is absolute or not,
 * <li>a {@code Function} that accepts an instance of {@code MetricRegistry} and
 * returns a map of the metrics of the relevant type.
 * </ul>
 * For example, the constructor for the implementation that handles
 * {@code Metered} might use this:
 * <pre>
 * {@code
 *     super(registry,
 *             Metered.class,
 *             Metered::name,
 *             Metered::absolute,
 *             MetricRegistry::getMeters);
 * }
 * </pre>
 * <li>{@link #prepareAndInvoke} to perform any steps before invoking the intercepted
 * method and then invoke that method,
 * <li>and, optionally, {@link #postInvoke} to perform any steps after invoking
 * the intercepted method.
 * </ul>
 */
@Dependent
abstract class InterceptorBase<T extends Metric, A extends Annotation> {

    private final MetricRegistry registry;
    private final Class<A> annotationClass;
    private final Function<A, String> nameFunction;
    private final Function<A, Boolean> isAbsoluteFunction;
    private final Function<MetricRegistry, SortedMap<String, T>> metricsMapFunction;
    private final String metricTypeName;
    InterceptorBase(MetricRegistry registry,
                    Class<A> annotationClass,
                    Function<A, String> nameFunction,
                    Function<A, Boolean> isAbsoluteFunction,
                    Function<MetricRegistry, SortedMap<String, T>> metricsMapFunction,
                    String metricTypeName) {
        this.registry = registry;
        this.annotationClass = annotationClass;
        this.nameFunction = nameFunction;
        this.isAbsoluteFunction = isAbsoluteFunction;
        this.metricsMapFunction = metricsMapFunction;
        this.metricTypeName = metricTypeName;
    }

    protected <T> Optional<T> getMetric(Map<String, T> metricMap, String metricName) {
        return Optional.ofNullable(metricMap.get(metricName));
    }

    @AroundConstruct
    private Object aroundConstructor(InvocationContext context) throws Exception {
        return called(context, context.getConstructor());
    }

    @AroundInvoke
    private Object aroundMethod(InvocationContext context) throws Exception {
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
        return context.getTarget() != null ? MetricsCdiExtension.getRealClass(context.getTarget()) : element.getDeclaringClass();
    }

    private <E extends Member & AnnotatedElement> Object called(InvocationContext context, E element) throws Exception {
        MetricUtil.LookupResult<A> lookupResult = lookupAnnotation(element, annotationClass, getClass(context, element));
        if (lookupResult != null) {
            A annot = lookupResult.getAnnotation();
            String metricName = getMetricName(element, getClass(context, element), lookupResult.getType(),
                                              nameFunction.apply(annot),
                                              isAbsoluteFunction.apply(annot));
            Optional<T> metric = getMetric(metricsMapFunction.apply(registry), metricName);
            T metricInstance = metric.orElseGet(() -> {
                throw new IllegalStateException("No " + metricTypeName + " with name [" + metricName
                                                        + "] found in registry [" + registry + "]");
            });
            Exception ex = null;
            try {
                return prepareAndInvoke(metricInstance, annot, context);
            } catch (Exception e) {
                ex = e;
                throw e;
            } finally {
                postInvoke(metricInstance, annot, context, ex);
            }
        }
        return context.proceed();
    }

    /**
     * Performs any logic to be run before the intercepted method is invoked and
     * then invokes {@code context.proceed()}, returning the value returned by
     * from {@code context.proceed()}.
     *
     * @param metricInstance metric being accessed
     * @param annotation     annotation instance for the metric
     * @param context        invocation context for the intercepted method call
     * @return return value from invoking the intercepted method
     * @throws Exception in case of errors invoking the intercepted method or
     *                   performing the pre-invoke processing
     */
    protected abstract Object prepareAndInvoke(T metricInstance,
                                               A annotation,
                                               InvocationContext context) throws Exception;

    /**
     * Performs any logic to be run after the intercepted method has run.
     * <p>
     * This method is invoked regardless of whether the intercepted method threw an exception.
     *
     * @param metricInstance metric being accessed
     * @param annotation     annotation instance for the metric
     * @param context        invocation context for the intercepted method call
     * @param ex             any exception caught when invoking {@code prepareAndInvoke};
     *                       null if that method threw no exception
     * @throws Exception in case of errors performing the post-call logic
     */
    protected void postInvoke(T metricInstance,
                              A annotation,
                              InvocationContext context,
                              Exception ex) throws Exception {
    }
}
