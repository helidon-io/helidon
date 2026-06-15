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

import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;
import io.helidon.webserver.spi.TransportBinding.Security;

record TestTransportBindingConfig(String name,
                                  boolean enabled,
                                  boolean failStart,
                                  boolean failReload,
                                  boolean hangStop,
                                  boolean supportsVirtualHosts,
                                  boolean portCapable,
                                  boolean ignoreStopInterrupt,
                                  boolean forceStop,
                                  boolean blockSharedExecutor,
                                  boolean fatalAfterStart,
                                  Security security,
                                  String bindingType) implements TransportBindingFactory {
    static final String TYPE = "test-transport";
    static final String ALTERNATE_TYPE = "alternate-test-transport";

    TestTransportBindingConfig(String name, boolean enabled) {
        this(name, enabled, false, false);
    }

    TestTransportBindingConfig(String name, boolean enabled, boolean failStart) {
        this(name, enabled, failStart, false);
    }

    TestTransportBindingConfig(String name, boolean enabled, boolean failStart, boolean tlsEnabled) {
        this(name, enabled, failStart, false, false, false, false, false, false, false, false,
             security(tlsEnabled), TYPE);
    }

    TestTransportBindingConfig(String name,
                               boolean enabled,
                               boolean failStart,
                               boolean tlsEnabled,
                               boolean failReload,
                               boolean hangStop,
                               boolean supportsVirtualHosts) {
        this(name, enabled, failStart, failReload, hangStop, supportsVirtualHosts,
             false, false, false, false, false, security(tlsEnabled), TYPE);
    }

    TestTransportBindingConfig(String name,
                               boolean enabled,
                               boolean failStart,
                               boolean tlsEnabled,
                               boolean failReload,
                               boolean hangStop,
                               boolean supportsVirtualHosts,
                               boolean portCapable) {
        this(name, enabled, failStart, failReload, hangStop, supportsVirtualHosts,
             portCapable, false, false, false, false, security(tlsEnabled), TYPE);
    }

    static TestTransportBindingConfig listenerTls(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false,
                                              false, false, false, false, false, Security.TLS, TYPE);
    }

    static TestTransportBindingConfig tlsEquivalent(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false,
                                              false, false, false, false, false, Security.TLS_EQUIVALENT, TYPE);
    }

    static TestTransportBindingConfig nullSecurity(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false,
                                              false, false, false, false, false, null, TYPE);
    }

    static TestTransportBindingConfig ignoreStopInterrupt(String name) {
        return new TestTransportBindingConfig(name, true, false, false, true, false,
                                              false, true, false, false, false, Security.UNPROTECTED, TYPE);
    }

    static TestTransportBindingConfig forceStopWithBlockedExecutor(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false,
                                              false, false, true, true, false, Security.UNPROTECTED, TYPE);
    }

    static TestTransportBindingConfig fatalAfterStart(String name) {
        return new TestTransportBindingConfig(name, true, false, false, false, false,
                                              false, false, false, false, true, Security.UNPROTECTED, TYPE);
    }

    static TestTransportBindingConfig alternate(String name, boolean enabled) {
        return new TestTransportBindingConfig(name, enabled, false, false, false, false,
                                              false, false, false, false, false,
                                              security(false), ALTERNATE_TYPE);
    }

    static TestTransportBindingConfig alternate(String name,
                                                boolean enabled,
                                                boolean failStart,
                                                boolean tlsEnabled) {
        return new TestTransportBindingConfig(name, enabled, failStart, false, false, false,
                                              false, false, false, false, false,
                                              security(tlsEnabled), ALTERNATE_TYPE);
    }

    static TestTransportBindingConfig alternate(String name,
                                                boolean enabled,
                                                boolean failStart,
                                                boolean tlsEnabled,
                                                boolean failReload,
                                                boolean hangStop,
                                                boolean supportsVirtualHosts) {
        return new TestTransportBindingConfig(name, enabled, failStart, failReload, hangStop, supportsVirtualHosts,
                                              false, false, false, false, false,
                                              security(tlsEnabled), ALTERNATE_TYPE);
    }

    static TestTransportBindingConfig alternate(String name,
                                                boolean enabled,
                                                boolean failStart,
                                                boolean tlsEnabled,
                                                boolean failReload,
                                                boolean hangStop,
                                                boolean supportsVirtualHosts,
                                                boolean portCapable) {
        return new TestTransportBindingConfig(name, enabled, failStart, failReload, hangStop, supportsVirtualHosts,
                                              portCapable, false, false, false, false,
                                              security(tlsEnabled), ALTERNATE_TYPE);
    }

    @Override
    public String type() {
        return bindingType;
    }

    @Override
    public boolean canBind(BindingPlanContext context) {
        return TestTransportBindingProvider.canBind(this, context);
    }

    @Override
    public TransportBinding create(TransportBindingContext context) {
        return TestTransportBindingProvider.create(this, context);
    }

    private static Security security(boolean tlsEnabled) {
        return tlsEnabled ? Security.TLS : Security.UNPROTECTED;
    }
}
