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

package io.helidon.config.metadata.docs;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link CmPage}.
 */
class CmPageTest {

    @Test
    void testTablesIsEmpty() {
        var empty = table();
        var nonEmpty = table(new CmPage.Row("key", "", "", "description", "", ""));

        assertThat(new CmPage.Tables(empty, empty, empty).isEmpty(), is(true));
        assertThat(new CmPage.Tables(nonEmpty, empty, empty).isEmpty(), is(false));
        assertThat(new CmPage.Tables(empty, nonEmpty, empty).isEmpty(), is(false));
        assertThat(new CmPage.Tables(empty, empty, nonEmpty).isEmpty(), is(false));
    }

    static CmPage.Table table(CmPage.Row... rows) {
        return new CmPage.Table(List.of(rows), false, false);
    }
}
