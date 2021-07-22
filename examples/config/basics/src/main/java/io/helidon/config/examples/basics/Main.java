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

package io.helidon.config.examples.basics;

import java.nio.file.Path;
import java.util.List;

import io.helidon.config.Config;

import static io.helidon.config.ConfigSources.classpath;

/**
 * Basics example.
 */
public class Main {

    private Main() {
    }

    /**
     * Executes the example.
     *
     * @param args arguments
     */
    public static void main(String... args) {
        Config config = Config.create(classpath("application.conf"));

        int pageSize = config.get("app.page-size").asInt().get();

        boolean storageEnabled = config.get("app.storageEnabled").asBoolean().orElse(false);

        List<Integer> basicRange = config.get("app.basic-range").asList(Integer.class).get();

        Path loggingOutputPath = config.get("logging.outputs.file.name").as(Path.class).get();

        System.out.println(pageSize);
        System.out.println(storageEnabled);
        System.out.println(basicRange);
        System.out.println(loggingOutputPath);
    }

}
