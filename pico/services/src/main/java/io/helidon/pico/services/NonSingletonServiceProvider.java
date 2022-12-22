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

import io.helidon.pico.ActivationLog;
import io.helidon.pico.DefaultActivationLogEntry;
import io.helidon.pico.DefaultActivationResult;
import io.helidon.pico.Event;
import io.helidon.pico.Phase;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;

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

        PicoServices picoServices = serviceProvider.picoServices();
        PicoServicesConfig cfg = picoServices.config();
        Optional<ActivationLog> log = picoServices.activationLog();

        Phase ultimateTargetPhase = Phase.ACTIVE;
        if (cfg.activationLogs()) {
            DefaultActivationLogEntry.Builder entry = serviceProvider
                    .toLogEntry(Event.STARTING, ultimateTargetPhase);
            entry.finishedActivationPhase(Phase.ACTIVATION_STARTING);
            log.ifPresent(activationLog -> activationLog.record(entry));
        }

        DefaultActivationResult.Builder res =
                serviceProvider.createResultPlaceholder(ultimateTargetPhase);
        if (cfg.activationLogs()) {
            serviceProvider.recordActivationEvent(Event.STARTING,
                    serviceProvider.currentActivationPhase(), res);
            serviceProvider.recordActivationEvent(Event.STARTING, Phase.GATHERING_DEPENDENCIES, res);
        }

        Map<String, InjectionPlan> plans = parent.getOrCreateInjectionPlan(false);
        Map<String, Object> deps = parent.resolveDependencies(plans);
        if (cfg.activationLogs()) {
            serviceProvider.recordActivationEvent(Event.FINISHED, Phase.GATHERING_DEPENDENCIES, res);
            serviceProvider.recordActivationEvent(Event.STARTING, Phase.CONSTRUCTING, res);
        }
        T instance = parent.createServiceProvider(deps);

        if (cfg.activationLogs()) {
            serviceProvider.recordActivationEvent(Event.FINISHED, Phase.CONSTRUCTING, res);
        }

        if (instance != null) {
            if (cfg.activationLogs()) {
                serviceProvider.recordActivationEvent(Event.STARTING, Phase.INJECTING, res);
            }

            List<String> serviceTypeOrdering = Objects.requireNonNull(parent.serviceTypeInjectionOrder());
            LinkedHashSet<String> injections = new LinkedHashSet<>();
            serviceTypeOrdering.forEach((forServiceType) -> {
                parent.doInjectingFields(instance, deps, injections, forServiceType);
                parent.doInjectingMethods(instance, deps, injections, forServiceType);
            });

            if (cfg.activationLogs()) {
                serviceProvider.recordActivationEvent(Event.FINISHED, Phase.INJECTING, res);
            }
        }

        if (cfg.activationLogs()) {
            serviceProvider.recordActivationEvent(Event.FINISHED, Phase.POST_CONSTRUCTING, res);
        }

        serviceProvider.doPostConstructing(res, Phase.POST_CONSTRUCTING);

        if (cfg.activationLogs()) {
            serviceProvider.recordActivationEvent(Event.FINISHED, Phase.POST_CONSTRUCTING, res);
            serviceProvider.recordActivationEvent(Event.FINISHED, Phase.ACTIVE, res);
        }

        return instance;
    }

}
