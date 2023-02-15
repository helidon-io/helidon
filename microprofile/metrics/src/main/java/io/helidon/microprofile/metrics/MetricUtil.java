/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * Class MetricUtil.
 */
final class MetricUtil {
    private static final System.Logger LOGGER = System.getLogger(MetricUtil.class.getName());

    private MetricUtil() {
    }

    static <A extends Annotation> List<LookupResult<A>> lookupAnnotations(
            AnnotatedType<?> annotatedType,
            AnnotatedMember<?> annotatedMember,
            Class<A> annotClass,
            Map<Class<?>, MetricsCdiExtension.StereotypeMetricsInfo> stereotypeTypes) {
        List<LookupResult<A>> result = lookupAnnotations(annotatedMember, annotClass, stereotypeTypes);
        if (result.isEmpty()) {
            result = lookupAnnotations(annotatedType, annotClass, stereotypeTypes);
        }
        return result;
    }

    static <A extends Annotation>  List<LookupResult<A>> lookupAnnotations(
            Annotated annotated,
            Class<A> annotClass,
            Map<Class<?>, MetricsCdiExtension.StereotypeMetricsInfo> stereotypeMetricsInfo) {

        return metricsAnnotationsOnElement(annotated, stereotypeMetricsInfo)
                .filter(annotClass::isInstance)
                .map(annotation -> new LookupResult<>(matchingType(annotated), annotClass.cast(annotation)))
                .collect(Collectors.toList());
    }

    static Stream<Annotation> metricsAnnotationsOnElement(Annotated annotated,
                                                          Map<Class<?>, MetricsCdiExtension.StereotypeMetricsInfo>
                                                                  stereotypeMetricsInfo) {
        // We have to filter by annotation class ourselves using annotated.getAnnotations(), because
        // annotated.getAnnotations(Class) delegates to the Java method. That would bypass any annotations that had been
        // added dynamically to the configurator.

        // Further, we need to create lookup results not only for explicit metrics annotations on the annotated element but
        // also any (possibly multiple) metrics annotations via stereotypes.

        return Stream.concat(annotated.getAnnotations().stream()
                                     .filter(a -> MetricsCdiExtension.ALL_METRIC_ANNOTATIONS.contains(a.annotationType())),
                             annotated.getAnnotations().stream()
                                     .filter(a -> stereotypeMetricsInfo.containsKey(a.annotationType()))
                                     .flatMap(a -> stereotypeMetricsInfo.get(a.annotationType()).metricsAnnotations().stream())
        );
    }

    static <T extends Annotation> Stream<T> metricsAnnotationsOnElement(Annotated annotated,
                                                                        Class<T> annotationType,
                                                                        Map<Class<?>, MetricsCdiExtension.StereotypeMetricsInfo>
                                                                                stereotypeMetricsInfo) {
        return metricsAnnotationsOnElement(annotated, stereotypeMetricsInfo)
                .filter(annotationType::isInstance)
                .map(annotationType::cast);
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
    static <E extends Member & AnnotatedElement>
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
                    LOGGER.log(Level.WARNING, "Attribute 'absolute=true' in metric annotation ignored at class level");
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
        for (String tagString : tagStrings) {
            final int eq = tagString.indexOf("=");
            if (eq > 0) {
                final String tagName = tagString.substring(0, eq);
                final String tagValue = tagString.substring(eq + 1);
                result.add(new Tag(tagName, tagValue));
            }
        }
        return result.toArray(new Tag[0]);
    }

    static String normalize(Metric metricAnno, Function<Metric, String> fn) {
        return metricAnno != null ? normalize(fn.apply(metricAnno)) : null;
    }

    static String normalize(String value) {
        return value == null || value.isEmpty() ? null : value.trim();
    }

    static String chooseDefaultUnit(MetricType metricType) {
        return switch (metricType) {
            case METERED -> MetricUnits.PER_SECOND;
            case TIMER -> MetricUnits.NANOSECONDS;
            case SIMPLE_TIMER -> MetricUnits.SECONDS;
            default -> MetricUnits.NONE;
        };
    }

    enum MatchingType {
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
                && isConsistentUnit(existingMetadata.getUnit(), metricAnnotation.unit()));
    }

    private static boolean isConsistent(String existingValue, String metricAnnoValue) {
        // Treat empty (defaulted) @Metric String values as "match-anything."
        return metricAnnoValue.isEmpty() || existingValue.equals(metricAnnoValue);
    }

    private static boolean isConsistentUnit(String existingValue, String metricAnnoValue) {
        return metricAnnoValue.equals(MetricUnits.NONE) || existingValue.equals(metricAnnoValue);
    }

    private static String prefix(Metric metricAnno, InjectionPoint ip) {
        boolean isAbsolute = metricAnno != null && metricAnno.absolute();

        return isAbsolute ? "" : ip.getMember().getDeclaringClass().getName() + ".";
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

    static class LookupResult<A extends Annotation> {

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
         * Returns the matching type for this lookup result.
         *
         * @return matching type
         */
        MatchingType getType() {
            return type;
        }

        /**
         * Returns the annotation for the lookup result.
         *
         * @return the annotation
         */
        A getAnnotation() {
            return annotation;
        }
    }
}
