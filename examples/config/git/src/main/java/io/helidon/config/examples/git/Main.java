/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
package io.helidon.config.examples.git;

import java.io.IOException;
import java.net.URI;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.git.GitConfigSource;

/**
 * Git source example.
 * <p>
 * This example expects:
 * <ol>
 * <li>a Git repository {@code helidonrobot/test-config} which contains:
 * <ol type="a">
 * <li>the branch {@code test} containing {@code application.conf} which sets
 * {@code greeting} to {@code hello},
 * <li>the branch {@code main} containing the file {@code application.conf}
 * which sets the property {@code greeting} to any value other than
 * {@code hello},
 * <li>optionally, any other branch in which {@code application.conf} sets
 * {@code greeting} to {@code hello}.
 * </ol>
 * <li>the environment variable {@code ENVIRONMENT_NAME} set to:
 * <ol type="a">
 * <li>{@code test}, or
 * <li>the name of the optional additional branch described above.
 * </ol>
 * </ol>
 */
public class Main {

    private static final String ENVIRONMENT_NAME_PROPERTY = "ENVIRONMENT_NAME";

    private Main() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     * @throws IOException when some git repo operation failed
     */
    public static void main(String... args) throws IOException {

        // we expect a name of the current environment in envvar ENVIRONMENT_NAME
        // in this example we just set envvar in maven plugin 'exec', but can be set in k8s pod via ConfigMap
        Config env = Config.create(ConfigSources.environmentVariables());

        String branch = env.get(ENVIRONMENT_NAME_PROPERTY).asString().orElse("master");

        System.out.println("Loading from branch " + branch);

        Config config = Config.create(
                GitConfigSource.builder()
                        .path("application.conf")
                        .uri(URI.create("https://github.com/helidonrobot/test-config.git"))
                        .branch(branch)
                        .build());

        System.out.println("Greeting is " + config.get("greeting").asString().get());
        assert config.get("greeting").asString().get().equals("hello");
    }

}
