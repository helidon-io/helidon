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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.builder.utils.BuilderUtils;
import io.helidon.builder.utils.Diff;
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
    // vvv : note that these will be removed in the future - it is here to compare the "old way" to the "new way"
    private static final Map<String, List<ActivatorCreatorRequest>> HISTORY_OF_CODE_GEN_ACTIVATORS = new LinkedHashMap<>();
    private static final Map<String, List<CodeGenInterceptorRequest>> HISTORY_OF_CODE_GEN_INTERCEPTORS = new LinkedHashMap<>();
    private static final List<String> HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES = new ArrayList<>();
    private static final List<String> HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES = new ArrayList<>();
    private static final Set<String> NAMES_IN_SIMULATION_MODE = new LinkedHashSet<>();
    private boolean simulationMode;
    // ^^^
    private final String name;
    private final CodeGenFiler filer;
    private final Messager messager;

    ActivatorCreatorHandler(String name,
                            ProcessingEnvironment processingEnv,
                            Messager messager) {
        this.name = Objects.requireNonNull(name);
        this.filer = CodeGenFiler.create(AbstractFilerMessager.createAnnotationBasedFiler(processingEnv, messager));
        this.messager = Objects.requireNonNull(messager);
    }

    @Override
    public ActivatorCreatorResponse createModuleActivators(ActivatorCreatorRequest request) {
        HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES.add(name);
        HISTORY_OF_CODE_GEN_ACTIVATORS.computeIfAbsent(name, (k) -> new ArrayList<>()).add(request);

        messager.debug(name + ": createModuleActivators(" + !simulationMode + "): " + request);
        if (simulationMode) {
            return DefaultActivatorCreatorResponse.builder().configOptions(request.configOptions()).build();
        }
        return ActivatorCreatorProvider.instance().createModuleActivators(request);
    }

    @Override
    public InterceptorCreatorResponse codegenInterceptors(CodeGenInterceptorRequest request) {
        HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES.add(name);
        HISTORY_OF_CODE_GEN_INTERCEPTORS.computeIfAbsent(name, (k) -> new ArrayList<>()).add(request);

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
        NAMES_IN_SIMULATION_MODE.add(name);
    }

    static Runnable reporting() {
        return new DebugReporting();
    }


    static class DebugReporting implements Runnable {
        @Override
        public void run() {
            reportOnActivators();
            reportOnInterceptors();
        }

        private void reportOnActivators() {
            System.out.println("History of code generation activators: " + HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES);

            if (HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES.size() <= 1) {
                return;
            }

            // the right side is always the last one we finished
            int pos = HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES.size() - 1;
            String leftSideName = null;
            String rightSideName = HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES.get(pos);
            List<ActivatorCreatorRequest> list = HISTORY_OF_CODE_GEN_ACTIVATORS.get(rightSideName);
            ActivatorCreatorRequest leftSide = null;
            ActivatorCreatorRequest rightSide = list.get(list.size() - 1);

            while (--pos >= 0 && (leftSideName == null)) {
                leftSideName = HISTORY_OF_CODE_GEN_ACTIVATOR_NAMES.get(pos);
                if (leftSideName.equals(rightSideName)) {
                    leftSide = list.get(list.size() - 2);
                } else {
                    list = HISTORY_OF_CODE_GEN_ACTIVATORS.get(leftSideName);
                    leftSide = list.get(list.size() - 1);
                }
            }

            List<Diff> diffs = BuilderUtils.diff(leftSide, rightSide);
            System.out.println("Activators diff between: "
                                       + leftSideName + " < and > " + rightSideName + ":\n" + diffs + "\n");
        }

        private void reportOnInterceptors() {
            System.out.println("History of code generation interceptors: " + HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES);

            if (HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES.size() <= 1) {
                return;
            }

            // the right side is always the last one we finished
            int pos = HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES.size() - 1;
            String lastNameProcessed = HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES.get(pos);
            List<CodeGenInterceptorRequest> list = HISTORY_OF_CODE_GEN_INTERCEPTORS.get(lastNameProcessed);
            CodeGenInterceptorRequest rightSide = list.get(list.size() - 1);
            CodeGenInterceptorRequest leftSide = null;

            String previousToLastNameProcessed = null;
            while (--pos >= 0 && (previousToLastNameProcessed == null)) {
                previousToLastNameProcessed = HISTORY_OF_CODE_GEN_INTERCEPTORS_NAMES.get(pos);
                if (previousToLastNameProcessed.equals(lastNameProcessed)) {
                    leftSide = list.get(list.size() - 2);
                } else {
                    list = HISTORY_OF_CODE_GEN_INTERCEPTORS.get(previousToLastNameProcessed);
                    leftSide = list.get(list.size() - 1);
                }
            }

            List<Diff> diffs = BuilderUtils.diff(leftSide, rightSide);
            System.out.println("Interceptors diff between: "
                                       + previousToLastNameProcessed + " < and > " + lastNameProcessed + ":\n" + diffs + "\n");
        }

    }

}
