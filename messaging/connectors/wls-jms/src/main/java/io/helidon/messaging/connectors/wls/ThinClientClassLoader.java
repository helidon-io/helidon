/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.messaging.connectors.wls;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import io.helidon.common.LazyValue;

import static java.lang.System.Logger.Level.TRACE;

class ThinClientClassLoader extends URLClassLoader {

    private static final System.Logger LOGGER = System.getLogger(ThinClientClassLoader.class.getName());
    private static final LazyValue<ThinClientClassLoader> ISOLATION_CL = LazyValue.create(ThinClientClassLoader::new);
    private static volatile String thinJarLocation = "wlthint3client.jar";
    private final ClassLoader contextClassLoader;

    ThinClientClassLoader() {
        super("thinClientClassLoader", new URL[0], null);
        contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {

            File currDirFile = Path.of("", thinJarLocation).toFile();
            LOGGER.log(TRACE, "Looking for Weblogic thin client jar file " + currDirFile.getPath() + " on filesystem");
            if (currDirFile.exists()) {
                this.addURL(currDirFile.toURI().toURL());
                return;
            }

            throw new RuntimeException("Can't locate thin jar file " + thinJarLocation);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (inWlsJar(name)) {
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException e) {
                LOGGER.log(TRACE, () -> "Cannot load class "
                        + name
                        + " from WLS thin client classloader, delegating to ctx classloader.", e);
                contextClassLoader.loadClass(name);
            }
        }
        return contextClassLoader.loadClass(name);
    }

    @Override
    public URL getResource(String name) {

        if (inWlsJar(name)) {
            return super.getResource(name);
        }
        return contextClassLoader.getResource(name);
    }

    static ThinClientClassLoader getInstance() {
        return ISOLATION_CL.get();
    }

    static void setThinJarLocation(String thinJarLocation) {
        ThinClientClassLoader.thinJarLocation = thinJarLocation;
    }

    static <T> T executeInIsolation(IsolationSupplier<T> supplier) {
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ISOLATION_CL.get());
            return supplier.get();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }
    }

    boolean inWlsJar(String name) {
        // Load jms exceptions from inside the thin jar to avoid deserialization issues
        if ((name.startsWith("javax.jms") || name.startsWith("jakarta.jms"))
                && name.endsWith("Exception")) {
            return true;
        }

        // Load only javax and jakarta JMS API from outside, so cast works
        return !name.startsWith("javax.jms")
                && !name.startsWith("jakarta.jms")
                && !name.equals(IsolatedContextFactory.class.getName());
    }

    @FunctionalInterface
    interface IsolationSupplier<T> {
        T get() throws Throwable;
    }
}
