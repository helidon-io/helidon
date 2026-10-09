/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.tls;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.tls.spi.TlsManagerProvider;
import io.helidon.config.Config;

public class RequiredConfigTlsManagerProvider implements TlsManagerProvider {
    static final String TYPE = "required-config";
    static final AtomicInteger CREATE_COUNT = new AtomicInteger();
    static final AtomicInteger INIT_COUNT = new AtomicInteger();

    @Override
    public String configKey() {
        return TYPE;
    }

    @Override
    public TlsManager create(Config config, String name) {
        CREATE_COUNT.incrementAndGet();
        String requiredValue = config.get("required-value").asString().get();
        return new RequiredConfigTlsManager(name, requiredValue);
    }

    static final class RequiredConfigTlsManager extends ConfiguredTlsManager {
        private final String requiredValue;

        private RequiredConfigTlsManager(String name, String requiredValue) {
            super(name, TYPE);
            this.requiredValue = requiredValue;
        }

        String requiredValue() {
            return requiredValue;
        }

        @Override
        public void init(TlsConfig tls) {
            INIT_COUNT.incrementAndGet();
            super.init(tls);
        }
    }
}
