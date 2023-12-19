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

package io.helidon.inject.maven.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.Codegen;
import io.helidon.codegen.scan.ScanTypeInfoFactory;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.codegen.InjectCodegenTypes;
import io.helidon.inject.codegen.InjectOptions;
import io.helidon.inject.service.Qualifier;

import io.github.classgraph.ClassInfo;

/**
 * An equivalent of inject annotation processor that takes care of all extensions, and provides only valid annotations.
 */
class HelidonScanProcessor {
    private static final Annotation DESCRIBE = Annotation.create(InjectCodegenTypes.INJECTION_SERVICE);
    private static final TypeName GENERATOR = TypeName.create(HelidonScanProcessor.class);
    private static final Annotation QUALIFIER_ANNOTATION = Annotation.create(InjectCodegenTypes.INJECTION_QUALIFIER);

    private final Codegen codegen;
    private final MavenScanContext ctx;
    private final boolean strictJsr330;

    HelidonScanProcessor(MavenScanContext ctx) {
        this.ctx = ctx;
        this.codegen = Codegen.create(ctx, GENERATOR);
        this.strictJsr330 = ctx.options().enabled(InjectOptions.JSR_330_STRICT);
    }

    /**
     * Process the currently scanned result and only handle types that match the provided predicate.
     *
     * @param candidates              candidate types
     * @param serviceTypeToQualifiers additional qualifiers
     * @return whether anything was generated
     */
    boolean process(Set<ClassInfo> candidates, Map<TypeName, Set<Qualifier>> serviceTypeToQualifiers) {
        // there will be just one round when using maven, as we generate sources, the rest should be handled by compiler

        // now we have a list of types that we want to process, we just need to make sure they are from the same module
        // or they are all module-less
        List<TypeInfo> typesToProcess = candidates.stream()
                .flatMap(it -> ScanTypeInfoFactory.create(ctx, it).stream())
                .toList();

        if (!serviceTypeToQualifiers.isEmpty()) {
            typesToProcess = addQualifiers(typesToProcess, serviceTypeToQualifiers);
        }
        if (strictJsr330) {
            typesToProcess = jsr330Types(typesToProcess);
        }

        // now we have mapped all annotations, elements, and types, so we can call codegen
        codegen.process(typesToProcess);
        // as we only have one round, we also finish processing
        codegen.processingOver();

        return ctx.filer().generatedSources();
    }

    private List<TypeInfo> addQualifiers(List<TypeInfo> typesToProcess, Map<TypeName, Set<Qualifier>> serviceTypeToQualifiers) {
        List<TypeInfo> result = new ArrayList<>();

        for (TypeInfo toProcess : typesToProcess) {
            Set<Qualifier> qualifiers = serviceTypeToQualifiers.get(toProcess.typeName());
            if (qualifiers != null) {
                result.add(addQualifiers(toProcess, qualifiers));
            } else {
                result.add(toProcess);
            }
        }

        return result;
    }

    private TypeInfo addQualifiers(TypeInfo toProcess, Set<Qualifier> qualifiers) {
        // and we also need to add the meta annotation for this annotation, so it is identified as a qualifier
        Map<TypeName, List<Annotation>> refs = new LinkedHashMap<>(toProcess.referencedTypeNamesToAnnotations());

        TypeInfo.Builder builder = TypeInfo.builder(toProcess);
        for (Qualifier qualifier : qualifiers) {
            builder.addAnnotation(Annotation.builder()
                                          .typeName(qualifier.typeName())
                                          .values(qualifier.values())
                                          .build());

            refs.compute(qualifier.typeName(), (it, list) -> {
                if (list == null) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(list);
            }).add(QUALIFIER_ANNOTATION);
        }

        builder.referencedTypeNamesToAnnotations(refs);
        return builder.build();
    }

    private List<TypeInfo> jsr330Types(List<TypeInfo> typesToProcess) {
        List<TypeInfo> result = new ArrayList<>();

        Set<TypeName> supportedAnnotations = codegen.supportedAnnotations();
        Set<String> supportedAnnotationPackages = codegen.supportedAnnotationPackagePrefixes();
        for (TypeInfo toProcess : typesToProcess) {
            if (noRelevantAnnotation(supportedAnnotations, supportedAnnotationPackages, toProcess)) {
                jsr330Type(result, toProcess);
            } else {
                result.add(toProcess);
            }
        }

        return result;
    }

    private void jsr330Type(List<TypeInfo> result, TypeInfo toProcess) {
        /*
        We may want to revisit this - maybe we should only consider a type to be a service only if it is used
        as an injection contract by another service in the same jar?
         */

        AccessModifier accessModifier = toProcess.accessModifier();
        if (accessModifier == AccessModifier.PRIVATE) {
            return;
        }
        // must have an accessible to args constructor
        Optional<TypedElementInfo> constructor = toProcess.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.CONSTRUCTOR)
                .filter(it -> it.parameterArguments().isEmpty())
                .filter(it -> it.accessModifier() != AccessModifier.PRIVATE)
                .findFirst();
        if (constructor.isEmpty()) {
            return;
        }

        result.add(TypeInfo.builder(toProcess)
                .annotations(addServiceAnnotation(toProcess.annotations()))
                .build());
    }

    private List<Annotation> addServiceAnnotation(List<Annotation> annotations) {
        List<Annotation> result = new ArrayList<>(annotations);
        result.add(DESCRIBE);
        return result;
    }

    private boolean noRelevantAnnotation(Set<TypeName> annotations,
                                         Set<String> annotationPackages,
                                         TypeInfo toProcess) {
        // type annotations
        boolean hasIt = hasAnnotation(annotations, annotationPackages, toProcess.annotations());
        if (hasIt) {
            return false;
        }
        // field and method annotations
        for (TypedElementInfo typedElementInfo : toProcess.elementInfo()) {
            hasIt = hasAnnotation(annotations, annotationPackages, typedElementInfo.annotations());
            if (hasIt) {
                return false;
            }
            if (typedElementInfo.kind() == ElementKind.METHOD
                    || typedElementInfo.kind() == ElementKind.CONSTRUCTOR) {
                for (TypedElementInfo param : typedElementInfo.parameterArguments()) {
                    hasIt = hasAnnotation(annotations, annotationPackages, param.annotations());
                    if (hasIt) {
                        return false;
                    }
                }
            }
            // do not care about other
        }

        return true;
    }

    private boolean hasAnnotation(Set<TypeName> annotations,
                                  Set<String> annotationPackages,
                                  List<Annotation> annotationsPresent) {
        for (Annotation annotation : annotationsPresent) {
            TypeName typeName = annotation.typeName();
            if (annotations.contains(typeName)) {
                return true;
            }
            String packageName = typeName.packageName() + ".";
            for (String annotationPackage : annotationPackages) {
                if (packageName.startsWith(annotationPackage)) {
                    return true;
                }
            }
        }
        return false;
    }

}
