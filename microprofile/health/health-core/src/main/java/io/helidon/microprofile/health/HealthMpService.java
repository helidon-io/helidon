/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.health;

import java.util.Set;

import javax.ws.rs.core.Application;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.server.spi.MpService;
import io.helidon.microprofile.server.spi.MpServiceContext;

/**
 * Helidon Microprofile Server extension for Health checks.
 */
public class HealthMpService implements MpService {
    @Override
    public void configure(MpServiceContext mpServiceContext) {
        String contextRoot = mpServiceContext.getHelidonConfig().get("helidon.health.web-context").asString("/health");

        mpServiceContext.addApplication(contextRoot, new HealthApplication());
    }

    static class HealthApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return CollectionsHelper.setOf(HealthEndpoint.class);
        }
    }
}
