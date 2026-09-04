/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.docs;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.LogManager;

import io.helidon.config.metadata.model.CmModel;

/**
 * Config docs generator entry point.
 */
public final class Main {

    private Main() {
    }

    /**
     * Process the config metadata from the classpath and generate the corresponding documentation.
     *
     * @param args a single parameter (the output directory)
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: output_directory");
            System.exit(1);
        }
        configureLogging();
        var outputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        var metadata = CmModel.loadAll(Main.class.getClassLoader());
        var docs = new CmDocCodegen(outputDir, metadata);
        docs.process();
    }

    private static void configureLogging() {
        if (System.getProperty("java.util.logging.config.file") == null
            && System.getProperty("java.util.logging.config.class") == null) {

            try (var is = Main.class.getResourceAsStream("logging.properties")) {
                if (is != null) {
                    LogManager.getLogManager().readConfiguration(is);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
