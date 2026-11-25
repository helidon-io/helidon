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
package io.helidon.telemetry.logging;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.Api;
import io.helidon.common.Errors;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.logging.common.LogConfig;
import io.helidon.telemetry.logging.spi.LogBridgeProvider;

/**
 * Discovers an explicitly installed OpenTelemetry logging adapter for Helidon's selected logging backend.
 * Discovery neither constructs nor configures an OpenTelemetry instance and never attaches logging hooks.
 */
@Api.Preview
public final class LogBridges {
    private LogBridges() {
    }

    /**
     * Find the optional adapter visible to a caller's class loader. At most one adapter may be present,
     * and it must support the logging provider already selected by {@link LogConfig}. No adapter is
     * a supported choice: the caller may still use the OpenTelemetry APIs directly.
     * <p>
     * Retain a bridge returned by the provider before calling {@link LogBridge#start()}, so failed
     * startup can be cleaned up. Stop publication admission, detach the bridge, drain admitted work,
     * then close the bridge before closing any caller-owned OpenTelemetry instance. This facility
     * does not select between multiple application SDKs sharing one process-wide logging context;
     * callers must coordinate registrations to avoid duplicate capture or cross-application routing.
     *
     * @param loader class loader used for adapter discovery
     * @return matching provider, or empty if no adapter is installed
     * @throws io.helidon.common.Errors.ErrorMessagesException if adapter configuration is invalid
     */
    public static Optional<LogBridgeProvider> findProvider(ClassLoader loader) {
        Objects.requireNonNull(loader, "loader");
        var providers = HelidonServiceLoader.create(ServiceLoader.load(LogBridgeProvider.class, loader)).asList();
        if (providers.isEmpty()) {
            return Optional.empty();
        }
        var logging = LogConfig.provider();
        var errors = Errors.collector();
        if (providers.size() > 1) {
            errors.fatal("Logging adapters: at most one OpenTelemetry logging adapter may be installed");
        }
        for (var provider : providers) {
            if (!provider.supports(logging)) {
                errors.fatal(provider.getClass().getName(), "Logging adapter must match the active logging backend");
            }
        }
        errors.collect().checkValid();
        return Optional.of(providers.getFirst());
    }
}
