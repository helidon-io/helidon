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
package io.helidon.tests.integration.harness;

import java.io.File;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Path utility class.
 */
public class PathFinder {

    private static final System.Logger LOGGER = System.getLogger(PathFinder.class.getName());
    private static final boolean IS_WINDOWS = File.pathSeparatorChar != ':';
    private static final String PATH_ENV_VAR = "PATH";
    private static final List<String> WINDOWS_EXECUTABLE_EXTENSIONS = List.of("exe", "bin", "bat", "cmd", "ps1");
    private static final Predicate<Path> VALID_PATH = p -> Files.exists(p) && Files.isDirectory(p);
    private static final List<Path> PATH_ENTRIES =
            Optional.ofNullable(System.getenv(PATH_ENV_VAR))
                    .map(p -> Arrays.asList(p.split(File.pathSeparator)))
                    .stream()
                    .flatMap(Collection::stream)
                    .map(Path::of)
                    .filter(VALID_PATH)
                    .toList();

    private PathFinder() {
    }

    private static Path findWindowsCmd(Path dir, String cmd) {
        return WINDOWS_EXECUTABLE_EXTENSIONS.stream()
                .map((ext) -> dir.resolve(cmd + "." + ext))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
    }

    private static Path findCmd(Path dir, String cmd) {
        LOGGER.log(Level.DEBUG, String.format("Searching for cmd: %s in %s", cmd, dir));
        Path cmdFile = dir.resolve(cmd);
        if (Files.isRegularFile(cmdFile)) {
            return cmdFile;
        }
        return IS_WINDOWS ? findWindowsCmd(dir, cmd) : null;
    }

    /**
     * Find a file in the system path.
     *
     * @param fileName file to find
     * @param envVar   override
     * @return optional
     */
    public static Optional<Path> find(String fileName, String envVar) {
        return Stream.concat(
                        Optional.ofNullable(System.getenv(envVar))
                                .map(Path::of)
                                .map(p -> p.resolve("bin"))
                                .stream()
                                .filter(VALID_PATH),
                        PATH_ENTRIES.stream())
                .flatMap(dir -> Optional.ofNullable(findCmd(dir, fileName)).stream())
                .findFirst();
    }
}
