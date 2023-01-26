/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.maven.plugin;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.helidon.pico.tools.ToolsException;

/**
 * Responsible for creating a spi classlaoder using a child-first delegation strategy that can
 * handle execution of callables, etc. using it.
 */
 class ExecutableClassLoader {
     private ExecutableClassLoader() {
     }

    /**
     * Creates the loader appropriate for {@link ExecHandler}.
     *
     * @param classPath the classpath to use
     * @param parent the parent loader
     * @return the loader
     */
    public static URLClassLoader create(
            Collection<Path> classPath,
            ClassLoader parent) {
        List<URL> urls = new ArrayList<>(classPath.size());
        try {
            for (Path dependency : classPath) {
                urls.add(dependency.toUri().toURL());
            }
        } catch (MalformedURLException e) {
            throw new ToolsException("unable to build classpath", e);
        }

        if (parent == null) {
            parent = Thread.currentThread().getContextClassLoader();
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }

}
