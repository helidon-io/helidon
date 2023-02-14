/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Class MetricUtil.
 */
public final class MetricUtil {
    private static final Logger LOGGER = Logger.getLogger(MetricUtil.class.getName());

    private MetricUtil() {
    }

    /**
     * DO NOT USE THIS METHOD please, it will be removed.
     * <p>
     *     Instead, see {@link MatchingType}.
     * </p>
     *
     * @param element element
     * @param annotClass annotation class
     * @param clazz class
     * @param <E> element type
     * @param <A> annotation type
     * @return lookup result
     * @deprecated This method is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored.
     *      This method will be removed outside of major version of Helidon.
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static <E extends Member & AnnotatedElement, A extends Annotation>
    LookupResult<A> lookupAnnotation(E element, Class<? extends Annotation> annotClass, Class<?> clazz) {
        // First check annotation on element
        A annotation = (A) element.getAnnotation(annotClass);
        if (annotation != null) {
            return new LookupResult<>(MatchingType.METHOD, annotation);
        }
        // Finally check annotations on class
        annotation = (A) element.getDeclaringClass().getAnnotation(annotClass);
        if (annotation == null) {
            annotation = (A) clazz.getAnnotation(annotClass);
        }
        return annotation == null ? null : new LookupResult<>(MatchingType.CLASS, annotation);
    }

    @Deprecated
    static <A extends Annotation> LookupResult<A> lookupAnnotation(
            AnnotatedType<?> annotatedType,
            AnnotatedMethod<?> annotatedMethod,
            Class<A> annotClass) {
        A annotation = annotatedMethod.getAnnotation(annotClass);
        if (annotation != null) {
            return new LookupResult<>(matchingType(annotatedMethod), annotation);
        }

        annotation = annotatedType.getAnnotation(annotClass);
        if (annotation == null) {
            annotation = annotatedType.getJavaClass().getAnnotation(annotClass);
        }
        return annotation == null ? null : new LookupResult<>(MatchingType.CLASS, annotation);
    }

    @Deprecated
    static <A extends Annotation> List<LookupResult<A>> lookupAnnotations(
            AnnotatedType<?> annotatedType,
            AnnotatedMember<?> annotatedMember,
            Class<A> annotClass) {
        List<LookupResult<A>> result = lookupAnnotations(annotatedMember, annotClass);
        if (result.isEmpty()) {
            result = lookupAnnotations(annotatedType, annotClass);
        }
        return result;
    }

    static <A extends Annotation>  List<LookupResult<A>> lookupAnnotations(Annotated annotated,
            Class<A> annotClass) {
        // We have to filter by annotation class ourselves, because annotatedMethod.getAnnotations(Class) delegates
        // to the Java method. That would bypass any annotations that had been added dynamically to the configurator.
        return annotated.getAnnotations().stream()
                .filter(annotClass::isInstance)
                .map(annotation -> new LookupResult<>(matchingType(annotated), annotClass.cast(annotation)))
                .collect(Collectors.toList());
    }

    /**
     * This method is intended only for other Helidon components.
     *
     * @param element such as method
     * @param clazz class
     * @param matchingType type to match
     * @param explicitName name
     * @param absolute if absolute
     * @param <E> type of element
     *
     * @return name of the metric
     */
    @Deprecated
    public static <E extends Member & AnnotatedElement>
    String getMetricName(Member element, Class<?> clazz, MatchingType matchingType, String explicitName, boolean absolute) {
        String result;
        if (matchingType == MatchingType.METHOD) {
            result = explicitName == null || explicitName.isEmpty()
                    ? getElementName(element, clazz) : explicitName;
            if (!absolute) {
                Class<?> declaringClass = clazz;
                if (element instanceof Method) {
                    // We need to find the declaring class if a method
                    List<Method> methods = Arrays.asList(declaringClass.getDeclaredMethods());
                    while (!methods.contains(element)) {
                        declaringClass = declaringClass.getSuperclass();
                        methods = Arrays.asList(declaringClass.getDeclaredMethods());
                    }
                }
                result = declaringClass.getName() + '.' + result;
            }
        } else if (matchingType == MatchingType.CLASS) {
            if (explicitName == null || explicitName.isEmpty()) {
                result = getElementName(element, clazz);
                if (!absolute) {
                    result = clazz.getName() + '.' + result;
                }
            } else {
                // Absolute must be false at class level, issue warning here
                if (absolute) {
                    LOGGER.warning(() -> "Attribute 'absolute=true' in metric annotation ignored at class level");
                }
                result = clazz.getPackage().getName() + '.' + explicitName
                        + '.' + getElementName(element, clazz);
            }
        } else {
            throw new InternalError("Unknown matching type");
        }
        return result;
    }

    /**
     * Computes the proper metric name for an annotated parameter, accounting for any {@code Metric} annotation that might be
     * present on the parameter.
     *
     * @param annotatedParameter annotated parameter
     * @return metric name
     */
    static String metricName(AnnotatedParameter<?> annotatedParameter) {
        Member member = annotatedParameter.getDeclaringCallable().getJavaMember();
        return metricName(member.getDeclaringClass().getName() + ".",
                          annotatedParameter.getJavaParameter().getName(),
                          annotatedParameter);
    }

    /**
     * Computes the proper metric name for an annotated field, accounting for any {@code Metric} annotation that might be
     * present on the field.
     *
     * @param annotatedField annoated field
     * @return metric name
     */
    static String metricName(AnnotatedField<?> annotatedField) {
        return metricName(annotatedField.getJavaMember().getDeclaringClass().getName() + ".",
                          annotatedField.getJavaMember().getName(),
                          annotatedField);
    }

    static String metricName(Metric metricAnno, InjectionPoint ip) {
        return prefix(metricAnno, ip) + shortName(metricAnno, ip) + ctorSuffix(ip);
    }

    private static String metricName(String prefixFromDeclaration, String suffixFromDeclaration, Annotated annotated) {
        String prefix = prefixFromDeclaration;
        String suffix = suffixFromDeclaration;
        if (annotated.isAnnotationPresent(Metric.class)) {
            Metric metricAnno = annotated.getAnnotation(Metric.class);
            if (metricAnno.absolute()) {
                prefix = "";
            }
            if (!metricAnno.name().isEmpty()) {
                suffix = metricAnno.name();
            }
        }
        return prefix + suffix;
    }

    /**
     * Register a metric.
     *
     * @param registry the metric registry in which to register the metric
     * @param element the annotated element
     * @param clazz the annotated class
     * @param annotation the annotation to register
     * @param type the {@link MatchingType} indicating the type of annotated element
     * @param <E> the annotated element type
     */
    @Deprecated
    public static <E extends Member & AnnotatedElement>
    void registerMetric(MetricRegistry registry, E element, Class<?> clazz, Annotation annotation, MatchingType type) {

        if (annotation instanceof Counted) {
            Counted counted = (Counted) annotation;
            String metricName = getMetricName(element, clazz, type, counted.name().trim(), counted.absolute());
            String displayName = counted.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(counted.description().trim())
                    .withType(MetricType.COUNTER)
                    .withUnit(counted.unit().trim())
                    .reusable(counted.reusable()).build();
            registry.counter(meta, tags(counted.tags()));
            LOGGER.fine(() -> "### Registered counter " + metricName);
        } else if (annotation instanceof Metered) {
            Metered metered = (Metered) annotation;
            String metricName = getMetricName(element, clazz, type, metered.name().trim(), metered.absolute());
            String displayName = metered.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(metered.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(metered.unit().trim())
                    .reusable(metered.reusable()).build();
            registry.meter(meta, tags(metered.tags()));
            LOGGER.fine(() -> "### Registered meter " + metricName);
        } else if (annotation instanceof ConcurrentGauge) {
            ConcurrentGauge concurrentGauge = (ConcurrentGauge) annotation;
            String metricName = getMetricName(element, clazz, type, concurrentGauge.name().trim(),
                    concurrentGauge.absolute());
            String displayName = concurrentGauge.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(concurrentGauge.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(concurrentGauge.unit().trim()).build();
            registry.concurrentGauge(meta, tags(concurrentGauge.tags()));
            LOGGER.fine(() -> "### Registered ConcurrentGauge " + metricName);
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            String metricName = getMetricName(element, clazz, type, timed.name().trim(), timed.absolute());
            String displayName = timed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(timed.description().trim())
                    .withType(MetricType.TIMER)
                    .withUnit(timed.unit().trim())
                    .reusable(timed.reusable()).build();
            registry.timer(meta, tags(timed.tags()));
            LOGGER.fine(() -> "### Registered timer " + metricName);
        } else if (annotation instanceof SimplyTimed) {
            SimplyTimed simplyTimed = (SimplyTimed) annotation;
            String metricName = getMetricName(element, clazz, type, simplyTimed.name().trim(), simplyTimed.absolute());
            String displayName = simplyTimed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(simplyTimed.description().trim())
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(simplyTimed.unit().trim())
                    .reusable(simplyTimed.reusable()).build();
            registry.simpleTimer(meta, tags(simplyTimed.tags()));
            LOGGER.fine(() -> "### Registered simple timer " + metricName);
        }
    }

    /**
     * Register a metric.
     *
     * @param element the annotated element
     * @param clazz the annotated class
     * @param lookupResult the annotation lookup result
     * @param <E> the annotated element type
     */
    public static <E extends Member & AnnotatedElement>
    void registerMetric(E element, Class<?> clazz, LookupResult<? extends Annotation> lookupResult) {
        registerMetric(element, clazz, lookupResult.getAnnotation(), lookupResult.getType());
    }

    /**
     * Register a metric.
     *
     * @param element the annotated element
     * @param clazz the annotated class
     * @param annotation the annotation to register
     * @param type the {@link MatchingType} indicating the type of annotated element
     * @param <E> the annotated element type
     */
    public static <E extends Member & AnnotatedElement>
    void registerMetric(E element, Class<?> clazz, Annotation annotation, MatchingType type) {
        MetricRegistry registry = getMetricRegistry();

        if (annotation instanceof Counted) {
            Counted counted = (Counted) annotation;
            String metricName = getMetricName(element, clazz, type, counted.name().trim(), counted.absolute());
            String displayName = counted.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(counted.description().trim())
                    .withType(MetricType.COUNTER)
                    .withUnit(counted.unit().trim())
                    .reusable(counted.reusable()).build();
            registry.counter(meta);
            LOGGER.fine(() -> "### Registered counter " + metricName);
        } else if (annotation instanceof Metered) {
            Metered metered = (Metered) annotation;
            String metricName = getMetricName(element, clazz, type, metered.name().trim(), metered.absolute());
            String displayName = metered.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(metered.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(metered.unit().trim())
                    .reusable(metered.reusable()).build();
            registry.meter(meta);
            LOGGER.fine(() -> "### Registered meter " + metricName);
        } else if (annotation instanceof ConcurrentGauge) {
            ConcurrentGauge concurrentGauge = (ConcurrentGauge) annotation;
            String metricName = getMetricName(element, clazz, type, concurrentGauge.name().trim(),
                    concurrentGauge.absolute());
            String displayName = concurrentGauge.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(concurrentGauge.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(concurrentGauge.unit().trim()).build();
            registry.concurrentGauge(meta);
            LOGGER.fine(() -> "### Registered ConcurrentGauge " + metricName);
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            String metricName = getMetricName(element, clazz, type, timed.name().trim(), timed.absolute());
            String displayName = timed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(timed.description().trim())
                    .withType(MetricType.TIMER)
                    .withUnit(timed.unit().trim())
                    .reusable(timed.reusable()).build();
            registry.timer(meta);
            LOGGER.fine(() -> "### Registered timer " + metricName);
        } else if (annotation instanceof SimplyTimed) {
            SimplyTimed simplyTimed = (SimplyTimed) annotation;
            String metricName = getMetricName(element, clazz, type, simplyTimed.name().trim(), simplyTimed.absolute());
            String displayName = simplyTimed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(simplyTimed.description().trim())
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(simplyTimed.unit().trim())
                    .reusable(simplyTimed.reusable()).build();
            registry.timer(meta);
            LOGGER.fine(() -> "### Registered simple timer " + metricName);
        }
    }

    private static MetricRegistry getMetricRegistry() {
        return RegistryProducer.getDefaultRegistry();
    }

    static String getElementName(Member element, Class<?> clazz) {
        return element instanceof Constructor ? clazz.getSimpleName() : element.getName();
    }

    static Tag[] tags(Metric metricAnno) {
        return metricAnno != null ? tags(metricAnno.tags()) : new Tag[0];
    }

    static Tag[] tags(String[] tagStrings) {
        if (tagStrings == null) {
            return new Tag[0];
        }
        final List<Tag> result = new ArrayList<>();
        for (int i = 0; i < tagStrings.length; i++) {
            final int eq = tagStrings[i].indexOf("=");
            if (eq > 0) {
                final String tagName = tagStrings[i].substring(0, eq);
                final String tagValue = tagStrings[i].substring(eq + 1);
                result.add(new Tag(tagName, tagValue));
            }
        }
        return result.toArray(new Tag[result.size()]);
    }

    static String normalize(Metric metricAnno, Function<Metric, String> fn) {
        return metricAnno != null ? normalize(fn.apply(metricAnno)) : null;
    }

    static String normalize(String value) {
        return value == null || value.isEmpty() ? null : value.trim();
    }

    static String chooseDefaultUnit(MetricType metricType) {
        String result;
        switch (metricType) {
            case METERED:
                result = MetricUnits.PER_SECOND;
                break;

            case TIMER:
                result = MetricUnits.NANOSECONDS;
                break;

            case SIMPLE_TIMER:
                result = MetricUnits.SECONDS;
                break;

            default:
                result = MetricUnits.NONE;
        }
        return result;
    }

    /**
     * DO NOT USE THIS CLASS please.
     *
     * Types of possible matching (which influence default metric naming, for example).
     * @deprecated This class is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored
     */
    @Deprecated
    public enum MatchingType {
        /**
         * Method.
         */
        METHOD,
        /**
         * Class.
         */
        CLASS,
        /**
         * Field.
         */
        FIELD,
        /**
         * Parameter.
         */
        PARAMETER;
    }

    static boolean checkConsistentMetadata(
            String metricName,
            Metadata existingMetadata,
            MetricType metricType,
            Metric metricAnnotation) {

        return isConsistent(existingMetadata.getName(), metricName)
            && existingMetadata.getTypeRaw().equals(metricType)
            && (metricAnnotation == null
               || isConsistent(existingMetadata.getDescription(), metricAnnotation.description())
                  && isConsistent(existingMetadata.getDisplayName(), metricAnnotation.displayName())
                  && isConsistent(existingMetadata.getUnit(), metricAnnotation.unit()));
    }


    private static boolean isConsistent(String existingValue, String metricAnnoValue) {
        // Treat empty (defaulted) @Metric String values as "match-anything."
        return metricAnnoValue.isEmpty() || existingValue.equals(metricAnnoValue);
    }

    private static boolean isConsistent(Optional<String> existingValue, String metricAnnoValue) {
        // If the Optional value derived from the @Metric annotation is empty or is NONE (the default) then it
        // matches any existing value.
        if (metricAnnoValue.isEmpty() || metricAnnoValue.equals(MetricUnits.NONE)) {
            return true;
        }
        return existingValue.map(valueFromExistingMetadata -> isConsistent(valueFromExistingMetadata, metricAnnoValue))
                .orElse(false); // The metric anno value is non-empty but the existing metadata value is empty. No match.
    }



    private static String prefix(Metric metricAnno, InjectionPoint ip) {
        boolean isAbsolute = metricAnno != null && metricAnno.absolute();

        String prefix = isAbsolute ? "" : ip.getMember().getDeclaringClass().getName() + ".";
        //        if (ip.getAnnotated() instanceof AnnotatedParameter && !isAbsolute) {
        //            prefix += ip.getMember().metricName() + ".";
        //        }
        return prefix;
    }

    private static String shortName(Metric metricAnno, InjectionPoint ip) {
        if (metricAnno != null && !metricAnno.name().isEmpty()) {
            return metricAnno.name();
        }
        return ip.getAnnotated() instanceof AnnotatedParameter
                ? ((AnnotatedParameter<?>) ip.getAnnotated()).getJavaParameter().getName()
                : ip.getMember().getName();
    }

    private static String ctorSuffix(InjectionPoint ip) {
        return ip.getMember() instanceof Constructor ? ".new" : "";
    }


    private static MatchingType matchingType(Annotated annotated) {
        return annotated instanceof AnnotatedMember
                ? (((AnnotatedMember<?>) annotated).getJavaMember() instanceof Executable
                    ? MatchingType.METHOD : MatchingType.CLASS)
                : annotated instanceof AnnotatedParameter
                        ? MatchingType.PARAMETER
                        : MatchingType.CLASS;
    }

    /**
     * DO NOT USE THIS CLASS please.
     * @param <A> type of annotation
     * @deprecated This class is made public to migrate from metrics1 to metrics2 for gRPC, this should be refactored
     */
    @Deprecated
    public static class LookupResult<A extends Annotation> {

        private final MatchingType type;

        private final A annotation;

        /**
         * Constructor.
         *
         * @param type       The type of matching.
         * @param annotation The annotation.
         */
        LookupResult(MatchingType type, A annotation) {
            this.type = type;
            this.annotation = annotation;
        }

        /**
         *
         * @return the type of match found in this lookup result
         */
        public MatchingType getType() {
            return type;
        }

        /**
         *
         * @return the annotation matched in this lookup result
         */
        public A getAnnotation() {
            return annotation;
        }
    }
}
