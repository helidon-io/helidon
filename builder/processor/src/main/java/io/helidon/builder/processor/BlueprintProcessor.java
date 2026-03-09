/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.processor;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Annotation processor for prototype blueprints.
 * Generates prototype implementation from the blueprint.
 *
 * @deprecated replaced with helidon-builder-codegen in
 *         combination with helidon-codegen-apt
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public class BlueprintProcessor extends AbstractProcessor {

    /**
     * Public constructor required for service loader.
     */
    public BlueprintProcessor() {
        super();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                "io.helidon.builder.api.Prototype.Blueprint",
                "io.helidon.builder.api.RuntimeType.PrototypedBy",
                "io.helidon.common.Generated");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        processingEnv.getMessager()
                .printWarning("Module helidon-builder-processor is deprecated, "
                              + "use io.helidon.builder:helidon-builder-codegen instead");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
