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
package io.helidon.lra.coordinator;

import java.net.URI;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class LraDatabasePersistentRegistryTest {

    @Test
    void longParticipantLinksSurvivePersistence() {
        Config config = Config.create(ConfigSources.create(Map.of(
                        "helidon.lra.coordinator.db.connection.url",
                        "jdbc:h2:mem:lra-long-participant-link;DB_CLOSE_DELAY=-1")),
                ConfigSources.classpath("application.yaml"))
                .get(CoordinatorService.CONFIG_PREFIX);
        URI callbackUri = URI.create("https://participant.example/lra-participant/compensate/"
                                             + "io.helidon.microprofile.lra.ParticipantTest"
                                             + "$SuperclassConsumesInterfaceJaxRsParticipantResource/"
                                             + "superclassConsumesInterfaceCompensate"
                                             + "?helidon-lra-capability=v1."
                                             + "x".repeat(43));
        assertThat(callbackUri.toASCIIString().length(), greaterThan(255));

        LraDatabasePersistentRegistry registry = new LraDatabasePersistentRegistry(config);
        CoordinatorService coordinator = CoordinatorService.builder()
                .config(config)
                .url(() -> URI.create("https://coordinator.example/lra-coordinator"))
                .persistentRegistry(registry)
                .build();
        LraImpl lra = new LraImpl(coordinator, "long-participant-link", config);
        lra.addParticipant("<" + callbackUri + ">; rel=\"complete\"");
        registry.put(lra.lraId(), lra);
        coordinator.shutdown();

        CoordinatorService recoveredCoordinator = CoordinatorService.builder()
                .config(config)
                .url(() -> URI.create("https://coordinator.example/lra-coordinator"))
                .build();
        try {
            Lra recoveredLra = recoveredCoordinator.lra(lra.lraId());
            assertThat(recoveredLra.participants().getFirst().completeURI().orElseThrow(), is(callbackUri));
        } finally {
            recoveredCoordinator.shutdown();
        }
    }
}
