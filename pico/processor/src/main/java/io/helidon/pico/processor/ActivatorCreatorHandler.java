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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.AbstractFilerMessager;
import io.helidon.pico.tools.ActivatorCreatorProvider;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CodeGenInterceptorRequest;
import io.helidon.pico.tools.DefaultActivatorCreatorResponse;
import io.helidon.pico.tools.DefaultInterceptorCreatorResponse;
import io.helidon.pico.tools.InterceptorCreatorResponse;
import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.spi.ActivatorCreator;

class ActivatorCreatorHandler implements ActivatorCreator {
    // note that this will be removed in the future - it is here to compare the "old way" to the "new way"
    private static final Map<String, List<ActivatorCreatorRequest>> historyOfCreateModuleActivators = new ConcurrentHashMap<>();
    private static final Map<String, List<CodeGenInterceptorRequest>> historyOfCodegenInterceptors = new ConcurrentHashMap<>();
    private final String name;
    private final CodeGenFiler filer;
    private final Messager messager;
    private boolean simulationMode;

    ActivatorCreatorHandler(String name,
                            ProcessingEnvironment processingEnv,
                            Messager messager) {
        this.name = name;
        this.filer = CodeGenFiler.create(
                AbstractFilerMessager.createAnnotationBasedFiler(processingEnv, messager));
        this.messager = messager;
    }

    @Override
    public ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest request) {
        historyOfCreateModuleActivators.computeIfAbsent(name, (k) -> new ArrayList<>()).add(request);

        messager.debug(name + ": createModuleActivators(" + !simulationMode + "): " + request);
        if (simulationMode) {
            return DefaultActivatorCreatorResponse.builder().configOptions(request.configOptions()).build();
        }
        return ActivatorCreatorProvider.instance().createModuleActivators(request);
    }

    @Override
    public InterceptorCreatorResponse codegenInterceptors(CodeGenInterceptorRequest request) {
        historyOfCodegenInterceptors.computeIfAbsent(name, (k) -> new ArrayList<>()).add(request);

        messager.debug(name + ": codegenInterceptors(" + !simulationMode + "): " + request);
        if (simulationMode) {
            return DefaultInterceptorCreatorResponse.builder().build();
        }
        return ActivatorCreatorProvider.instance().codegenInterceptors(request);
    }

    @Override
    public TypeName toActivatorImplTypeName(TypeName activatorTypeName) {
        messager.debug(name + ": toActivatorImplTypeName(" + !simulationMode + "): " + activatorTypeName);
        return ActivatorCreatorProvider.instance()
                .toActivatorImplTypeName(activatorTypeName);
    }

    CodeGenFiler filer() {
        return filer;
    }

    void activateSimulationMode() {
        this.simulationMode = true;
    }

}
