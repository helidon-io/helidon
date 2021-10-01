/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Annotation processor.
 */
public class ConfigMetadataProcessor extends AbstractProcessor {
    static final String ANNOTATIONS_PACKAGE = "io.helidon.config.metadata.";
    static final String CONFIGURED_CLASS = ANNOTATIONS_PACKAGE + "Configured";
    static final String CONFIGURED_OPTION_CLASS = ANNOTATIONS_PACKAGE + "ConfiguredOption";
    static final String CONFIGURED_OPTIONS_CLASS = ANNOTATIONS_PACKAGE + "ConfiguredOptions";

    private ConfigMetadataHandler handler;

    /**
     * Public constructor required for service loader.
     */
    public ConfigMetadataProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CONFIGURED_CLASS,
                      CONFIGURED_OPTION_CLASS,
                      CONFIGURED_OPTIONS_CLASS);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        handler = new ConfigMetadataHandler();
        handler.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return handler.process(annotations, roundEnv);
    }
}
