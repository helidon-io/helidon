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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcMapperServiceGenerationTest {
    @Test
    void generatesMarkerAndExplicitMapperServiceDependencies() throws IOException {
        TestCompiler.Result result = compiler()
                .addSource("ContactRepository.java", """
                        package example;

                        import java.util.Optional;

                        import io.helidon.data.Data;
                        import io.helidon.data.jdbc.Jdbc;
                        import io.helidon.data.jdbc.JdbcClient;
                        import io.helidon.service.registry.Service;

                        record ContactPhone(long contactId, String phoneLabel) {
                        }

                        final class MapperDependency {
                        }

                        @Service.Singleton
                        final class ContactPhoneMapper implements JdbcClient.RowMapper<ContactPhone> {
                            private final MapperDependency dependency;

                            ContactPhoneMapper(MapperDependency dependency) {
                                this.dependency = dependency;
                            }

                            @Override
                            public ContactPhone map(JdbcClient.Row row) {
                                return new ContactPhone(row.required("contactId", Long.class),
                                                        row.required("phoneLabel", String.class));
                            }
                        }

                        @Service.Singleton
                        final class AlternatePhoneMapper implements JdbcClient.RowMapper<ContactPhone> {
                            @Override
                            public ContactPhone map(JdbcClient.Row row) {
                                return new ContactPhone(row.required("contactId", Long.class),
                                                        row.required("phoneLabel", String.class));
                            }
                        }

                        @Data.Repository
                        @Data.Provider("jdbc")
                        interface ContactRepository {
                            @Jdbc.Statement("select CONTACT_ID as contactId, PHONE as phoneLabel from PHONE")
                            @Jdbc.RowMapper
                            ContactPhone mappedByContract();

                            @Jdbc.Statement("select CONTACT_ID as contactId, PHONE as phoneLabel from PHONE")
                            @Jdbc.RowMapper()
                            Optional<ContactPhone> optionalMappedByContract();

                            @Jdbc.Statement("select CONTACT_ID as contactId, PHONE as phoneLabel from PHONE")
                            @Jdbc.RowMapper(ContactPhoneMapper.class)
                            ContactPhone mappedExplicitly();

                            @Jdbc.Statement("select CONTACT_ID as contactId, PHONE as phoneLabel from PHONE")
                            @Jdbc.RowMapper(AlternatePhoneMapper.class)
                            ContactPhone mappedByAlternateService();

                            @Jdbc.Statement("insert into PHONE(PHONE) values (:phone)")
                            @Jdbc.GeneratedKeys({"CONTACT_ID", "PHONE"})
                            @Jdbc.RowMapper
                            ContactPhone insertAndMapKeys(String phone);
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join("\n", result.diagnostics()), result.success(), is(true));
        String source = Files.readString(result.sourceOutput().resolve("example/ContactRepository__Jdbc.java"));
        assertThat(source, containsString("private final JdbcClient.RowMapper<ContactPhone> contactPhoneRowMapper;"));
        assertThat(source, containsString("private final JdbcClient.RowMapper<ContactPhone> contactPhoneMapper;"));
        assertThat(source, containsString("private final JdbcClient.RowMapper<ContactPhone> alternatePhoneMapper;"));
        assertThat(source, containsString("JdbcClient.RowMapper<ContactPhone> contactPhoneRowMapper"));
        assertThat(source, containsString("ContactPhoneMapper contactPhoneMapper"));
        assertThat(source, containsString("AlternatePhoneMapper alternatePhoneMapper"));
        assertThat(source, containsString(".map(contactPhoneRowMapper).one();"));
        assertThat(source, containsString(".map(contactPhoneRowMapper).optional();"));
        assertThat(source, containsString(".map(contactPhoneMapper).one();"));
        assertThat(source, containsString(".map(alternatePhoneMapper).one();"));
        assertThat(source, containsString(".generatedKeys()"
                                                 + ".addColumn(\"CONTACT_ID\")"
                                                 + ".addColumn(\"PHONE\")"
                                                 + ".map(contactPhoneRowMapper).one();"));
        assertThat(source, not(containsString("new ContactPhoneMapper()")));
        assertThat(source, not(containsString("new AlternatePhoneMapper()")));
        assertThat(source, not(containsString("contactPhoneRowMapper2")));
    }
}
