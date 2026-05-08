/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.codegen.api.stability.enforcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * Annotation processor enforcing stability annotation on public types in Helidon production modules.
 */
public class ApiStabilityEnforcerProcessor extends AbstractProcessor {
    private Messager messager;
    private TypeElement previewAnnotation;
    private TypeElement incubatingAnnotation;
    private TypeElement internalAnnotation;
    private TypeElement stableAnnotation;

    /**
     * Required by {@link java.util.ServiceLoader}.
     */
    public ApiStabilityEnforcerProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        var elements = processingEnv.getElementUtils();

        var previewAnnotation = elements.getTypeElement("io.helidon.common.Api.Preview");
        var incubatingAnnotation = elements.getTypeElement("io.helidon.common.Api.Incubating");
        var internalAnnotation = elements.getTypeElement("io.helidon.common.Api.Internal");
        var stableAnnotation = elements.getTypeElement("io.helidon.common.Api.Stable");

        if (previewAnnotation == null
                || incubatingAnnotation == null
                || internalAnnotation == null
                || stableAnnotation == null) {
            this.previewAnnotation = null;
            this.incubatingAnnotation = null;
            this.internalAnnotation = null;
            this.stableAnnotation = null;
            this.messager = processingEnv.getMessager();
            return;
        }

        this.previewAnnotation = previewAnnotation;
        this.incubatingAnnotation = incubatingAnnotation;
        this.internalAnnotation = internalAnnotation;
        this.stableAnnotation = stableAnnotation;

        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (stableAnnotation == null) {
            return false;
        }

        Set<? extends Element> rootElements = roundEnv.getRootElements();
        for (Element rootElement : rootElements) {
            if (rootElement instanceof TypeElement type) {
                if (type.getModifiers().contains(Modifier.PUBLIC)) {
                    process(type);
                }
            }
        }
        return false;
    }

    private void process(TypeElement type) {
        List<Stability> discovered = declaredStabilities(type);

        if (discovered.isEmpty()) {
            messager.printError("Public API " + type + " is missing stability annotation (@Api.*)", type);
            return;
        }

        if (discovered.size() > 1) {
            messager.printError("Public API " + type + " has more than one stability annotation (@Api.*)", type);
            return;
        }

        validateNestedElements(type, discovered.getFirst(), type);
    }

    private void validateNestedElements(Element enclosingApi, Stability enclosingStability, Element enclosingElement) {
        for (Element enclosed : enclosingElement.getEnclosedElements()) {
            List<Stability> declaredStabilities = declaredStabilities(enclosed);
            Stability highestDeclared = highest(declaredStabilities);
            Element effectiveEnclosingApi = enclosingApi;
            Stability effectiveEnclosingStability = enclosingStability;

            if (highestDeclared != null) {
                if (highestDeclared.higherThan(enclosingStability)) {
                    messager.printError("Element " + enclosed
                                                + " must not declare " + highestDeclared.annotationName()
                                                + " because enclosing API " + enclosingApi
                                                + " is " + enclosingStability.annotationName(),
                                        enclosed);
                } else {
                    effectiveEnclosingApi = enclosed;
                    effectiveEnclosingStability = highestDeclared;
                }
            }

            validateNestedElements(effectiveEnclosingApi, effectiveEnclosingStability, enclosed);
        }
    }

    private List<Stability> declaredStabilities(Element element) {
        List<Stability> discovered = new ArrayList<>();
        for (var am : element.getAnnotationMirrors()) {
            Stability stability = stability(am.getAnnotationType().asElement());
            if (stability != null) {
                discovered.add(stability);
            }
        }
        return discovered;
    }

    private Stability stability(Element annotation) {
        if (stableAnnotation.equals(annotation)) {
            return Stability.STABLE;
        }
        if (previewAnnotation.equals(annotation)) {
            return Stability.PREVIEW;
        }
        if (incubatingAnnotation.equals(annotation)) {
            return Stability.INCUBATING;
        }
        if (internalAnnotation.equals(annotation)) {
            return Stability.INTERNAL;
        }
        return null;
    }

    private Stability highest(List<Stability> stabilities) {
        Stability highest = null;
        for (var stability : stabilities) {
            if (highest == null || stability.higherThan(highest)) {
                highest = stability;
            }
        }
        return highest;
    }

    private enum Stability {
        INTERNAL(0, "@Api.Internal"),
        INCUBATING(1, "@Api.Incubating"),
        PREVIEW(2, "@Api.Preview"),
        STABLE(3, "@Api.Stable");

        private final int rank;
        private final String annotationName;

        Stability(int rank, String annotationName) {
            this.rank = rank;
            this.annotationName = annotationName;
        }

        private boolean higherThan(Stability other) {
            return rank > other.rank;
        }

        private String annotationName() {
            return annotationName;
        }
    }
}
