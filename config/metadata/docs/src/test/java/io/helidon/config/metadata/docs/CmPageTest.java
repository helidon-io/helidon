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

import io.helidon.config.metadata.docs.CmPage.Row;
import io.helidon.config.metadata.docs.CmPage.Table;
import io.helidon.config.metadata.docs.CmPage.Tables;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link CmPage}.
 */
class CmPageTest {

    @Test
    void testTablesIsEmpty() {
        var empty = new Table(List.of(), false, false);
        var nonEmpty = new Table(List.of(new Row("key", "", "", "description", "", "")), false, false);

        assertThat(new Tables(empty, empty, empty).isEmpty(), is(true));
        assertThat(new Tables(nonEmpty, empty, empty).isEmpty(), is(false));
        assertThat(new Tables(empty, nonEmpty, empty).isEmpty(), is(false));
        assertThat(new Tables(empty, empty, nonEmpty).isEmpty(), is(false));
    }

    @Test
    void testRowTooltips() {
        var noTooltip = new Row("key", "0123456789", "0123456789", "description", "", "");
        var typeTooltip = new Row("key", "01234567890", "0123456789", "description", "", "");
        var defaultTooltip = new Row("key", "0123456789", "01234567890", "description", "", "");

        assertThat(noTooltip.hasTypeTooltip(), is(false));
        assertThat(noTooltip.hasDefaultTooltip(), is(false));
        assertThat(typeTooltip.hasTypeTooltip(), is(true));
        assertThat(typeTooltip.hasDefaultTooltip(), is(false));
        assertThat(defaultTooltip.hasTypeTooltip(), is(false));
        assertThat(defaultTooltip.hasDefaultTooltip(), is(true));
    }
}
