/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.testsubjects;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationResult;
import io.helidon.pico.Injector;
import io.helidon.pico.Metrics;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.Services;

abstract class AbstractPicoServices implements PicoServices {

    @Override
    public PicoServicesConfig config() {
        return null;
    }

    @Override
    public Services services() {
        return null;
    }

    @Override
    public Optional<ServiceBinder> createServiceBinder(
            Module module) {
        return Optional.empty();
    }

    @Override
    public Optional<Injector> injector() {
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, ActivationResult>> shutdown() {
        return Optional.empty();
    }

    @Override
    public Optional<ActivationLog> activationLog() {
        return Optional.empty();
    }

    @Override
    public Optional<Metrics> metrics() {
        return Optional.empty();
    }

    @Override
    public Optional<Set<ServiceInfoCriteria>> lookups() {
        return Optional.empty();
    }

}
