/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.examples.car.pico;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.ServiceInfo;

import jakarta.inject.Singleton;
import lombok.ToString;

@Singleton
@ToString
public class EngineProvider implements InjectionPointProvider<Engine> {
    @Override
    public Engine get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        return new Engine();
    }
}
