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

package io.helidon.microprofile.lra.tck;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;

@ApplicationScoped
public class CoordinatorAppService {

    @Inject
    Config config;

    LazyValue<CoordinatorService> coordinatorService = LazyValue.create(() -> CoordinatorService.builder()
            .config(config.get(CoordinatorService.CONFIG_PREFIX))
            .build());

    @Produces
    @ApplicationScoped
    @RoutingName(value = CoordinatorDeployer.COORDINATOR_ROUTING_NAME, required = true)
    @RoutingPath("/lra-coordinator")
    public CoordinatorService coordinatorService() {
        return coordinatorService.get();
    }

    private void whenApplicationTerminates(@Observes @Destroyed(ApplicationScoped.class) final Object event) {
        coordinatorService.get().shutdown();
    }
}
