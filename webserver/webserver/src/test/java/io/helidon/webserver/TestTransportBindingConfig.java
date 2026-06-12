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

package io.helidon.webserver;

import io.helidon.webserver.spi.TransportBindingConfig;

record TestTransportBindingConfig(String name,
                                  boolean enabled,
                                  boolean failStart,
                                  boolean tlsEnabled,
                                  boolean failReload,
                                  boolean hangStop,
                                  boolean supportsVirtualHosts,
                                  boolean portCapable,
                                  boolean reportTlsWithoutListenerTls,
                                  boolean ignoreStopInterrupt,
                                  boolean forceStop,
                                  boolean blockSharedExecutor) implements TransportBindingConfig {
    static final String TYPE = "test-transport";

    TestTransportBindingConfig(String name, boolean enabled) {
        this(name, enabled, false, false);
    }

    TestTransportBindingConfig(String name, boolean enabled, boolean failStart) {
        this(name, enabled, failStart, false);
    }

    TestTransportBindingConfig(String name, boolean enabled, boolean failStart, boolean tlsEnabled) {
        this(name, enabled, failStart, tlsEnabled, false, false, false, false, false, false, false, false);
    }

    TestTransportBindingConfig(String name,
                               boolean enabled,
                               boolean failStart,
                               boolean tlsEnabled,
                               boolean failReload,
                               boolean hangStop,
                               boolean supportsVirtualHosts) {
        this(name, enabled, failStart, tlsEnabled, failReload, hangStop, supportsVirtualHosts,
             false, false, false, false, false);
    }

    TestTransportBindingConfig(String name,
                               boolean enabled,
                               boolean failStart,
                               boolean tlsEnabled,
                               boolean failReload,
                               boolean hangStop,
                               boolean supportsVirtualHosts,
                               boolean portCapable) {
        this(name, enabled, failStart, tlsEnabled, failReload, hangStop, supportsVirtualHosts,
             portCapable, false, false, false, false);
    }

    static TestTransportBindingConfig reportTlsWithoutListenerTls(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false, false,
                                              false, true, false, false, false);
    }

    static TestTransportBindingConfig ignoreStopInterrupt(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, true, false,
                                              false, false, true, false, false);
    }

    static TestTransportBindingConfig forceStopWithBlockedExecutor(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false, false,
                                              false, false, false, true, true);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
