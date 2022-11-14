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

package io.helidon.pico.testsupport;

import java.util.Objects;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.spi.impl.DefaultPicoServices;

class InternalPicoServices extends DefaultPicoServices {
    InternalDefaultServices services;

    InternalPicoServices(PicoServicesConfig config, InternalDefaultServices services) {
        super(config, services, null, false);
        this.services = services;
    }

    @Override
    public void reset() {
        super.reset();
        if (Objects.nonNull(services)) {
            setServices(services);
        }
    }

}
