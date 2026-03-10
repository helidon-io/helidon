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
package io.helidon.codegen.testing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

/**
 * Test paths.
 */
class TestPaths {

    private TestPaths() {
    }

    /**
     * Get the file system location for a given class file.
     *
     * @param clazz class for which to derive the location
     * @return Path
     */
    static Path paths(Class<?> clazz) {
        try {
            return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new working directory based on the caller class and method.
     *
     * @param predicate predicate used to filter out the first frame
     * @return Path
     */
    static Path newWorkDir(Predicate<StackWalker.StackFrame> predicate) {
        var frame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream.filter(it -> !it.getDeclaringClass().equals(TestPaths.class) && predicate.test(it))
                        .findFirst())
                .orElseThrow();

        return newWorkDir(frame.getDeclaringClass(), frame.getMethodName());
    }

    private static Path newWorkDir(Class<?> declaringClass, String methodName) {
        try {
            var classesDir = Paths.get(declaringClass.getProtectionDomain().getCodeSource().getLocation().toURI());
            var targetDir = classesDir.getParent();
            if (targetDir == null) {
                throw new IllegalStateException("Unable to derive target directory");
            }

            // ensure unique directory
            String prefix = dirName(declaringClass.getSimpleName());
            String suffix = dirName(methodName);
            var workDir = targetDir.resolve(prefix).resolve(suffix);
            for (int i = 1; Files.exists(workDir); i++) {
                workDir = targetDir.resolve(prefix + "-" + i).resolve(suffix);
            }
            return Files.createDirectories(workDir);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String dirName(String str) {
        var sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!sb.isEmpty()) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
