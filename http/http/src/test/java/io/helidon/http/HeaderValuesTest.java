/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeaderValuesTest {
    @Test
    void testEmptyValuesFail() {
        assertThrows(IllegalArgumentException.class, () -> HeaderValues.create("Name", List.of()));
        assertThrows(IllegalArgumentException.class, () -> HeaderValues.create("Name"));
        assertThrows(IllegalArgumentException.class, () -> HeaderValues.create(HeaderNames.CONTENT_DISPOSITION, List.of()));
        assertThrows(IllegalArgumentException.class, () -> HeaderValues.create(HeaderNames.CONTENT_DISPOSITION));
    }

    @Test
    void testConstantValue() {
        // test a value to make sure we do not have a problem with initialization
        Header connectionClose = HeaderValues.CONNECTION_CLOSE;
        assertThat(connectionClose.name().toLowerCase(Locale.ROOT), is("connection"));
        assertThat(connectionClose.headerName(), is(HeaderNames.CONNECTION));
        assertThat(connectionClose.get(), is("close"));
    }
}
