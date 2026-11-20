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

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.logging.common.LogConfig;
import io.helidon.logging.common.spi.LoggingProvider;
import io.helidon.telemetry.logging.spi.LogBridgeProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogBridgesTest {
    private static LoggingProvider selected;
    private static boolean created;

    @TempDir
    Path directory;

    @Test
    void absenceOfAdapterIsSupported() {
        assertThat(LogBridges.findProvider(getClass().getClassLoader()).isEmpty(), is(true));
    }

    @Test
    void discoveryUsesPreviouslySelectedBackendAndDoesNotCreateHooks() throws Exception {
        var expected = LogConfig.provider();
        selected = null;
        created = false;
        try (var loader = providers(Provider.class)) {
            Files.writeString(directory.resolve("META-INF/services/" + LoggingProvider.class.getName()),
                              UnexpectedLoggingProvider.class.getName());
            var thread = Thread.currentThread();
            var previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(loader);
                var provider = LogBridges.findProvider(loader).orElseThrow();
                assertThat(provider, instanceOf(Provider.class));
                assertThat(selected, sameInstance(expected));
                assertThat(LogConfig.provider(), sameInstance(expected));
                assertThat(created, is(false));
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }

    @Test
    void independentConfigurationViolationsAreCollected() throws Exception {
        created = false;
        try (var loader = providers(Provider.class, UnsupportedProvider.class)) {
            var failure = assertThrows(RuntimeException.class, () -> LogBridges.findProvider(loader));
            assertThat(failure.getMessage(), containsString("at most one"));
            assertThat(failure.getMessage(), containsString("active logging backend"));
            assertThat(created, is(false));
        }
    }

    @Test
    void rejectsNullDiscoveryLoader() {
        assertThrows(NullPointerException.class, () -> LogBridges.findProvider(null));
    }

    private URLClassLoader providers(Class<?>... types) throws Exception {
        var services = directory.resolve("META-INF/services");
        Files.createDirectories(services);
        var names = new StringBuilder();
        for (var type : types) {
            names.append(type.getName()).append('\n');
        }
        Files.writeString(services.resolve(LogBridgeProvider.class.getName()), names);
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, getClass().getClassLoader());
    }

    public static class Provider implements LogBridgeProvider {
        @Override
        public boolean supports(LoggingProvider provider) {
            selected = provider;
            return true;
        }

        @Override
        public LogBridge create(LogBridgeContext context) {
            created = true;
            throw new AssertionError("Discovery must not create logging hooks");
        }
    }

    public static class UnsupportedProvider extends Provider {
        @Override
        public boolean supports(LoggingProvider provider) {
            return false;
        }
    }

    public static class UnexpectedLoggingProvider implements LoggingProvider {
        public UnexpectedLoggingProvider() {
            throw new AssertionError("Logging provider must not be selected again through another class loader");
        }

        @Override
        public void initialization() {
        }

        @Override
        public void runTime() {
        }
    }
}
