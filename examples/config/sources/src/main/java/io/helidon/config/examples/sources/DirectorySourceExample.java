/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.examples.sources;

import io.helidon.config.Config;

import static io.helidon.config.ConfigSources.directory;

/**
 * This example shows how to read configuration from several files placed in selected directory.
 */
public class DirectorySourceExample {

    private DirectorySourceExample() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        /*
           Creates a config from files from specified directory.
           E.g. Kubernetes Secrets:
         */

        Config secrets = Config.builder(directory("conf/secrets"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        String username = secrets.get("username").asString().get();
        System.out.println("Username: " + username);
        assert username.equals("libor");

        String password = secrets.get("password").asString().get();
        System.out.println("Password: " + password);
        assert password.equals("^ery$ecretP&ssword");
    }

}
