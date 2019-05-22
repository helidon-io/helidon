/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.microprofile.openapi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

/**
 * Portable extension to allow construction of a Jandex index (to pass to
 * SmallRye OpenAPI) from CDI if no {@code META-INF/jandex.idx} file exists on
 * the class path.
 */
public class IndexBuilder implements Extension {

    private static final String INDEX_PATH = "/META-INF/jandex.idx";

    private static final Logger LOGGER = Logger.getLogger(IndexBuilder.class.getName());

    private final boolean isIndexPresentOnClasspath;

    private final Set<Class<?>> annotatedTypes = new HashSet<>();

    /**
     * Creates a new instance of the index builder.
     *
     * @throws IOException in case of error checking for the Jandex index file
     */
    public IndexBuilder() throws IOException {
        isIndexPresentOnClasspath = checkForIndexFile();
        if (isIndexPresentOnClasspath) {
            LOGGER.log(Level.FINE, () -> String.format("Index file %s was located and will be used", INDEX_PATH));
        } else {
            LOGGER.log(Level.INFO, () -> String.format(
                    "OpenAPI support could not locate the Jandex index file %s "
                    + "so will build an in-memory index.%n"
                    + "This slows your app start-up and, depending on CDI configuration, "
                    + "might omit some type information needed for a complete OpenAPI document.%n"
                    + "Consider using the Jandex maven plug-in during your build "
                    + "to create the index and add it to your app.",
                    INDEX_PATH));
        }
    }

    /**
     * Records each type that is annotated unless a Jandex index was found on
     * the classpath (in which case we do not need to build our own in memory).
     *
     * @param <X> annotated type
     * @param event {@code ProcessAnnotatedType} event
     */
    private <X> void processAnnotatedType(@Observes ProcessAnnotatedType<X> type) {
        if (isIndexPresentOnClasspath) {
            return;
        }
        Class<?> c = type.getAnnotatedType().getJavaClass();
        annotatedTypes.add(c);
    }

    /**
     * Reports an {@link IndexView} for the Jandex index that describes
     * annotated classes for endpoints.
     *
     * @return {@code IndexView} describing discovered classes
     * @throws IOException in case of error reading an existing index file or
     * reading class bytecode from the classpath
     */
    public IndexView indexView() throws IOException {
        return isIndexPresentOnClasspath ? existingIndexFileReader() : indexFromHarvestedClasses();
    }

    private IndexView existingIndexFileReader() throws IOException {
        try (InputStream jandexIS = getClass().getResourceAsStream(INDEX_PATH)) {
            if (jandexIS == null) {
                throw new IllegalArgumentException("Attempted to read from previously-located index file "
                        + INDEX_PATH + " but the file cannot be found");
            }
            LOGGER.log(Level.FINE, "Using Jandex index at {0}", INDEX_PATH);
            return new IndexReader(jandexIS).read();
        }
    }

    private IndexView indexFromHarvestedClasses() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> c : annotatedTypes) {
            try (InputStream is = contextClassLoader().getResourceAsStream(resourceNameForClass(c))) {
                if (is == null) {
                    throw new IllegalArgumentException("Cannot load bytecode from class "
                            + c.getName() + " at " + resourceNameForClass(c)
                            + " for annotation processing");
                }
                indexer.index(is);
            }
        }

        LOGGER.log(Level.FINE, "Using internal Jandex index created from CDI bean discovery");
        Index result = indexer.complete();
        dumpIndex(Level.FINER, result);
        return result;
    }

    private boolean checkForIndexFile() throws IOException {
        try (InputStream jandexIS = getClass().getResourceAsStream(INDEX_PATH)) {
            return jandexIS != null;
        }
    }

    private static void dumpIndex(Level level, Index index) throws UnsupportedEncodingException {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, "Dump of internal Jandex index:");
            PrintStream oldStdout = System.out;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream newPS = new PrintStream(baos, true, Charset.defaultCharset().name())) {
                System.setOut(newPS);
                index.printAnnotations();
                index.printSubclasses();
                LOGGER.log(level, baos.toString(Charset.defaultCharset().name()));
            } finally {
                System.setOut(oldStdout);
            }
        }
    }

    private static ClassLoader contextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private static String resourceNameForClass(Class<?> c) {
        return c.getName().replace('.', '/') + ".class";
    }
}
