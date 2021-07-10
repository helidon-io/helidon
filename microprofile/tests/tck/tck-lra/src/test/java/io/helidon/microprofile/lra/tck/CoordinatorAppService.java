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

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.lra.coordinator.LraJaxbPersistentRegistry;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;

@ApplicationScoped
public class CoordinatorAppService {

    @Inject
    org.eclipse.microprofile.config.Config config;

    LazyValue<Config> seConfig = LazyValue.create(() -> {
        Config.Builder builder = Config.builder();
        Properties p = new Properties();
        config.getPropertyNames().forEach(k -> p.put(k, config.getValue(k, String.class)));
        builder.addSource(ConfigSources.create(p));
        return builder.build();
    });

    LazyValue<LraJaxbPersistentRegistry> persistentRegistry = LazyValue.create(() ->
            new LraJaxbPersistentRegistry(seConfig.get()));

    LazyValue<CoordinatorService> coordinatorService = LazyValue.create(() -> CoordinatorService.builder()
            .persistentRegistry(persistentRegistry.get())
            .config(seConfig.get())
            .build());

    @Produces
    @ApplicationScoped
    @RoutingName(value = CoordinatorDeployer.COORDINATOR_ROUTING_NAME, required = true)
    @RoutingPath("/lra-coordinator")
    public CoordinatorService coordinatorService() {
        return coordinatorService.get();
    }

    private void whenApplicationTerminates(@Observes @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        persistentRegistry.get().save();
    }
}
