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

import org.junit.jupiter.api.Test;

import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.QUERY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for {@link Scalar} class.
 */
class DirectiveTest {

    @Test
    public void testConstructors() {
        Directive directive = new Directive("auth");
        assertThat(directive.getName(), is("auth"));
        assertThat(directive.getArguments().size(), is(0));
        assertThat(directive.getLocations().size(), is(0));

        Argument arg = new Argument("name", "String", true, null);
        directive.addArgument(arg);
        assertThat(directive.getArguments().contains(arg), is(true));

        directive.addLocation(FIELD_DEFINITION.name());
        assertThat(directive.getLocations().contains(FIELD_DEFINITION.name()), is(true));

        assertThat(directive.getSchemaAsString(), is(notNullValue()));
    }

    @Test
    public void testDirectiveWith0Argument1Location() {
        Directive directive = new Directive("directiveName");
        directive.addLocation(FIELD_DEFINITION.name());
        assertThat(directive.getSchemaAsString(), is("directive @directiveName on " + FIELD_DEFINITION.name()));
    }

    @Test
    public void testDirectiveWith1Argument1Location() {
        Argument arg = new Argument("name", "String", true, null);
        Directive directive = new Directive("directiveName");
        directive.addArgument(arg);
        directive.addLocation(FIELD_DEFINITION.name());
        assertThat(directive.getSchemaAsString(), is("directive @directiveName(name: String!) on " + FIELD_DEFINITION.name()));
    }

    @Test
    public void testDirectiveWith2Argument1Location() {
        Argument arg1 = new Argument("name", "String", true, null);
        Argument arg2 = new Argument("name1", "Int", false, null);
        Directive directive = new Directive("directiveName");
        directive.addArgument(arg1);
        directive.addArgument(arg2);
        directive.addLocation(FIELD_DEFINITION.name());
        assertThat(directive.getSchemaAsString(),
                   is("directive @directiveName(name: String!, name1: Int) on " + FIELD_DEFINITION.name()));
    }

    @Test
    public void testDirectiveWith2Argument2Location() {
        Argument arg1 = new Argument("name", "String", true, null);
        Argument arg2 = new Argument("name1", "Int", false, null);
        Directive directive = new Directive("directiveName");
        directive.addArgument(arg1);
        directive.addArgument(arg2);
        directive.addLocation(FIELD_DEFINITION.name());
        directive.addLocation(FIELD.name());
        assertThat(directive.getSchemaAsString(),
                   is("directive @directiveName(name: String!, name1: Int) on " + FIELD_DEFINITION.name() + "|" + FIELD.name()));
    }

    @Test
    public void testDirectiveWith0Argument2Location() {
        Directive directive = new Directive("directiveName");
        directive.addLocation(FIELD_DEFINITION.name());
        directive.addLocation(FIELD.name());
        assertThat(directive.getSchemaAsString(),
                   is("directive @directiveName on " + FIELD_DEFINITION.name() + "|" + FIELD.name()));
    }

    @Test

    public void testDirectiveWith0Argument3Location() {
        Directive directive = new Directive("directiveName");
        directive.addLocation(FIELD_DEFINITION.name());
        directive.addLocation(FIELD.name());
        directive.addLocation(QUERY.name());
        assertThat(directive.getSchemaAsString(),
                   is("directive @directiveName on " + FIELD_DEFINITION.name() + "|" + FIELD.name() + "|" + QUERY.name()));
    }
}