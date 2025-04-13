/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.oracle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import io.helidon.common.Weight;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.anyString;

@Service.Singleton
@Service.Named("*")
@Weight(85.0D)
public class MockDatasourceFactory implements Service.ServicesFactory<DataSource> {
    @Override
    public List<Service.QualifiedInstance<DataSource>> services() {

        return List.of(
                Service.QualifiedInstance.create(createMockProxy("defaultDs"), Qualifier.createNamed("@default")),
                Service.QualifiedInstance.create(createMockProxy("customDs"), Qualifier.createNamed("customDs"))
        );
    }

    private static DataSource createMockProxy(String name) {
        var m = Mockito.mock(DataSource.class);
        var c = Mockito.mock(Connection.class);
        var s = Mockito.mock(PreparedStatement.class);
        try {

            Mockito.when(m.toString()).thenReturn(name);
            Mockito.when(s.execute(anyString())).thenAnswer((Answer<Void>) i -> {
                throw new SQLException(i.getArguments()[0].toString());
            });
            Mockito.when(c.createStatement()).thenReturn(s);
            Mockito.when(c.prepareStatement(anyString())).thenAnswer(i -> {
                throw new SQLException(i.getArguments()[0].toString());
            });
            Mockito.when(m.getConnection()).thenReturn(c);
            return m;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
