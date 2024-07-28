/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.logging.common.LogConfig;

/**
 * Main class to start generating config reference documentation.
 */
public final class Main {
    static {
        LogConfig.initClass();
    }

    private Main() {
    }

    /**
     * Start generating reference documentation.
     *
     * @param args either empty (target path is discovered if possible), or a single parameter - the target path to generate docs
     * @throws io.helidon.config.metadata.docs.ConfigDocsException in case the path is not correct, or a problem is discovered
     *                                                             while generating the documentation
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Path targetPath;

        if (args.length == 0) {
            targetPath = findReasonablePath();
        } else if (args.length == 1) {
            targetPath = Paths.get(args[0]);
        } else {
            throw new IllegalArgumentException("This tool can have zero or one parameters "
                                                       + "(path to generated code). Got " + args.length + " parameters");
        }

        Path path = targetPath.toAbsolutePath().normalize();
        if (Files.exists(path) && Files.isDirectory(path)) {
            ConfigDocs docs = ConfigDocs.create(path);
            docs.process();
        } else {
            throw new IllegalArgumentException("Target path must be a directory and must exist: "
                                                       + path.toAbsolutePath().normalize());
        }
    }

    private static Path findReasonablePath() {
        Path p = Paths.get("docs/src/main/asciidoc/config");
        if (Files.exists(p) && Files.isDirectory(p)) {
            return p;
        }
        p = Paths.get(".").toAbsolutePath().normalize();
        if (p.toString().replace('\\', '/').endsWith("config/metadata/docs")) {
            // we are probably in Helidon repository in config/metadata/docs
            return p.resolve("../../../docs/src/main/asciidoc/config");
        }
        throw new IllegalArgumentException("Cannot discover config asciidoc path, please provide it as a parameter");
    }
}
