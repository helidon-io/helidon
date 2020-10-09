/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.enterprise.context.Dependent;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import io.helidon.metrics.Registry;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;

import static io.helidon.microprofile.metrics.MetricUtil.MatchingType;
import static io.helidon.microprofile.metrics.MetricUtil.getMetricName;
import static io.helidon.microprofile.metrics.MetricUtil.lookupAnnotation;
import static io.helidon.microprofile.metrics.MetricUtil.tags;

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
 * the array of tag values for that instance of the annotation,</li>
 * <li>a {@code Function} that accepts an instance of the annotation and returns
 * whether the name is absolute or not,
 * <li>the simple metric type name, and
 * <li>the {@code Class} for the metric type.
 * </ul>
 * For example, the constructor for the implementation that handles
 * {@code Metered} might use this:
 * <pre>
 * {@code
 *     super(registry,
 *             Metered.class,
 *             Metered::name,
 *             Metered::tags,
 *             Metered::absolute,
 *             metricTypeName,
 *             metricTypeClass);
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
    private final Registry hRegistry;
    private final Class<A> annotationClass;
    private final Function<A, String> nameFunction;
    private final Function<A, String[]> tagsFunction;
    private final Function<A, Boolean> isAbsoluteFunction;
    private final Map<AnnotatedElement, T> elementMetricMap = new ConcurrentHashMap<>();
    private final String metricTypeName;
    private final Class<T> metricClass;
    private final Map<String, String> universalTags; // Get global and app tags for later

    InterceptorBase(MetricRegistry registry,
                    Class<A> annotationClass,
                    Function<A, String> nameFunction,
                    Function<A, String[]> tagsFunction,
                    Function<A, Boolean> isAbsoluteFunction,
                    String metricTypeName,
                    Class<T> metricClass) {
        this.registry = registry;
        hRegistry = Registry.class.cast(registry);
        this.annotationClass = annotationClass;
        this.nameFunction = nameFunction;
        this.tagsFunction = tagsFunction;
        this.isAbsoluteFunction = isAbsoluteFunction;
        this.metricTypeName = metricTypeName;
        this.metricClass = metricClass;
        universalTags = new MetricID("base").getTags();
    }

    protected <T> Optional<T> getMetric(Map<MetricID, T> metricMap, MetricID metricID) {
        return Optional.ofNullable(metricMap.get(metricID));
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
            T metricInstance = getMetricForElement(element, getClass(context, element), lookupResult);

            Exception ex = null;
            A annot = lookupResult.getAnnotation();
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

    /**
     * Return the metric for the given site, either by returning the cached one associated with the site or by looking up the
     * metric in the registry.
     *
     * @param element the annotated element being invoked
     * @param clazz the type of metric
     * @param lookupResult the combination of the annotation and the matching type
     * @param <E> specific type of element
     * @return the metric to be updated for the annotated element being invoked
     */
    private <E extends Member & AnnotatedElement> T getMetricForElement(E element, Class<?> clazz,
            MetricUtil.LookupResult<A> lookupResult) {

        T metric = elementMetricMap.computeIfAbsent(element, e -> createMetricForElement(element, clazz, lookupResult));
        return metric;
    }

    /**
     * Retrieves from the registry -- and stores in the site-to-metric map -- the metric coresponding to the specified site.
     *
     * @param element the annotated element being invoked
     * @param clazz the type of metric
     * @param lookupResult the combinatino of the annotation and the matching type
     * @param <E> specific type of element
     * @return the metric retrieved from the registry and added to the site-to-metric map
     */
    private <E extends Member & AnnotatedElement> T createMetricForElement(E element, Class<?> clazz,
            MetricUtil.LookupResult<A> lookupResult) {

        /*
         * Build the metric name that should exist for this annotation site and look up all metric IDs associated with that name.
         * (This is very efficient in the registry.)
         */
        A annot = lookupResult.getAnnotation();
        MatchingType matchingType = lookupResult.getType();

        String metricName = getMetricName(element, clazz, matchingType, nameFunction.apply(annot),
                isAbsoluteFunction.apply(annot));
        Optional<Map.Entry<? extends Metric, List<MetricID>>> matchingEntry = hRegistry.getOptionalMetricWithIDsEntry(metricName);
        if (!matchingEntry.isPresent()) {
            throw new IllegalStateException(String.format("No %s with name %s found in registry [%s]", metricTypeName,
                    metricName, registry));
        }

        /*
         * Build a simple (no config use) metric ID for the metric corresponding to this annotation site.
         */
        Tag[] tagsFromAnnotation = tags(tagsFunction.apply(annot));
        SimpleMetricID simpleMetricID = new SimpleMetricID(metricName, universalTags, tagsFromAnnotation);

        /*
         * Find the metric ID associated with the metric name that matches on name and tags.
         */
        MetricID metricID = matchingEntry.get().getValue().stream()
                .filter(simpleMetricID::matches)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "No %s with name %s and matching tags %s found in registry", metricTypeName, metricName,
                        tagsFromAnnotation)));

        /*
         * Retrieve the metric corresponding to the matching metric ID.
         */
        Metric metric = registry.getMetrics().get(metricID);
        return metricClass.cast(metric);
    }

    /**
     * A near-replica of the MP MetricID, but one that does not repeatedly access config to get global and app-level tags.
     * Instead, those come from a single MP MetricID which we instantiate and keep for future reference.
     */
    private static class SimpleMetricID {

        private final String name;
        private final Map<String, String> tags;

        private SimpleMetricID(String name, Map<String, String> univTags, Tag... tagExprs) {
            this.name = name;
            tags = computeTags(univTags, tagExprs);
        }

        private static Map<String, String> computeTags(Map<String, String> univTags, Tag... tags) {
            if (univTags.isEmpty() && (tags == null || tags.length == 0)) {
                return Collections.emptyMap();
            }
            Map<String, String> result = new TreeMap<>(univTags);
            if (tags != null && tags.length > 0) {
                for (Tag tag : tags) {
                    result.put(tag.getTagName(), tag.getTagValue());
                }
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SimpleMetricID that = (SimpleMetricID) o;
            return equals(that.name, that.tags);
        }

        private boolean matches(MetricID metricID) {
            return equals(metricID.getName(), metricID.getTags());
        }

        private boolean equals(String otherName, Map<String, String> otherTags) {
            return name.equals(otherName) && tags.equals(otherTags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tags);
        }
    }
}
