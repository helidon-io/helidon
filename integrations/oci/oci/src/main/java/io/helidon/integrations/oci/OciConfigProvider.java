/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.Service;

// the supplier MUST use fully qualified name, as the type is generated as part of annotation processing
// and the generated contract would be wrong if not
@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class OciConfigProvider implements Supplier<io.helidon.integrations.oci.OciConfig> {
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static volatile OciConfig ociConfig;

    OciConfigProvider() {
    }

    static void config(OciConfig ociConfig) {
        LOCK.writeLock().lock();
        try {
            OciConfigProvider.ociConfig = ociConfig;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    @Override
    public OciConfig get() {
        LOCK.readLock().lock();
        try {
            OciConfig toUse = ociConfig;
            if (toUse != null) {
                return toUse;
            }
        } finally {
            LOCK.readLock().unlock();
        }
        LOCK.writeLock().lock();
        try {
            create();
            return ociConfig;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static void create() {
        Config config = io.helidon.config.Config.create(
                ConfigSources.environmentVariables(),
                ConfigSources.systemProperties(),
                ConfigSources.file("oci-config.yaml").optional(true),
                ConfigSources.classpath("oci-config.yaml").optional(true));

        ociConfig = OciConfig.create(config);
    }
}
