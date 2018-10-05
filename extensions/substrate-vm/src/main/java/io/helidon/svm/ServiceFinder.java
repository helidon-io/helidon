/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.svm;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Finds all services intended for use with {@link java.util.ServiceLoader}.
 */
final class ServiceFinder {
    void process(URLClassLoader classLoader,
                 BiConsumer<String, InputStream> resourceConsumer,
                 Consumer<Class<?>> serviceClassConsumer) {

        Set<Path> todo = new HashSet<>();

        for (URL url : classLoader.getURLs()) {
            try {
                todo.add(Paths.get(url.toURI()));
            } catch (URISyntaxException | IllegalArgumentException e) {
                throw new ServiceFinderException("Unable to handle classpath element '" + url
                        .toExternalForm() + "'. Make sure that all classpath entries are either directories or valid jar "
                                                         + "files.", e);
            }
        }

        Set<String> found = new HashSet<>();

        for (Path element : todo) {
            try {
                if (Files.isDirectory(element)) {
                    scanExpanded(resourceConsumer, found, element, "");
                } else {
                    scanJar(resourceConsumer, found, element);
                }
            } catch (IOException ex) {
                throw new ServiceFinderException("Unable to handle classpath element '" + element + "'. Make sure that all "
                                                         + "classpath entries are "
                                                         + "either directories or valid jar files.", ex);
            }
        }

        for (String className : found) {
            try {
                System.out.println("Found service implementation class: " + className);
                serviceClassConsumer.accept(Class.forName(className));
            } catch (ClassNotFoundException e) {
                System.err.println("Failed to Class.forName(" + className + "). " + e.getMessage());
                throw new ServiceFinderException("A ServiceLoader service implementation " + className + " could not be created",
                                                 e);
            }
        }
    }

    private void scanExpanded(BiConsumer<String, InputStream> consumer,
                              Set<String> found,
                              Path toProcess,
                              String relativePath) throws IOException {
        if (Files.isDirectory(toProcess)) {
            Files.list(toProcess)
                    .forEach(path -> {
                        try {
                            scanExpanded(consumer,
                                         found,
                                         path,
                                         relativePath.isEmpty()
                                                 ? path.getFileName().toString()
                                                 : (relativePath + "/" + path.getFileName()));
                        } catch (IOException e) {
                            throw new ServiceFinderException("Failed to process classpath element: " + path, e);
                        }
                    });
        } else {
            if (matches(relativePath)) {
                Files.readAllLines(toProcess).stream()
                        .map(String::trim)
                        .filter(line -> !line.startsWith("#"))
                        .filter(line -> !line.isEmpty())
                        .forEach(found::add);

                consumer.accept(relativePath, Files.newInputStream(toProcess));
            }
        }
    }

    private static void scanJar(BiConsumer<String, InputStream> consumer,
                                Set<String> found,
                                Path element) throws IOException {
        try (JarFile jf = new JarFile(element.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (e.getName().endsWith("/")) {
                    continue;
                }
                if (matches(e.getName())) {
                    try (InputStream is = jf.getInputStream(e)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = is.read(buffer)) > 0) {
                            baos.write(buffer, 0, read);
                        }
                        try (BufferedReader br =
                                new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(baos.toByteArray())))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                if (!line.startsWith("#") && !line.isEmpty()) {
                                    found.add(line);
                                }
                            }
                        }
                    }
                    try (InputStream is = jf.getInputStream(e)) {
                        consumer.accept(e.getName(), is);
                    }
                }
            }
        }
    }

    private static final Set<String> IGNORED_SERVICE_PREFIXES = new HashSet<>();

    static {
        // java internal stuff
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/com.sun.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/sun.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/javax.print.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/javax.sound.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/javax.script.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/java.nio.file.spi.FileSystemProvider");

        // Graal & Substrate VM internal stuff
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/org.graalvm.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/jdk.vm.");
        IGNORED_SERVICE_PREFIXES.add("META-INF/services/com.oracle.truffle.");
    }

    private static boolean matches(String relativePath) {
        if (relativePath.startsWith("META-INF/services")) {
            // good start, now let`s filter out services we are NOT interested in¨
            for (String prefix : IGNORED_SERVICE_PREFIXES) {
                if (relativePath.startsWith(prefix)) {
                    return false;
                }
            }
            System.out.println("Found service resource: " + relativePath);
            return true;
        }
        return false;
    }

    static final class ServiceFinderException extends RuntimeException {
        private ServiceFinderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
