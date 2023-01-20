/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.features.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

class FeatureHandler {
    private static final String META_FILE = "META-INF/helidon/feature-metadata.properties";
    private final ModuleDescriptor descriptor = new ModuleDescriptor();
    private Messager messager;
    private Filer filer;

    FeatureHandler() {
    }

    void init(ProcessingEnvironment processingEnv) {
        // get compiler utilities
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
    }

    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            return doProcess(annotations, roundEnv);
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process feature metadata annotation processor. "
                    + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> modules = roundEnv.getElementsAnnotatedWithAny(annotations.toArray(new TypeElement[0]));
        for (Element module : modules) {
            processModule(module);
        }

        if (roundEnv.processingOver()) {
            storeMetadata();
        }

        return false;
    }

    private void processModule(Element moduleElement) {
        if (moduleElement.getKind() != ElementKind.MODULE) {
            return;
        }
        ModuleElement module = (ModuleElement) moduleElement;
        List<? extends AnnotationMirror> annotations = module.getAnnotationMirrors();
        String moduleName = module.getQualifiedName().toString();
        if (descriptor.name() != null && !moduleName.equals(descriptor.name())) {
            throw new IllegalStateException("Only one module can be compiled at a single time. Compiling both "
                                                    + moduleName + ", and " + descriptor.name());
        }
        descriptor.moduleName(moduleName);
        descriptor.name(moduleName);

        for (AnnotationMirror annotation : annotations) {
            switch (annotation.getAnnotationType().asElement().toString()) {
            case FeatureProcessor.AOT_CLASS:
                annotation.getElementValues()
                        .forEach((method, value) -> {
                            if (method.getSimpleName().contentEquals("supported")) {
                                descriptor.aotSupported((boolean) value.getValue());
                            } else if (method.getSimpleName().contentEquals("description")) {
                                descriptor.aotDescription((String) value.getValue());
                            }
                        });
                break;
            case FeatureProcessor.PREVIEW_CLASS:
                descriptor.preview(true);
                break;
            case FeatureProcessor.INCUBATING_CLASS:
                descriptor.incubating(true);
                break;
            case FeatureProcessor.DEPRECATED_CLASS:
                descriptor.deprecated(true);
                annotation.getElementValues()
                        .forEach((method, value) -> {
                            if (method.getSimpleName().contentEquals("since")) {
                                descriptor.deprecatedSince((String) value.getValue());
                            }
                        });
                if (descriptor.noDeprecatedSince()) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process feature metadata annotation processor. "
                            + " Module " + moduleName + " has @Deprecated without since. Since must be defined.");
                    throw new IllegalStateException("Deprecated without since in module " + moduleName);
                }
                break;
            case FeatureProcessor.FEATURE_CLASS:
                annotation.getElementValues()
                        .forEach((method, value) -> {
                            if (method.getSimpleName().contentEquals("value")) {
                                descriptor.name((String) value.getValue());
                            } else if (method.getSimpleName().contentEquals("description")) {
                                descriptor.description((String) value.getValue());
                            } else if (method.getSimpleName().contentEquals("path")) {
                                Object pathValue = value.getValue();
                                List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) pathValue;
                                String[] array = new String[values.size()];
                                for (int i = 0; i < array.length; i++) {
                                    array[i] = (String) values.get(i).getValue();
                                }
                                descriptor.path(array);

                            } else if (method.getSimpleName().contentEquals("in")) {
                                descriptor.in(enumList(value.getValue()));
                            } else if (method.getSimpleName().contentEquals("invalidIn")) {
                                descriptor.invalidIn(enumList(value.getValue()));
                            } else if (method.getSimpleName().contentEquals("since")) {
                                descriptor.since((String) value.getValue());
                            }
                        });
                break;
            default:
                break;
            }
        }
    }

    private String[] enumList(Object value) {
        // HelidonFlavor[]
        List theValue = (List) value;
        String[] array = new String[theValue.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = String.valueOf(theValue.get(i));
        }
        return array;
    }

    private void storeMetadata() {
        try (PrintWriter metaWriter = new PrintWriter(filer.createResource(StandardLocation.CLASS_OUTPUT,
                                                                           "",
                                                                           META_FILE)
                                                              .openWriter())) {

            descriptor.write(metaWriter);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write feature metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
