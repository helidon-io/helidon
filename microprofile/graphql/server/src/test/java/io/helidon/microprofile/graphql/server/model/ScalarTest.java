/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.model;

import java.util.Date;

import graphql.scalars.ExtendedScalars;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests for {@link Scalar} class.
 */
class ScalarTest {

    @Test
    public void testConstructors() {
        Scalar scalar = new Scalar("myName", Integer.class.getName(), ExtendedScalars.DateTime);
        assertThat(scalar.getName(), is("myName"));
        assertThat(scalar.getActualClass(), is(Integer.class.getName()));
        assertThat(scalar.getGraphQLScalarType().equals(ExtendedScalars.DateTime), is(true));
    }

    @Test
    public void testGetScalarAsString() {
        assertThat(new Scalar("Test", Date.class.getName(), ExtendedScalars.DateTime).getSchemaAsString(), is("scalar Test"));
        assertThat(new Scalar("Date", Date.class.getName(), ExtendedScalars.DateTime).getSchemaAsString(), is("scalar Date"));
    }
}