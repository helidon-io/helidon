/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

import org.hamcrest.CoreMatchers;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Assertions;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Functionality for use by unit and functional tests.
 *
 * @author Tim Middleton 2020.02.28
 */
public abstract class AbstractGraphQLTest {

    private SchemaPrinter schemaPrinter;

    /**
     * Create a Jandex index using the given file name and classes.
     *
     * @param fileName the file name to write the index to. The classes should be in the format of "java/lang/Thread.class"
     * @param clazzes  classes to index
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
        } finally {
            out.close();
        }
    }

    /**
     * Return a temporary file which will be used to import the Jandex index to.
     *
     * @return a new {@link File}
     * @throws IOException if any IO related errors
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
        } catch (Exception e) {
            throw new RuntimeException("Exception in resultsMatch sResults=[" + results + "], sFileName=" + fileName, e);
        }
    }

    protected GraphQLSchema generateGraphQLSchema(Schema schema) {

        try {
            GraphQLSchema graphQLSchema = schema.generateGraphQLSchema();
            displaySchema(graphQLSchema);
            return graphQLSchema;
        } catch (Exception e) {
            Assertions.fail("Schema generation failed. " + e.getMessage() +
                                    "\ncause: " + e.getCause() +
                                    "\nSchema: \n" + schema.getSchemaAsString());
            return null;
        }
    }

    protected void displaySchema(GraphQLSchema graphQLSchema) {
        System.err.println("Schema:\n=======\n"
                                   + getSchemaPrinter().print(graphQLSchema) +
                                   "\n=======");
    }

    protected SchemaPrinter getSchemaPrinter() {
        if (schemaPrinter == null) {
            SchemaPrinter.Options options = SchemaPrinter.Options
                    .defaultOptions().includeDirectives(false)
                    .includeExtendedScalarTypes(true)
                    .includeScalarTypes(true);
            schemaPrinter = new SchemaPrinter(options);
        }
        return schemaPrinter;
    }

    /**
     * Setup an index file for the given {@link Class}es.
     *
     * @param clazzes classes to setup index for
     * @return a {@link JandexUtils} instance
     * @throws IOException
     */
    protected static void setupIndex(String indexFileName, Class<?>... clazzes) throws IOException {
        createManualIndex(indexFileName, Arrays.stream(clazzes).map(c -> getIndexClassName(c)).toArray(String[]::new));
        System.setProperty(JandexUtils.PROP_INDEX_FILE, indexFileName);
        assertThat(indexFileName, CoreMatchers.is(notNullValue()));
        File indexFile = new File(indexFileName);
        assertThat(indexFile.exists(), CoreMatchers.is(true));

        // do a load to check the classes are there
        JandexUtils utils = new JandexUtils();
        utils.loadIndex();
        assertThat(utils.hasIndex(), CoreMatchers.is(true));
        assertThat(utils.getIndex().getKnownClasses().size(), CoreMatchers.is(clazzes.length));
    }

    protected static String getIndexClassName(Class clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Must not be null");
        }
        return clazz.getName().replaceAll("\\.", "/") + ".class";
    }

    protected boolean hasErrors(ExecutionResult executionResult) {
        return executionResult.getErrors().size() > 0;
    }

    protected void displayErrors(ExecutionResult result) {
        System.err.println(getError(result));
    }

    protected String getError(ExecutionResult result) {
        assertThat(result, CoreMatchers.is(notNullValue()));
        StringBuilder sb = new StringBuilder("Errors: ");
        result.getErrors().forEach(e -> sb.append(e.getErrorType()
                                                          .toString())
                .append(" ")
                .append(e.getMessage())
                .append(" ")
                .append(e.getLocations() != null ? e.getLocations().toString() : "")
                .append('\n'));
        return sb.toString();
    }

    /**
     * Assert an {@link ExecutionResult} is true and if not then display the error.
     *
     * @param result {@link ExecutionResult} data
     */
    protected Map<String, Object> getAndAssertResult(ExecutionResult result) {
        boolean failed = result.getErrors().size() > 0;
        if (failed) {
            String sError = getError(result);
            fail(sError);
        }
        return result.getData();
    }

    /**
     * Assert an {@link ExecutionResult} is true and if not then display the error.
     *
     * @param result {@link ExecutionResult} extensions
     */
    protected Map<Object, Object> getAndAssertExtensions(ExecutionResult result) {
        boolean failed = result.getErrors().size() > 0;
        if (failed) {
            String sError = getError(result);
            fail(sError);
        }
        return result.getExtensions();
    }
}
