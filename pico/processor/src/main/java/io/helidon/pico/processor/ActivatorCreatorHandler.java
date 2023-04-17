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
    private static final Map<String, List<ActivatorCreatorRequest>> historyOfCodeGenActivators = new LinkedHashMap<>();
    private static final Map<String, List<CodeGenInterceptorRequest>> historyOfCodeGenInterceptors = new LinkedHashMap<>();
    private static final List<String> historyOfCodeGenActivatorNames = new ArrayList<>();
    private static final List<String> historyOfCodeGenInterceptorsNames = new ArrayList<>();
    private static final Set<String> namesInSimulationMode = new LinkedHashSet<>();
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
        historyOfCodeGenActivatorNames.add(name);
        historyOfCodeGenActivators.computeIfAbsent(name, (k) -> new ArrayList<>()).add(request);

        messager.debug(name + ": createModuleActivators(" + !simulationMode + "): " + request);
        if (simulationMode) {
            return DefaultActivatorCreatorResponse.builder().configOptions(request.configOptions()).build();
        }
        return ActivatorCreatorProvider.instance().createModuleActivators(request);
    }

    @Override
    public InterceptorCreatorResponse codegenInterceptors(CodeGenInterceptorRequest request) {
        historyOfCodeGenInterceptorsNames.add(name);
        historyOfCodeGenInterceptors.computeIfAbsent(name, (k) -> new ArrayList<>()).add(request);

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
        namesInSimulationMode.add(name);
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
            System.out.println("History of code generation activators: " + historyOfCodeGenActivatorNames);

            if (historyOfCodeGenActivatorNames.size() <= 1) {
                return;
            }

            // the right side is always the last one we finished
            int pos = historyOfCodeGenActivatorNames.size() - 1;
            String lastNameProcessed = historyOfCodeGenActivatorNames.get(pos);
            List<ActivatorCreatorRequest> list = historyOfCodeGenActivators.get(lastNameProcessed);
            ActivatorCreatorRequest rightSide = list.get(list.size() - 1);
            ActivatorCreatorRequest leftSide = null;

            String previousToLastNameProcessed = null;
            while (--pos >= 0 && (previousToLastNameProcessed == null)) {
                previousToLastNameProcessed = historyOfCodeGenActivatorNames.get(pos);
                if (previousToLastNameProcessed.equals(lastNameProcessed)) {
                    leftSide = list.get(list.size() - 2);
                } else {
                    list = historyOfCodeGenActivators.get(previousToLastNameProcessed);
                    leftSide = list.get(list.size() - 1);
                }
            }

            List<Diff> diffs = BuilderUtils.diff(leftSide, rightSide);
            System.out.println("Activators Diff between: "
                                       + previousToLastNameProcessed + " < and > " + lastNameProcessed + ":\n" + diffs + "\n");
        }

        private void reportOnInterceptors() {
            System.out.println("History of code generation interceptors: " + historyOfCodeGenInterceptorsNames);

            if (historyOfCodeGenInterceptorsNames.size() <= 1) {
                return;
            }

            // the right side is always the last one we finished
            int pos = historyOfCodeGenInterceptorsNames.size() - 1;
            String lastNameProcessed = historyOfCodeGenInterceptorsNames.get(pos);
            List<CodeGenInterceptorRequest> list = historyOfCodeGenInterceptors.get(lastNameProcessed);
            CodeGenInterceptorRequest rightSide = list.get(list.size() - 1);
            CodeGenInterceptorRequest leftSide = null;

            String previousToLastNameProcessed = null;
            while (--pos >= 0 && (previousToLastNameProcessed == null)) {
                previousToLastNameProcessed = historyOfCodeGenInterceptorsNames.get(pos);
                if (previousToLastNameProcessed.equals(lastNameProcessed)) {
                    leftSide = list.get(list.size() - 2);
                } else {
                    list = historyOfCodeGenInterceptors.get(previousToLastNameProcessed);
                    leftSide = list.get(list.size() - 1);
                }
            }

            List<Diff> diffs = BuilderUtils.diff(leftSide, rightSide);
            System.out.println("Interceptors Diff between: "
                                       + previousToLastNameProcessed + " < and > " + lastNameProcessed + ":\n" + diffs + "\n");
        }

    }

}
