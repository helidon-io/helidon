/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.integrations.micrometer.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.integrations.micrometer.MeterRegistryFactory;
import io.helidon.integrations.micrometer.MicrometerSupport;

import io.micrometer.core.instrument.MeterRegistry;

@ApplicationScoped
class MeterRegistryProducer {

    static final String CONFIG_KEY = "micrometer";

    /*
     * Also maintains a lazy refc to the single {@code MicrometerSupport} instance.
     */
    private static final LazyValue<MicrometerSupport> MICROMETER_SUPPORT_FACTORY =
            LazyValue.create(MeterRegistryProducer::createMicrometerSupport);

    private MeterRegistryProducer() {
    }

    @Produces
    static MeterRegistry getMeterRegistry() {
        return getMicrometerSupport()
                .registry();
    }

    static MicrometerSupport getMicrometerSupport() {
        return MICROMETER_SUPPORT_FACTORY.get();
    }

    static void clear() {
        getMeterRegistry().clear();
    }

    private static MicrometerSupport createMicrometerSupport() {
        Config micrometerConfig = Config.create().get(CONFIG_KEY);
        MeterRegistryFactory factory = MeterRegistryFactory.getInstance(
                MeterRegistryFactory.builder()
                    .config(micrometerConfig));
        MicrometerSupport result = MicrometerSupport.builder()
                .config(micrometerConfig)
                .meterRegistryFactorySupplier(factory)
                .build();

        return result;
    }
}
