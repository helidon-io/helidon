/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.dbresult;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT.Type;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Verify proper flow control handling in query processing.
 */
public class FlowControlIT {

    private static final System.Logger LOGGER = System.getLogger(FlowControlIT.class.getName());

    /**
     * Source data verification.
     */
    @Test
    public void testSourceData() {
        Stream<DbRow> rows = DB_CLIENT.execute()
                .namedQuery("select-types");
        assertThat(rows, notNullValue());
        List<DbRow> list = rows.toList();
        assertThat(list, not(empty()));
        assertThat(list.size(), equalTo(18));
        for (DbRow row : list) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            Type type = new Type(id, name);
            assertThat(name, TYPES.get(id).name().equals(name));
            LOGGER.log(Level.DEBUG, () -> String.format("Type: %s", type));
        }
    }
}
