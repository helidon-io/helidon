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
 */

package io.helidon.microprofile.graphql.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Functionality for use by unit and functional tests.
 *
 * @author Tim Middleton 2020.02.28
 */
public abstract class AbstractGraphQLTest {

    /**
     * Create a Jandex index using the given file name and classes.
     *
     * @param fileName  the file name to write the index to. The classes should be in the format of
     *                  "java/lang/Thread.class"
     * @param clazzes   classes to index
     */
    public static void createManualIndex(String fileName, String... clazzes) throws IOException {
         Indexer indexer = new Indexer();
         for (String clazz : clazzes) {
             InputStream stream = AbstractGraphQLTest.class.getClassLoader().getResourceAsStream(clazz);
             indexer.index(stream);
             stream.close();
         }
         Index index = indexer.complete();

         FileOutputStream out = new FileOutputStream(fileName);
         IndexWriter writer = new IndexWriter(out);
         try {
             writer.write(index);
         }
         finally {
             out.close();
         }
    }
    
    /**
     * Return a temporary file which will be used to import the Jandex index to.
     * @return a new {@link File}
     * @throws IOException  if any IO related errors
     */
    public static String getTempIndexFile() throws IOException {
        return Files.createTempFile("index" + System.currentTimeMillis(), "idx").toFile().toString();
    }

    /**
     * Return true if the contents of the String match the contents of the file.
     *
     * @param results  String to compare
     * @param fileName filename to compare
     */
    protected static void assertResultsMatch(String results, String fileName) {
        if (results == null || fileName == null) {
            throw new IllegalArgumentException("sResults or sFileName cannot be null");
        }

        try {
            ClassLoader classLoader = AbstractGraphQLTest.class.getClassLoader();
            URL resource = classLoader.getResource(fileName);
            if (resource == null) {
                throw new IllegalArgumentException("Unable to find comparison file " + fileName);
            }
            File file = new File(resource.getFile());
            String sFromFile = new String(Files.readAllBytes(file.toPath()));

            assertThat("Results do not match expected", results, is(sFromFile));
        }
        catch (Exception e) {
            throw new RuntimeException("Exception in resultsMatch sResults=[" + results + "], sFileName=" + fileName, e);
        }
    }
}
