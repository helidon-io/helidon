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

package io.helidon.pico.processor;

import java.util.Objects;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.AbstractFilerMessager;
import io.helidon.pico.tools.ActivatorCreatorProvider;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CodeGenInterceptorRequest;
import io.helidon.pico.tools.InterceptorCreatorResponse;
import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.spi.ActivatorCreator;

/**
 * Provides wrapping of the {@link ActivatorCreator}}.
 */
class CreatorHandler implements ActivatorCreator {
    private final String name;
    private final CodeGenFiler filer;
    private final Messager messager;

    CreatorHandler(String name,
                   ProcessingEnvironment processingEnv,
                   Messager messager) {
        this.name = Objects.requireNonNull(name);
        this.filer = CodeGenFiler.create(AbstractFilerMessager.createAnnotationBasedFiler(processingEnv, messager));
        this.messager = Objects.requireNonNull(messager);
    }

    // note: overrides ActivatorCreator
    @Override
    public ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest request) {
        messager.debug(name + ": createModuleActivators: " + request);
        return ActivatorCreatorProvider.instance().createModuleActivators(request);
    }

    // note: overrides ActivatorCreator
    @Override
    public InterceptorCreatorResponse codegenInterceptors(CodeGenInterceptorRequest request) {
        messager.debug(name + ": codegenInterceptors(): " + request);
        return ActivatorCreatorProvider.instance().codegenInterceptors(request);
    }

    // note: overrides ActivatorCreator
    @Override
    public TypeName toActivatorImplTypeName(TypeName activatorTypeName) {
        return ActivatorCreatorProvider.instance()
                .toActivatorImplTypeName(activatorTypeName);
    }

    CodeGenFiler filer() {
        return filer;
    }

}
