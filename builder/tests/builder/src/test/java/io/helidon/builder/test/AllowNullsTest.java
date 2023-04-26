/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.TestNotNullable;
import io.helidon.builder.test.testsubjects.TestNotNullableDefault;
import io.helidon.builder.test.testsubjects.TestNullable;
import io.helidon.builder.test.testsubjects.TestNullableDefault;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class AllowNullsTest {

    @Test
    void testIt() {
        TestNullable nullable = TestNullableDefault.builder().build();
        assertThat(nullable.val(), nullValue());

        nullable = TestNullableDefault.builder().val(null).build();
        assertThat(nullable.val(), nullValue());

        nullable = TestNullableDefault.toBuilder(nullable).build();
        assertThat(nullable.val(), nullValue());

        TestNotNullable fake = () -> null;
        try {
            TestNotNullableDefault.toBuilder(fake);
            fail();
        } catch (NullPointerException e) {
            // expected
        }

        TestNotNullableDefault.Builder notNullableBuilder = TestNotNullableDefault.builder();
        assertThrows(NullPointerException.class, () -> notNullableBuilder.val(null));
        assertThrows(IllegalStateException.class, notNullableBuilder::build);
    }

}
