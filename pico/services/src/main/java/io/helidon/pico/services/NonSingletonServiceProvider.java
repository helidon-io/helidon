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

package io.helidon.pico.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.Phase;

/**
 * A provider that represents a non-singleton service.
 *
 * @param <T> the type of the service this provider manages
 */
class NonSingletonServiceProvider<T> extends AbstractServiceProvider<T> {

    private NonSingletonServiceProvider(AbstractServiceProvider<T> parent) {
        picoServices(Optional.of(parent.picoServices()));
        serviceInfo(parent.serviceInfo());
        dependencies(parent.dependencies());
    }

    static <T> T createAndActivate(
            AbstractServiceProvider<T> parent) {
        NonSingletonServiceProvider<T> serviceProvider = new NonSingletonServiceProvider<>(parent);

        LogEntryAndResult logEntryAndResult = serviceProvider.createLogEntryAndResult(Phase.ACTIVE);
        serviceProvider.startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_STARTING);

        serviceProvider.startTransitionCurrentActivationPhase(logEntryAndResult, Phase.GATHERING_DEPENDENCIES);
        Map<String, InjectionPlan> plans = parent.getOrCreateInjectionPlan(false);
        logEntryAndResult.activationResult().injectionPlans(plans);
        Map<String, Object> deps = parent.resolveDependencies(plans);
        logEntryAndResult.activationResult().resolvedDependencies(deps);
        serviceProvider.finishedTransitionCurrentActivationPhase(logEntryAndResult);

        serviceProvider.startTransitionCurrentActivationPhase(logEntryAndResult, Phase.CONSTRUCTING);
        T instance = parent.createServiceProvider(deps);
        serviceProvider.finishedTransitionCurrentActivationPhase(logEntryAndResult);

        if (instance != null) {
            serviceProvider.startTransitionCurrentActivationPhase(logEntryAndResult, Phase.INJECTING);
            List<String> serviceTypeOrdering = Objects.requireNonNull(parent.serviceTypeInjectionOrder());
            LinkedHashSet<String> injections = new LinkedHashSet<>();
            serviceTypeOrdering.forEach((forServiceType) -> {
                parent.doInjectingFields(instance, deps, injections, forServiceType);
                parent.doInjectingMethods(instance, deps, injections, forServiceType);
            });
            serviceProvider.finishedTransitionCurrentActivationPhase(logEntryAndResult);

            serviceProvider.startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_STARTING);

            serviceProvider.startTransitionCurrentActivationPhase(logEntryAndResult, Phase.POST_CONSTRUCTING);
            serviceProvider.doPostConstructing(logEntryAndResult);
            serviceProvider.finishedTransitionCurrentActivationPhase(logEntryAndResult);

            serviceProvider.startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVATION_FINISHING);
        }

        serviceProvider.startAndFinishTransitionCurrentActivationPhase(logEntryAndResult, Phase.ACTIVE);

        return instance;
    }

}
