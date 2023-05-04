/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.processor;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.QualifierAndValueDefault;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.api.ServiceInfoBasics;
import io.helidon.pico.api.ServiceInfoDefault;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.TypeNames;
import io.helidon.pico.tools.TypeTools;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

/**
 * Carries static methods that are agnostic to the active processing environment.
 *
 * @see ActiveProcessorUtils
 */
final class GeneralProcessorUtils {
    private GeneralProcessorUtils() {
    }

    /**
     * Determines the root throwable stack trace element from a chain of throwable causes.
     *
     * @param t the throwable
     * @return the root throwable error stack trace element
     */
    static StackTraceElement rootStackTraceElementOf(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getStackTrace()[0];
    }

    /**
     * Converts a collection to a comma delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    static String toString(Collection<?> coll) {
        return toString(coll, null, null);
    }

    /**
     * Provides specialization in concatenation, allowing for a function to be called for each element as well as to
     * use special separators.
     *
     * @param coll      the collection
     * @param fnc       the optional function to translate the collection item to a string
     * @param separator the optional separator
     * @param <T> the type held by the collection
     * @return the concatenated, delimited string value
     */
    static <T> String toString(Collection<T> coll,
                               Function<T, String> fnc,
                               String separator) {
        Function<T, String> fn = (fnc == null) ? String::valueOf : fnc;
        separator = (separator == null) ? ", " : separator;
        return coll.stream().map(fn).collect(Collectors.joining(separator));
    }

    /**
     * Splits given using a comma-delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @return the list of string values
     */
    static List<String> toList(String str) {
        return toList(str, ",");
    }

    /**
     * Splits a string given a delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @param delim the delimiter
     * @return the list of string values
     */
    static List<String> toList(String str,
                               String delim) {
        String[] split = str.split(delim);
        return Arrays.stream(split).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Will return non-empty File if the uri represents a local file on the fs.
     *
     * @param uri the uri of the artifact
     * @return the file instance, or empty if not local
     */
    static Optional<Path> toPath(URI uri) {
        if (uri.getHost() != null) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(uri));
    }

    /**
     * Attempts to resolve the {@link RunLevel} value assigned to the provided service.
     *
     * @param service the service
     * @return the declared run level if available
     */
    static Optional<Integer> toRunLevel(TypeInfo service) {
        AnnotationAndValue runLevelAnno =
                AnnotationAndValueDefault.findFirst(RunLevel.class, service.annotations()).orElse(null);
        if (runLevelAnno != null) {
            return Optional.of(Integer.valueOf(runLevelAnno.value().orElseThrow()));
        }

        // RunLevel is not inheritable - we will therefore not search up the hierarchy
//        if (service.superTypeInfo().isPresent()) {
//            return toRunLevel(service.superTypeInfo().get());
//        }

        return Optional.empty();
    }

    /**
     * Attempts to resolve the {@link Weight} value assigned to the provided service.
     *
     * @param service the service
     * @return the declared weight if available
     */
    static Optional<Double> toWeight(TypeInfo service) {
        AnnotationAndValue weightAnno =
                AnnotationAndValueDefault.findFirst(Weight.class, service.annotations()).orElse(null);
        if (weightAnno != null) {
            return Optional.of(Double.valueOf(weightAnno.value().orElseThrow()));
        }

        // Weight is not inheritable - we will therefore not search up the hierarchy
//        if (service.superTypeInfo().isPresent()) {
//            return toWeight(service.superTypeInfo().get());
//        }

        return Optional.empty();
    }

    /**
     * Attempts to resolve the {@link PostConstruct} method name assigned to the provided service.
     *
     * @param service the service
     * @return the post construct method if available
     */
    static Optional<String> toPostConstructMethod(TypeInfo service) {
        List<String> postConstructs = service.elementInfo().stream()
                .filter(it -> {
                    AnnotationAndValue anno = findFirst(PostConstruct.class, it.annotations()).orElse(null);
                    return (anno != null);
                })
                .map(TypedElementName::elementName)
                .toList();
        if (postConstructs.size() == 1) {
            return Optional.of(postConstructs.get(0));
        } else if (postConstructs.size() > 1) {
            throw new IllegalStateException("There can be at most one "
                                                    + PostConstruct.class.getName()
                                                    + " annotated method per type: " + service.typeName());
        }

        if (service.superTypeInfo().isPresent()) {
            return toPostConstructMethod(service.superTypeInfo().get());
        }

        return Optional.empty();
    }

    /**
     * Attempts to resolve the {@link PreDestroy} method name assigned to the provided service.
     *
     * @param service the service
     * @return the pre destroy method if available
     */
    static Optional<String> toPreDestroyMethod(TypeInfo service) {
        List<String> preDestroys = service.elementInfo().stream()
                .filter(it -> {
                    AnnotationAndValue anno = findFirst(PreDestroy.class, it.annotations()).orElse(null);
                    return (anno != null);
                })
                .map(TypedElementName::elementName)
                .toList();
        if (preDestroys.size() == 1) {
            return Optional.of(preDestroys.get(0));
        } else if (preDestroys.size() > 1) {
            throw new IllegalStateException("There can be at most one "
                                                    + PreDestroy.class.getName()
                                                    + " annotated method per type: " + service.typeName());
        }

        if (service.superTypeInfo().isPresent()) {
            return toPreDestroyMethod(service.superTypeInfo().get());
        }

        return Optional.empty();
    }

    /**
     * Attempts to resolve the scope names of the provided service.
     *
     * @param service the service
     * @return the set of declared scope names
     */
    static Set<String> toScopeNames(TypeInfo service) {
        Set<String> scopeAnnotations = new LinkedHashSet<>();

        service.referencedTypeNamesToAnnotations()
                .forEach((typeName, listOfAnnotations) -> {
                    if (listOfAnnotations.stream()
                            .map(it -> it.typeName().name())
                            .anyMatch(it -> it.equals(TypeNames.JAKARTA_SCOPE))) {
                        scopeAnnotations.add(typeName.name());
                    }
                });

        if (Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
            boolean hasApplicationScope = findFirst(TypeNames.JAKARTA_APPLICATION_SCOPED, service.annotations()).isPresent();
            if (hasApplicationScope) {
                scopeAnnotations.add(Singleton.class.getName());
                scopeAnnotations.add(TypeNames.JAKARTA_APPLICATION_SCOPED);
            }
        }

        return scopeAnnotations;
    }

    /**
     * Returns the type hierarchy of the provided service type info.
     *
     * @param service the service
     * @return the type hierarchy
     */
    static List<TypeName> toServiceTypeHierarchy(TypeInfo service) {
        List<TypeName> result = new ArrayList<>();
        result.add(service.typeName());
        service.superTypeInfo().ifPresent(it -> result.addAll(toServiceTypeHierarchy(it)));
        return result;
    }

    /**
     * Returns the qualifiers assigned to the provided service type info.
     *
     * @param service the service
     * @return the qualifiers of the service
     */
    static Set<QualifierAndValue> toQualifiers(TypeInfo service) {
        Set<QualifierAndValue> result = new LinkedHashSet<>();

        for (AnnotationAndValue anno : service.annotations()) {
            List<AnnotationAndValue> metaAnnotations = service.referencedTypeNamesToAnnotations().get(anno.typeName());
            Optional<? extends AnnotationAndValue> qual = findFirst(Qualifier.class, metaAnnotations);
            if (qual.isPresent()) {
                result.add(QualifierAndValueDefault.convert(anno));
            }
        }

        // note: should qualifiers be inheritable? Right now we assume not to support the jsr-330 spec
        //        service.superTypeInfo().ifPresent(it -> result.addAll(toQualifiers(it)));
        //        service.interfaceTypeInfo().forEach(it -> result.addAll(toQualifiers(it)));

        return result;
    }

    /**
     * Returns the qualifiers assigned to the provided typed element belonging to the associated service.
     *
     * @param element the typed element (e.g., field, method, or constructor)
     * @param service the service for which the typed element belongs
     * @return the qualifiers associated with the provided element
     */
    static Set<QualifierAndValue> toQualifiers(TypedElementName element,
                                               TypeInfo service) {
        Set<QualifierAndValue> result = new LinkedHashSet<>();

        for (AnnotationAndValue anno : element.annotations()) {
            List<AnnotationAndValue> metaAnnotations = service.referencedTypeNamesToAnnotations().get(anno.typeName());
            Optional<? extends AnnotationAndValue> qual = (metaAnnotations == null)
                    ? Optional.empty() : findFirst(Qualifier.class, metaAnnotations);
            if (qual.isPresent()) {
                result.add(QualifierAndValueDefault.convert(anno));
            }
        }

        // note: should qualifiers be inheritable? Right now we assume not to support the jsr-330 spec

        return result;
    }

    /**
     * Returns true if the provided type name is a {@code Provider<>} type.
     *
     * @param typeName the type name to check
     * @return true if the provided type is a provider type.
     */
    static boolean isProviderType(TypeName typeName) {
        String name = typeName.name();
        return (name.equals(TypeNames.JAKARTA_PROVIDER)
                        || name.equals(TypeNames.JAVAX_PROVIDER)
                        || name.equals(TypeNames.PICO_INJECTION_POINT_PROVIDER));
    }

    /**
     * Simple check to see the passed String value is non-null and non-blank.
     *
     * @param val the value to check
     * @return true if non-null and non-blank
     */
    static boolean hasValue(String val) {
        return (val != null && !val.isBlank());
    }

    /**
     * Looks for either a jakarta or javax annotation.
     *
     * @param jakartaAnno the jakarta annotation class type
     * @param annotations the set of annotations to look in
     * @return the optional annotation if there is a match
     */
    static Optional<? extends AnnotationAndValue> findFirst(Class<? extends Annotation> jakartaAnno,
                                                            Collection<? extends AnnotationAndValue> annotations) {
        return findFirst(jakartaAnno.getName(), annotations);
    }

    static ServiceInfoBasics toBasicServiceInfo(TypeInfo service) {
        return ServiceInfoDefault.builder()
                .serviceTypeName(service.typeName().name())
                .declaredWeight(toWeight(service))
                .declaredRunLevel(toRunLevel(service))
                .scopeTypeNames(toScopeNames(service))
                .build();
    }

    static Optional<? extends AnnotationAndValue> findFirst(String jakartaAnnoName,
                                                            Collection<? extends AnnotationAndValue> annotations) {
        Optional<? extends AnnotationAndValue> anno = AnnotationAndValueDefault.findFirst(jakartaAnnoName, annotations);
        if (anno.isPresent()) {
            return anno;
        }

        return AnnotationAndValueDefault.findFirst(TypeTools.oppositeOf(jakartaAnnoName), annotations);
    }

}
