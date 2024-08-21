/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import io.helidon.common.processor.ElementInfoPredicates;
import io.helidon.common.processor.TypeInfoFactory;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;

import static io.helidon.config.metadata.processor.UsedTypes.BLUEPRINT;
import static io.helidon.config.metadata.processor.UsedTypes.CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.META_CONFIGURED;

/*
 * This class is separated so javac correctly reports possible errors.
 */
class  ConfigMetadataHandler {
    /*
     * Configuration metadata file location.
     */
    private static final String META_FILE = "META-INF/helidon/config-metadata.json";

    // Newly created options as part of this processor run - these will be stored to META_FILE
    // map of type name to its configured type
    private final Map<TypeName, ConfiguredType> newOptions = new HashMap<>();
    // map of module name to list of classes that belong to it
    private final Map<String, List<TypeName>> moduleTypes = new HashMap<>();
    private final Set<Element> classesToHandle = new LinkedHashSet<>();
    /*
     * Compiler utilities for annotation processing
     */
    private Elements elementUtils;
    private Messager messager;
    private TypeElement configuredElement;
    private Filer filer;
    private TypeElement metaConfiguredElement;
    private ProcessingEnvironment processingEnv;

    /**
     * Public constructor required for service loader.
     */
    ConfigMetadataHandler() {
    }

    synchronized void init(ProcessingEnvironment processingEnv) {
        // get compiler utilities
        this.processingEnv = processingEnv;
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();

        // get the types
        this.configuredElement = elementUtils.getTypeElement(CONFIGURED.fqName());
        this.metaConfiguredElement = elementUtils.getTypeElement(META_CONFIGURED.fqName());
    }

    boolean process(RoundEnvironment roundEnv) {
        try {
            return doProcess(roundEnv);
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process config metadata annotation processor. "
                    + toMessage(e));
            e.printStackTrace();
            return false;
        }
    }

    // add types we handle to our set, and when the processing is over, generate the metadata file
    private boolean doProcess(RoundEnvironment roundEnv) {
        // we need to collect all types for processing, but we may not have the annotations on classpath
        if (configuredElement != null) {
            classesToHandle.addAll(roundEnv.getElementsAnnotatedWith(configuredElement));
        }
        if (metaConfiguredElement != null) {
            classesToHandle.addAll(roundEnv.getElementsAnnotatedWith(metaConfiguredElement));
        }
        if (roundEnv.processingOver()) {
            for (Element aClass : classesToHandle) {
                processClass(aClass);
            }

            storeMetadata();
        }

        return false;
    }

    /*
     * This is a class annotated with @Configured or @Prototype.Configured
     */
    private void processClass(Element aClass) {
        if (aClass instanceof TypeElement typeElement) {
            Optional<TypeInfo> typeInfo = TypeInfoFactory.create(processingEnv,
                                                                 typeElement,
                                                                 ElementInfoPredicates::isMethod);
            if (typeInfo.isEmpty()) {
                // this type cannot be analyzed
                return;
            }

            TypeHandler handler;

            TypeInfo configuredType = typeInfo.get();
            if (configuredType.hasAnnotation(META_CONFIGURED)) {
                if (configuredType.hasAnnotation(BLUEPRINT)) {
                    // old style - if using config meta annotation on class, expecting config meta annotations on options
                    handler = new TypeHandlerMetaApiBlueprint(processingEnv, configuredType);
                } else {
                    // only new style of annotations (we expect that if class annotation is changed to Prototype.Configured)
                    // all other annotations are migrated as well
                    handler = new TypeHandlerMetaApi(processingEnv, configuredType);
                }
            } else if (configuredType.hasAnnotation(CONFIGURED)) {
                if (configuredType.hasAnnotation(BLUEPRINT)) {
                    // this is a blueprint (annotation @Prototype.Blueprint)
                    handler = new TypeHandlerBuilderApi(processingEnv, configuredType);
                } else {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                                          "Requested to process type: " + aClass + ", annotated with @"
                                                  + CONFIGURED.className()
                                                  + " does not have @" + BLUEPRINT.className() + " annotation",
                                          aClass);
                    return;
                }
            } else {
                // this may be a type that extends another type annotated with @Configured, @C
                messager.printMessage(Diagnostic.Kind.ERROR,
                                      "Requested to process type: " + aClass + ", but it does not have required "
                                              + "annotation",
                                      aClass);
                return;
            }

            TypeHandlerResult result = handler.handle();

            TypeName targetType = result.targetType();
            ConfiguredType configured = result.configuredType();

            newOptions.put(targetType, configured);
            moduleTypes.computeIfAbsent(result.moduleName(), it -> new ArrayList<>()).add(targetType);
        }
    }

    private void storeMetadata() {
        if (moduleTypes.isEmpty()) {
            return;
        }
        try (PrintWriter metaWriter = new PrintWriter(filer.createResource(StandardLocation.CLASS_OUTPUT,
                                                                           "",
                                                                           META_FILE)
                                                              .openWriter())) {

            /*
             The root of the json file is an array that contains module entries
             This is to allow merging of files - such as when we would want to create on-the-fly
             JSON for a project with only its dependencies.
             */
            List<Hson.Struct> moduleArray = new ArrayList<>();

            for (var module : moduleTypes.entrySet()) {
                String moduleName = module.getKey();
                var types = module.getValue();
                List<Hson.Struct>  typeArray = new ArrayList<>();
                types.forEach(it -> newOptions.get(it).write(typeArray));
                moduleArray.add(Hson.Struct.builder()
                                        .set("module", moduleName)
                                        .setStructs("types", typeArray)
                                        .build());
            }

            Hson.Array.create(moduleArray).write(metaWriter);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write configuration metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String toMessage(Exception e) {
        return e.getClass().getName() + ": " + e.getMessage();
    }
}
