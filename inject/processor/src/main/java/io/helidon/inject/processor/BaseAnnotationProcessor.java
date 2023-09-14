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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.common.types.TypeName;
import io.helidon.inject.tools.Options;

/**
 * Abstract base for all Helidon annotation processing.
 */
abstract class BaseAnnotationProcessor extends AbstractProcessor {
    private static final AtomicBoolean LOGGED_WARNING = new AtomicBoolean();
    private final System.Logger logger = System.getLogger(getClass().getName());

    private ActiveProcessorUtils utils;

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    protected BaseAnnotationProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.utils = new ActiveProcessorUtils(this, processingEnv);
        super.init(processingEnv);

        if (!Options.isOptionEnabled(Options.TAG_ACCEPT_PREVIEW)
                && LOGGED_WARNING.compareAndSet(false, true)) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.WARNING,
                                  "Helidon Inject is a preview feature, and may introduce backward incompatible changes. "
                                          + "It is a production quality code, but we may need to do fine tuning of APIs and SPIs."
                                          + " This warning can be disabled by compiler argument -Ainject.acceptPreview=true");
        }
    }

    @Override
    public abstract Set<String> getSupportedAnnotationTypes();

    System.Logger logger() {
        return logger;
    }

    ActiveProcessorUtils utils() {
        return Objects.requireNonNull(utils);
    }

    Optional<TypeElement> toTypeElement(TypeName typeName) {
        return Optional.ofNullable(processingEnv.getElementUtils().getTypeElement(typeName.resolvedName()));
    }

}
