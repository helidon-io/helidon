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

package io.helidon.pico.services;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.Bootstrap;
import io.helidon.pico.PicoServices;
import io.helidon.pico.spi.PicoServicesProvider;

import jakarta.inject.Singleton;

/**
 * The default implementation for {@link io.helidon.pico.spi.PicoServicesProvider}.
 *
 * @see io.helidon.pico.PicoServices#picoServices()
 */
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT)
public class DefaultPicoServicesProvider implements PicoServicesProvider {

    private static final AtomicReference<DefaultPicoServices> INSTANCE = new AtomicReference<>();

    @Override
    public PicoServices services(Bootstrap bootstrap) {
        DefaultPicoServices services = new DefaultPicoServices(bootstrap, true);

        if (!INSTANCE.compareAndSet(null, services)) {
            services = new DefaultPicoServices(bootstrap, false);
        }

        return services;
    }

}
