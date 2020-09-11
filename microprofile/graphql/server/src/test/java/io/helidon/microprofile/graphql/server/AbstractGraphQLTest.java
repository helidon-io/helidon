/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.beans.IntrospectionException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
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

    @SuppressWarnings("unchecked")
    protected String getError(List<Map<String, Object>> result) {
        assertThat(result, CoreMatchers.is(notNullValue()));
        StringBuilder sb = new StringBuilder("Errors: ");
        for (Map<String, Object> mapError : result) {
            sb.append(mapError.get(ExecutionContext.MESSAGE)).append('\n');
            List<Map<String, Object>> listLocations = (List<Map<String, Object>>) mapError.get(ExecutionContext.LOCATIONS);
            Map<String, Object> mapExtensions = (Map<String, Object>) mapError.get(ExecutionContext.EXTENSIONS);

            if (listLocations != null) {
                for (Map<String, Object> mapLocations : listLocations) {
                    sb.append(ExecutionContext.LINE).append(':')
                            .append(mapLocations.get(ExecutionContext.LINE))
                            .append(ExecutionContext.COLUMN).append(':')
                            .append(mapLocations.get(ExecutionContext.COLUMN));
                }
            }

            if (mapExtensions != null) {
                mapExtensions.entrySet().forEach((e) -> sb.append(e.getKey() + "=" + e.getValue()));
            }
        }
        return sb.toString();
    }

    /**
     * Assert an {@link ExecutionResult} is true and if not then display the error and fail.
     *
     * @param result {@link ExecutionResult} data
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getAndAssertResult(Map<String, Object> result) {
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) result.get(ExecutionContext.ERRORS);
        boolean failed = listErrors != null && listErrors.size() > 0;
        if (failed) {
            String sError = getError(listErrors);
            fail(sError);
        }
        return (Map<String, Object>) result.get(ExecutionContext.DATA);
    }

    protected String getContactAsQueryInput(SimpleContact contact) {
        return new StringBuilder("{")
                .append("id: \"").append(contact.getId()).append("\" ")
                .append("name: \"").append(contact.getName()).append("\" ")
                .append("age: ").append(contact.getAge())
                .append("} ").toString();
    }
    
    protected void assertDefaultFormat(SchemaType type, String fdName, String defaultFormat, boolean isDefaultFormatApplied) {
        assertThat(type, CoreMatchers.is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, CoreMatchers.is(notNullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(isDefaultFormatApplied));
        String[] format = fd.getFormat();
        assertThat(format, CoreMatchers.is(notNullValue()));
        assertThat(format.length == 2, CoreMatchers.is(notNullValue()));
        assertThat(format[0], CoreMatchers.is(defaultFormat));
    }

    protected SchemaFieldDefinition getFieldDefinition(SchemaType type, String name) {
        for (SchemaFieldDefinition fd : type.getFieldDefinitions()) {
            if (fd.getName().equals(name)) {
                return fd;
            }
        }
        return null;
    }

    protected SchemaArgument getArgument(SchemaFieldDefinition fd, String name) {
        assertThat(fd, CoreMatchers.is(notNullValue()));
        for (SchemaArgument argument : fd.getArguments()) {
            if (argument.getArgumentName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    protected void assertReturnTypeDefaultValue(SchemaType type, String fdName, String defaultValue) {
        assertThat(type, CoreMatchers.is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, CoreMatchers.is(notNullValue()));
        assertThat("Default value for " + fdName + " should be " + defaultValue +
                           " but is " + fd.getDefaultValue(), fd.getDefaultValue(), CoreMatchers.is(defaultValue));
    }

    protected void assertReturnTypeMandatory(SchemaType type, String fdName, boolean mandatory) {
        assertThat(type, CoreMatchers.is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, CoreMatchers.is(notNullValue()));
        assertThat("Return type for " + fdName + " should be mandatory=" + mandatory +
                           " but is " + fd.isReturnTypeMandatory(), fd.isReturnTypeMandatory(), CoreMatchers.is(mandatory));
    }

    protected void assertArrayReturnTypeMandatory(SchemaType type, String fdName, boolean mandatory) {
        assertThat(type, CoreMatchers.is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, CoreMatchers.is(notNullValue()));
        assertThat("Array return type for " + fdName + " should be mandatory=" + mandatory +
                           " but is " + fd.isArrayReturnTypeMandatory(), fd.isArrayReturnTypeMandatory(), CoreMatchers
                           .is(mandatory));
    }

    protected void assertReturnTypeArgumentMandatory(SchemaType type, String fdName, String argumentName, boolean mandatory) {
        assertThat(type, CoreMatchers.is(notNullValue()));
        SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
        assertThat(fd, CoreMatchers.is(notNullValue()));
        SchemaArgument argument = getArgument(fd, argumentName);
        assertThat(argument, CoreMatchers.is(notNullValue()));
        assertThat("Return type for argument " + argumentName + " should be mandatory="
                           + mandatory + " but is " + argument.isMandatory(), argument.isMandatory(), CoreMatchers.is(mandatory));
    }
    
}
