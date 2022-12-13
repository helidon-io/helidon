/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Annotation processor.
 */
public class FeatureProcessor extends AbstractProcessor {
    static final String ANNOTATIONS_PACKAGE = "io.helidon.common.features.api.";
    static final String AOT_CLASS = ANNOTATIONS_PACKAGE + "Aot";
    static final String INCUBATING_CLASS = ANNOTATIONS_PACKAGE + "Incubating";
    static final String PREVIEW_CLASS = ANNOTATIONS_PACKAGE + "Preview";
    static final String FEATURE_CLASS = ANNOTATIONS_PACKAGE + "Feature";
    static final String DEPRECATED_CLASS = "java.lang.Deprecated";

    private FeatureHandler handler;

    /**
     * Public constructor required for service loader.
     */
    public FeatureProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(AOT_CLASS,
                      PREVIEW_CLASS,
                      FEATURE_CLASS,
                      INCUBATING_CLASS,
                      DEPRECATED_CLASS);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        handler = new FeatureHandler();
        handler.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return handler.process(annotations, roundEnv);
    }
}
