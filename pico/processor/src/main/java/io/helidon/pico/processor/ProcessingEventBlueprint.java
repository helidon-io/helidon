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

import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypedElementInfo;

/**
 * Attributes that can be observed via {@link io.helidon.pico.processor.spi.PicoAnnotationProcessorObserver}.
 */
@Prototype.Blueprint
interface ProcessingEventBlueprint {

    /**
     * Optionally, the active {@link javax.annotation.processing.ProcessingEnvironment} if it is available.
     *
     * @return the processing environment if it is available
     */
    Optional<ProcessingEnvironment> processingEnvironment();

    /**
     * The {@code jakarta.inject.Inject}'able type elements, and possibly any other elements that are found to be of interest for
     * processing. The set of processed elements are subject to change in the future. The implementor is therefore encouraged
     * to not make assumptions about the set of elements that are in this set.
     *
     * @return the set of injectable elements, and any other elements of interest to the pico APT
     */
    Set<TypedElementInfo> elementsOfInterest();

}
