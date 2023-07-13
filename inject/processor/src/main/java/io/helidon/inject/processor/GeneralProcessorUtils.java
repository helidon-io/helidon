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

package io.helidon.inject.processor;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.api.RunLevel;
import io.helidon.inject.api.ServiceInfo;
import io.helidon.inject.api.ServiceInfoBasics;
import io.helidon.inject.tools.Options;
import io.helidon.inject.tools.TypeNames;
import io.helidon.inject.tools.TypeTools;

/**
 * Carries static methods that are agnostic to the active processing environment.
 *
 * @see ActiveProcessorUtils
 */
public final class GeneralProcessorUtils {
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
        Annotation runLevelAnno =
                Annotations.findFirst(RunLevel.class, service.annotations()).orElse(null);
        if (runLevelAnno != null) {
            return Optional.of(Integer.valueOf(runLevelAnno.value().orElseThrow()));
        }

        // RunLevel is not inheritable - we will therefore not search up the hierarchy

        return Optional.empty();
    }

    /**
     * Attempts to resolve the {@link Weight} value assigned to the provided service.
     *
     * @param service the service
     * @return the declared weight if available
     */
    static Optional<Double> toWeight(TypeInfo service) {
        Annotation weightAnno =
                Annotations.findFirst(Weight.class, service.annotations()).orElse(null);
        if (weightAnno != null) {
            return Optional.of(Double.valueOf(weightAnno.value().orElseThrow()));
        }

        // Weight is not inheritable - we will therefore not search up the hierarchy

        return Optional.empty();
    }

    /**
     * Attempts to resolve the {@code PostConstruct} method name assigned to the provided service.
     *
     * @param service the service
     * @return the post construct method if available
     */
    static Optional<String> toPostConstructMethod(TypeInfo service) {
        List<String> postConstructs = service.elementInfo().stream()
                .filter(it -> it.hasAnnotation(TypeNames.JAKARTA_POST_CONSTRUCT_TYPE))
                .map(TypedElementInfo::elementName)
                .toList();
        if (postConstructs.size() == 1) {
            return Optional.of(postConstructs.get(0));
        } else if (postConstructs.size() > 1) {
            throw new IllegalStateException("There can be at most one "
                                                    + TypeNames.JAKARTA_POST_CONSTRUCT
                                                    + " annotated method per type: " + service.typeName());
        }

        // PostConstruct is not inheritable - we will therefore not search up the hierarchy

        return Optional.empty();
    }

    /**
     * Attempts to resolve the {@code PreDestroy} method name assigned to the provided service.
     *
     * @param service the service
     * @return the pre destroy method if available
     */
    static Optional<String> toPreDestroyMethod(TypeInfo service) {
        List<String> preDestroys = service.elementInfo().stream()
                .filter(it -> it.hasAnnotation(TypeNames.JAKARTA_PRE_DESTROY_TYPE))
                .map(TypedElementInfo::elementName)
                .toList();
        if (preDestroys.size() == 1) {
            return Optional.of(preDestroys.get(0));
        } else if (preDestroys.size() > 1) {
            throw new IllegalStateException("There can be at most one "
                                                    + TypeNames.JAKARTA_PRE_DESTROY
                                                    + " annotated method per type: " + service.typeName());
        }

        // PreDestroy is not inheritable - we will therefore not search up the hierarchy
        //        if (service.superTypeInfo().isPresent()) {
        //            return toPreDestroyMethod(service.superTypeInfo().get());
        //        }

        return Optional.empty();
    }

    /**
     * Attempts to resolve the scope names of the provided service.
     *
     * @param service the service
     * @return the set of declared scope names
     */
    static Set<TypeName> toScopeNames(TypeInfo service) {
        Set<TypeName> scopeAnnotations = new LinkedHashSet<>();

        service.referencedTypeNamesToAnnotations()
                .forEach((typeName, listOfAnnotations) -> {
                    if (listOfAnnotations.stream()
                            .map(Annotation::typeName)
                            .anyMatch(TypeNames.JAKARTA_SCOPE_TYPE::equals)) {
                        scopeAnnotations.add(typeName);
                    }
                });

        if (Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
            boolean hasApplicationScope = service.hasAnnotation(TypeNames.JAKARTA_APPLICATION_SCOPED_TYPE);
            if (hasApplicationScope) {
                scopeAnnotations.add(TypeNames.JAKARTA_SINGLETON_TYPE);
                scopeAnnotations.add(TypeNames.JAKARTA_APPLICATION_SCOPED_TYPE);
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
    static Set<Qualifier> toQualifiers(TypeInfo service) {
        Set<Qualifier> result = new LinkedHashSet<>();

        for (Annotation anno : service.annotations()) {
            List<Annotation> metaAnnotations = service.referencedTypeNamesToAnnotations().get(anno.typeName());
            Optional<? extends Annotation> qual = findFirst(TypeNames.JAKARTA_QUALIFIER, metaAnnotations);
            if (qual.isPresent()) {
                result.add(Qualifier.create(anno));
            }
        }

        // note: should qualifiers be inheritable? Right now we assume not to support the jsr-330 spec.
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
    static Set<Qualifier> toQualifiers(TypedElementInfo element,
                                       TypeInfo service) {
        Set<Qualifier> result = new LinkedHashSet<>();

        for (Annotation anno : element.annotations()) {
            List<Annotation> metaAnnotations = service.referencedTypeNamesToAnnotations().get(anno.typeName());
            Optional<? extends Annotation> qual = (metaAnnotations == null)
                    ? Optional.empty() : findFirst(TypeNames.JAKARTA_QUALIFIER, metaAnnotations);
            if (qual.isPresent()) {
                result.add(Qualifier.create(anno));
            }
        }

        // note: should qualifiers be inheritable? Right now we assume not to support the jsr-330 spec (see note above).

        return result;
    }

    /**
     * Returns true if the provided type name is a {@code Provider<>} type.
     *
     * @param typeName the type name to check
     * @return true if the provided type is a provider type.
     */
    public static boolean isProviderType(TypeName typeName) {
        String name = typeName.name();
        return (
                name.equals(TypeNames.JAKARTA_PROVIDER)
                        || name.equals(TypeNames.JAVAX_PROVIDER)
                        || name.equals(TypeNames.INJECTION_POINT_PROVIDER)
                        || TypeNames.INJECTION_SERVICE_PROVIDER.equals(name));
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
     * @param annoType the annotation type
     * @param annotations the set of annotations to look in
     * @return the optional annotation if there is a match
     */
    static Optional<? extends Annotation> findFirst(String annoType,
                                                    Collection<? extends Annotation> annotations) {
        if (annotations == null) {
            return Optional.empty();
        }

        Optional<? extends Annotation> anno = Annotations.findFirst(annoType, annotations);
        if (anno.isPresent()) {
            return anno;
        }

        return Annotations.findFirst(TypeTools.oppositeOf(annoType), annotations);
    }

    static ServiceInfoBasics toBasicServiceInfo(TypeInfo service) {
        return ServiceInfo.builder()
                .serviceTypeName(service.typeName())
                .update(it -> toWeight(service).ifPresent(it::declaredWeight))
                .update(it -> toRunLevel(service).ifPresent(it::declaredRunLevel))
                .scopeTypeNames(toScopeNames(service))
                .build();
    }

}
