/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.builder.test.testsubjects.A;
import io.helidon.builder.test.testsubjects.ADefault;
import io.helidon.builder.test.testsubjects.B;
import io.helidon.builder.test.testsubjects.BDefault;
import io.helidon.builder.test.testsubjects.C;
import io.helidon.builder.test.testsubjects.CDefault;
import io.helidon.builder.test.testsubjects.T;
import io.helidon.builder.test.testsubjects.TDefault;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class UsageTest {

    @Test
    void testIt() {
        A a = ADefault.builder()
                .a("hello")
                .build();
        assertThat(a, notNullValue());
        assertThat(a.a(), is("hello"));

        B b = BDefault.builder()
                .a("hello")
                .b("helloB")
                .build();
        assertThat(b, notNullValue());
        assertThat(b.a(), is("hello"));
        assertThat(b.b(), is("helloB"));

        C c = CDefault.builder()
                .a("hello")
                .c("helloC")
                .b("helloB")
                .build();
        assertThat(c, notNullValue());
        assertThat(c.a(), is("hello"));
        assertThat(c.b(), is("helloB"));
        assertThat(c.c(), is("helloC"));

        T t = TDefault.builder()
                .a("hello")
                .t("helloT")
                .b("helloB")
                .build();
        assertThat(t, notNullValue());
        assertThat(t.a(), is("hello"));
        assertThat(t.b(), is("helloB"));
        assertThat(t.t(), is("helloT"));
    }

}
