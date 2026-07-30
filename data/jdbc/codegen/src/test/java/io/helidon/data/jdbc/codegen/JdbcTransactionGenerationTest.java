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
package io.helidon.data.jdbc.codegen;

import java.io.IOException;
import java.nio.file.Files;

import io.helidon.codegen.testing.TestCompiler;

import org.junit.jupiter.api.Test;

import static io.helidon.data.jdbc.codegen.JdbcCodegenTestSupport.compiler;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcTransactionGenerationTest {

    @Test
    void copiesMethodTransactionAnnotationsToGeneratedMethods() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("TransactionalRepository.java", """
                        package example;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.transaction.Tx;

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface TransactionalRepository {
                            @Tx.Mandatory
                            @Jdbc.Statement("select VALUE from ITEM")
                            String mandatory();

                            @Tx.New
                            @Jdbc.Statement("select VALUE from ITEM")
                            String inNewTransaction();

                            @Tx.Never
                            @Jdbc.Statement("select VALUE from ITEM")
                            String never();

                            @Tx.Required
                            @Jdbc.Statement("select VALUE from ITEM")
                            String required();

                            @Tx.Supported
                            @Jdbc.Statement("select VALUE from ITEM")
                            String supported();

                            @Tx.Unsupported
                            @Jdbc.Statement("select VALUE from ITEM")
                            String unsupported();
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/TransactionalRepository__Jdbc.java"));
        assertThat(source, containsString("@Tx.Mandatory"));
        assertThat(source, containsString("@Tx.New"));
        assertThat(source, containsString("@Tx.Never"));
        assertThat(source, containsString("@Tx.Required"));
        assertThat(source, containsString("@Tx.Supported"));
        assertThat(source, containsString("@Tx.Unsupported"));
    }
}
