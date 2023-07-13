/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver;

import io.helidon.config.Config;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Phase;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.testing.InjectionTestingSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Disabled
class WebServerConfigDrivenTest {
    static final boolean NORMAL_PRODUCTION_PATH = false;

    @AfterEach
    public void reset() {
        if (!NORMAL_PRODUCTION_PATH) {
            // requires 'inject.permits-dynamic=true' to be able to reset
            InjectionTestingSupport.resetAll();
        }
    }

    @Test
    void testConfigDriven() {
        // This will pick up application.yaml from the classpath as default configuration file
        Config config = Config.create();

        if (NORMAL_PRODUCTION_PATH) {
            // bootstrap Injection with our config tree when it initializes
            InjectionServices.globalBootstrap(Bootstrap.builder().config(config).build());
        }

        // initialize Injection, and drive all activations based upon what has been configured
        Services services;
        if (NORMAL_PRODUCTION_PATH) {
            services = InjectionServices.realizedServices();
        } else {
            InjectionServices injectionServices = InjectionTestingSupport.testableServices(config);
            services = injectionServices.services();
        }

        ServiceProvider<WebServer> webServerSp = services.lookupFirst(WebServer.class);
        assertThat(webServerSp.currentActivationPhase(), is(Phase.ACTIVE));
    }

}
