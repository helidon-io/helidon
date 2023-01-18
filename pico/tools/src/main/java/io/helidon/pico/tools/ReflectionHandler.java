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

package io.helidon.pico.tools;

import io.helidon.pico.spi.Resetable;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

/**
 * Handles anything involving classpath scanning and introspection.
 */
class ReflectionHandler implements Resetable {

    /**
     * The shared instance.
     */
    static final ReflectionHandler INSTANCE = new ReflectionHandler();

    private ClassLoader loader;
    private ScanResult scan;

    @Override
    public boolean reset(
            boolean deep) {
        if (deep) {
            loader = getCurrentLoader();
            scan = new ClassGraph()
                    .overrideClassLoaders(loader)
                    .enableAllInfo()
                    .scan();

        } else {
            loader = null;
            scan = null;
        }
        return true;
    }

    private ClassLoader getCurrentLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = getClass().getClassLoader();
        }
        return loader;
    }

    /**
     * Lazy scan the classpath.
     *
     * @return the scan result
     */
    ScanResult scan() {
        if (scan == null || loader != getCurrentLoader()) {
            reset(true);
        }

        return scan;
    }

}
