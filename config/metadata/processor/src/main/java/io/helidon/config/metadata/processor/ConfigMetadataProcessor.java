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

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import static io.helidon.config.metadata.processor.UsedTypes.CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.META_CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.META_OPTION;
import static io.helidon.config.metadata.processor.UsedTypes.META_OPTIONS;

/**
 * Annotation processor.
 *
 * @deprecated use {@code helidon-config-metadata-codegen} instead
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public class ConfigMetadataProcessor extends AbstractProcessor {
    private ConfigMetadataHandler handler;

    /**
     * Public constructor required for service loader.
     */
    public ConfigMetadataProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(META_CONFIGURED.fqName(),
                      META_OPTION.fqName(),
                      META_OPTIONS.fqName(),
                      CONFIGURED.fqName());
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

        processingEnv.getMessager()
                .printWarning("Module helidon-config-metadata-processor is deprecated, "
                                       + "please use io.helidon.config.metadata:helidon-config-metadata-codegen");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return handler.process(roundEnv);
    }
}
