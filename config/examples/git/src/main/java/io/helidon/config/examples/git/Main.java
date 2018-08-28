/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import static io.helidon.config.git.GitConfigSourceBuilder.from;

/**
 * Git source example.
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

        Config env = Config.from(ConfigSources.environmentVariables());

        Config config = Config.from(
                from("application.conf")
                        .uri(URI.create("https://github.com/okosatka/test-config.git"))
                        .branch(env.get(ENVIRONMENT_NAME_PROPERTY).asString("master"))
                        .build());

        assert config.get("greeting").asString().equals("hello");

    }

}
