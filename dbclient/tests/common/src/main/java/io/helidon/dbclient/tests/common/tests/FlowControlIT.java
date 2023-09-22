/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient.tests.common.tests;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.tests.common.model.Kind;

import org.junit.jupiter.api.Test;

import static io.helidon.dbclient.tests.common.model.Kind.KINDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Verify proper flow control handling in query processing.
 */
public abstract class FlowControlIT {

    private static final System.Logger LOGGER = System.getLogger(FlowControlIT.class.getName());

    private final DbClient dbClient;

    public FlowControlIT(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * Source data verification.
     */
    @Test
    public void testSourceData() {
        Stream<DbRow> rows = dbClient.execute()
                .namedQuery("select-types");
        assertThat(rows, notNullValue());
        List<DbRow> list = rows.toList();
        assertThat(list, not(empty()));
        assertThat(list.size(), equalTo(18));
        for (DbRow row : list) {
            Integer id = row.column(1).get(Integer.class);
            String name = row.column(2).get(String.class);
            Kind type = new Kind(id, name);
            assertThat(name, KINDS.get(id).name().equals(name));
            LOGGER.log(Level.DEBUG, () -> String.format("Type: %s", type));
        }
    }
}
