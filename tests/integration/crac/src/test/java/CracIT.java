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

import java.nio.file.Path;

import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.WebClient;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers(disabledWithoutDocker = true)
class CracIT {
    static {
        LogConfig.initClass();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CracIT.class);

    private static final ImageFromDockerfile image = new ImageFromDockerfile().withDockerfile(Path.of("./Dockerfile"));

    @Container
    static final GenericContainer<?> CONTAINER = new GenericContainer<>(image)
            .withExposedPorts(8080)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forListeningPort());

    private final WebClient webClient = WebClient.builder()
            .baseUri("http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8080))
            .build();

    @Test
    void getCrackedPokemon() {
        try (var res = webClient.get("pokemon/400").request()) {
            assertThat(res.as(JsonObject.class).getString("name"), equalTo("CRaCasaur"));
        }
    }
}
