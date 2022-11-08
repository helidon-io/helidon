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

package io.helidon.pico;

import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

/**
 * The holder for the active {@link PicoServices} instance.
 */
class PicoServicesHolder {
    private static final LazyValue<Optional<PicoServices>> INSTANCE = LazyValue.create(PicoServicesHolder::load);

    private PicoServicesHolder() {
    }

    static Optional<PicoServices> picoServices() {
        return INSTANCE.get();
    }

    private static Optional<PicoServices> load() {
        return HelidonServiceLoader.create(ServiceLoader.load(PicoServices.class))
                .asList()
                .stream()
                .findFirst();
    }
}
