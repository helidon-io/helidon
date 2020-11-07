/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

/**
 * Utilities for working with Jandex indexes.
 */
class JandexUtils {

    private static final Logger LOGGER = Logger.getLogger(JandexUtils.class.getName());

    /**
     * Default Jandex index file.
     */
    protected static final String DEFAULT_INDEX_FILE = "META-INF/jandex.idx";

    /**
     * Property to override the default index file. (Normally used for functional tests)
     */
    public static final String PROP_INDEX_FILE = "io.helidon.microprofile.graphql.indexfile";

    /**
     * The {@link Set} of loaded indexes.
     */
    private Set<Index> setIndexes = new HashSet<>();

    /**
     * The file used to load the index.
     */
    private String indexFile;

    /**
     * Construct an instance of the utilities class..
     */
    private JandexUtils() {
        indexFile = System.getProperty(PROP_INDEX_FILE, DEFAULT_INDEX_FILE);
    }

    /**
     * Create a new {@link JandexUtils}.
     * @return a new {@link JandexUtils}
     */
    public static JandexUtils create() {
         return new JandexUtils();
    }

    /**
     * Load all the index files of the given name.
     */
    public void loadIndexes() {
        try {
            List<URL> listUrls = findIndexFiles(indexFile);

            // loop through each URL and load the index
            for (URL url : listUrls) {
                try (InputStream input = url.openStream()) {
                    setIndexes.add(new IndexReader(input).read());
                } catch (Exception e) {
                    LOGGER.warning("Unable to load default Jandex index file: " + url
                                           + " : " + e.getMessage());
                }
            }
        } catch (IOException ignore) {
            // any Exception coming from getResources() or toURL() is ignored and
            // the Map of indexes remain empty
        }
    }

    /**
     * Return all the Jandex index files with the given name. If the name is absolute then
     * return the singl file.
     *
     * @param indexFileName  index file name
     * @return a {@link List} of the index file names
     *
     * @throws IOException if any error
     */
    private List<URL> findIndexFiles(String indexFileName) throws IOException {
        List<URL> result = new ArrayList<>();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(indexFile);
        if (file.isAbsolute()) {
            result.add(file.toPath().toUri().toURL());
            return result;
        }

        Enumeration<URL> urls = contextClassLoader.getResources(indexFileName);
        while (urls.hasMoreElements()) {
            result.add(urls.nextElement());
        }

        return result;
    }


    /**
     * Return a {@link Collection} of {@link Class}es which are implementors of a given class/interface.
     *
     * @param clazz           {@link Class} to check for implementors
     * @param includeAbstract indicates if abstract classes should be included
     * @return a {@link Collection} of {@link Class}es
     */
    public Collection<Class<?>> getKnownImplementors(String clazz, boolean includeAbstract) {
        Set<Class<?>> setResults = new HashSet<>();
        if (!hasIndex()) {
            return null;
        }

        for (Index index : setIndexes) {
            Set<ClassInfo> allKnownImplementors = index.getAllKnownImplementors(DotName.createSimple(clazz));
            for (ClassInfo classInfo : allKnownImplementors) {
                Class<?> clazzName = null;
                try {
                    clazzName = Class.forName(classInfo.toString());
                } catch (ClassNotFoundException e) {
                    // ignore as class should exist
                }
                if (includeAbstract || !Modifier.isAbstract(clazzName.getModifiers())) {
                    setResults.add(clazzName);
                }
            }
        }

        return setResults;
    }

    /**
     * Return a {@link Collection} of {@link Class}es which are implementors of a given class/interface and are not abstract.
     *
     * @param clazz {@link Class} to check for implementors
     * @return a {@link Collection} of {@link Class}es
     */
    public Collection<Class<?>> getKnownImplementors(String clazz) {
        return getKnownImplementors(clazz, false);
    }

    /**
     * Indicates if an index was found.
     *
     * @return true if an index was found
     */
    public boolean hasIndex() {
        return setIndexes != null && setIndexes.size() > 0;
    }

    /**
     * Return the generated {@link Set} of {@link Index}es.
     *
     * @return the generated {@link Set} of {@link Index}es
     */
    public Set<Index> getIndexes() {
        return setIndexes;
    }

    /**
     * The index file used to load the index. (may not exist).
     *
     * @return index file used to load the index
     */
    public String getIndexFile() {
        return indexFile;
    }
}
