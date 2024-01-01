/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.inject.basics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;

/**
 * By adding the {@link io.helidon.inject.service.Injection.Singleton}
 * annotation results in ToolBox becoming a service. Services can be looked up
 * programmatically or declaratively injected via {@link io.helidon.inject.service.Injection.Inject}.
 * <p>
 * Here {@link Weight} is used that is higher than the default, making it more preferred in weighted rankings.
 */
@Injection.Singleton
@Injection.RunLevel(Injection.RunLevel.STARTUP)
@Weight(Weighted.DEFAULT_WEIGHT + 1)
public class ToolBox {

    private final List<Supplier<Tool>> allToolProviders;
    private Tool preferredBigTool;

    // Field injection is supported for non-static, non-private fields (but not recommended)
    // Here we are using it to also showcase for Optional usages.
    @Injection.Inject
    Optional<LittleHammer> optionalLittleHammer;

    /**
     * Here the constructor injects all {@link Tool} provider instances available. {@link java.util.function.Supplier} is used to allow lazy
     * activation of services until {@link java.util.function.Supplier#get()} is called.
     *
     * @param allToolProviders all tool providers
     */
    @Injection.Inject
    ToolBox(List<Supplier<Tool>> allToolProviders) {
        this.allToolProviders = Objects.requireNonNull(allToolProviders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Example of setter based injection.
     *
     * @param preferredBigTool the preferred big tool
     */
    @Injection.Inject
    @SuppressWarnings("unused")
    void setPreferredBigTool(@Big Tool preferredBigTool) {
        this.preferredBigTool = Objects.requireNonNull(preferredBigTool);
    }

    /**
     * This method will be called by Pico after this instance is lazily initialized (because this is the
     * {@link io.helidon.inject.service.Injection.PostConstruct}
     * method).
     */
    @Injection.PostConstruct
    @SuppressWarnings("unused")
    void init() {
        System.out.println("Preferred (highest weighted) 'Big' Tool: " + preferredBigTool);
        System.out.println("Optional 'Little' Hammer: " + optionalLittleHammer);

        printToolBoxContents();
    }

    public void printToolBoxContents() {
        System.out.println("Tools in the virtual ToolBox:");
        for (Supplier<Tool> tool : allToolProviders) {
            System.out.println(" tool: " + tool);
        }
    }

}
