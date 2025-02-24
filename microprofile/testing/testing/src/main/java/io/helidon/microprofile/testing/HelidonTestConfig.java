/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.util.Map;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

/**
 * Helidon test configuration.
 * <p>
 * A config delegate that serves one of three config instances:
 * <ul>
 *     <li>bootstrapping configuration</li>
 *     <li>synthetic configuration</li>
 *     <li>original configuration</li>
 * </ul>
 * <p>
 * This mechanism removes the need to define bootstrapping configuration when using {@link Configuration#useExisting()}.
 * <p>
 * This mechanism also provides a way to update the synthetic configuration at a later stage using CDI.
 */
class HelidonTestConfig extends HelidonTestConfigDelegate {

    private final HelidonTestConfigSynthetic syntheticConfig;
    private final Config originalConfig;
    private volatile Config delegate;

    HelidonTestConfig(HelidonTestInfo<?> testInfo) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();

        originalConfig = resolver.getConfig(cl);

        // release original config
        resolver.releaseConfig(originalConfig);

        // start with bootstrap config
        delegate = resolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        "mp.initializer.no-warn", "true")))
                .build();

        syntheticConfig = new HelidonTestConfigSynthetic(testInfo, this::refresh);

        // register delegate
        resolver.registerConfig(this, cl);
    }

    @Override
    Config delegate() {
        return delegate;
    }

    /**
     * Get the synthetic config.
     *
     * @return synthetic config
     */
    HelidonTestConfigSynthetic synthetic() {
        return syntheticConfig;
    }

    /**
     * Resolve the delegate.
     */
    void resolve() {
        if (syntheticConfig.useExisting()) {
            delegate = originalConfig;
        } else {
            delegate = syntheticConfig;
        }
    }
}
