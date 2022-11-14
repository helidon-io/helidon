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

import java.util.Optional;

import io.helidon.pico.PicoServices;
import io.helidon.pico.spi.ext.Resetable;
import io.helidon.pico.spi.impl.DefaultServices;

/**
 * A version of {@link io.helidon.pico.PicoServices} that is conducive to unit testing.
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public class TestablePicoServices implements PicoServices, Resetable {

    final TestableServices services;
    final InternalPicoServices picoServices;

    /**
     * Default constructor.
     */
    public TestablePicoServices() {
        this(new TestableServices());
    }

    /**
     * Constructor taking a configuration.
     *
     * @param initServices the initial services registry
     */
    public TestablePicoServices(TestableServices initServices) {
        picoServices = new InternalPicoServices(initServices.config, initServices.services);
        services = initServices;
    }

    @Override
    public TestableServices services() {
        DefaultServices internalServices = picoServices.services();
        assert (internalServices == services.services);
        return services;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<TestablePicoServicesConfig> config() {
        return (Optional<TestablePicoServicesConfig>) picoServices.config();
    }

    public boolean isGlobal() {
        return picoServices.isGlobal();
    }

    public boolean isDynamic() {
        return picoServices.isDynamic();
    }

    @Override
    public void reset() {
        picoServices.reset();
    }

}
