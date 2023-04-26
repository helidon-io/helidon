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

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;

import static io.helidon.pico.processor.GeneralProcessorUtils.rootStackTraceElementOf;

/**
 * Processor for @{@link jakarta.inject.Singleton} type annotations.
 */
public class ServiceAnnotationProcessor extends BaseAnnotationProcessor<Void> {

    private static final Set<String> SUPPORTED_TARGETS = Set.of(
            TypeNames.JAKARTA_SINGLETON,
            TypeNames.PICO_EXTERNAL_CONTRACTS,
            TypeNames.PICO_INTERCEPTED,
            TypeNames.JAVAX_SINGLETON,
            TypeNames.JAKARTA_APPLICATION_SCOPED,
            TypeNames.JAVAX_APPLICATION_SCOPED
    );

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ServiceAnnotationProcessor() {
    }

    @Override
    protected Set<String> annoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    protected Set<String> contraAnnotations() {
        return Set.of(TypeNames.PICO_CONFIGURED_BY);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        try {
            if (!roundEnv.processingOver()
                    && (servicesToProcess().moduleName() == null)) {
                ActiveProcessorUtils utils = new ActiveProcessorUtils(this, processingEnv, roundEnv);
                utils.relayModuleInfoToServicesToProcess(servicesToProcess());
            }

            return super.process(annotations, roundEnv);
        } catch (Throwable t) {
            utils().error(getClass().getSimpleName() + " error during processing; " + t
                          + " @ " + rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("Error detected while processing: " + t
                                             + " @ " + rootStackTraceElementOf(t), t);
        } finally {
            if (roundEnv.processingOver()) {
                servicesToProcess().clearModuleName();
            }
        }
    }

}
