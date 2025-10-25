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

package io.helidon.validation.tests.validation;

import java.util.List;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.testing.junit5.Testing;
import io.helidon.validation.ConstraintViolation.Location;
import io.helidon.validation.ConstraintViolation.PathElement;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class ValidationTest {
    private final ValidatedService service;

    public ValidationTest(ValidatedService service) {
        this.service = service;
    }

    @Test
    public void testValid() {
        var response = service.process(new ValidatedType("good_test_value", 42));

        assertThat(response, is("Good"));
    }

    @Test
    public void testInvalidResponse() {
        var result = assertThrows(ValidationException.class, () -> service.process(new ValidatedType("good_test_value", 43)));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();

        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD,
                                                         "process(io.helidon.validation.tests.validation.ValidatedType)"),
                                      PathElement.create(Location.RETURN_VALUE, "String")));
        assertThat(violation.message(), containsString("is blank"));
        assertThat(violation.invalidValue(), is(""));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.String.NotBlank.class)));
    }

    @Test
    public void testInvalidParameterMinValue() {
        var result = assertThrows(ValidationException.class, () -> service.process(new ValidatedType("good_test_value", 40)));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();
        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD,
                                                         "process(io.helidon.validation.tests.validation.ValidatedType)"),
                                      PathElement.create(Location.PARAMETER, "type"),
                                      PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                      PathElement.create(Location.RECORD_COMPONENT, "second")));
        assertThat(violation.message(), containsString("is less than 42"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.Integer.Min.class)));
    }

    @Test
    public void testInvalidParameterPattern() {
        var result = assertThrows(ValidationException.class, () -> service.process(new ValidatedType("invalid_text", 42)));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();
        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD,
                                                         "process(io.helidon.validation.tests.validation.ValidatedType)"),
                                      PathElement.create(Location.PARAMETER, "type"),
                                      PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                      PathElement.create(Location.RECORD_COMPONENT, "first")));
        assertThat(violation.message(), containsString("pattern"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.String.Pattern.class)));
    }

    @Test
    public void testMoreViolations() {
        var result = assertThrows(ValidationException.class, () -> service.process(new ValidatedType("invalid_text", 40)));

        var violations = result.violations();

        assertThat(violations, hasSize(2));

        var violation = violations.get(0);
        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD,
                                                         "process(io.helidon.validation.tests.validation.ValidatedType)"),
                                      PathElement.create(Location.PARAMETER, "type"),
                                      PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                      PathElement.create(Location.RECORD_COMPONENT, "first")));
        assertThat(violation.message(), containsString("pattern"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.String.Pattern.class)));

        violation = violations.get(1);
        location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD,
                                                         "process(io.helidon.validation.tests.validation.ValidatedType)"),
                                      PathElement.create(Location.PARAMETER, "type"),
                                      PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                      PathElement.create(Location.RECORD_COMPONENT, "second")));
        assertThat(violation.message(), containsString("is less than 42"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.Integer.Min.class)));
    }

    @Test
    public void testInvalidParameterList() {
        var result = assertThrows(ValidationException.class,
                                  () -> service.process(List.of(new ValidatedType("invalid_text", 42))));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();
        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD, "process(java.util.List)"),
                                      PathElement.create(Location.PARAMETER, "list"),
                                      PathElement.create(Location.ELEMENT, "element"),
                                      PathElement.create(Location.TYPE, ValidatedType.class.getName()),
                                      PathElement.create(Location.RECORD_COMPONENT, "first")));
        assertThat(violation.message(), is("does not match pattern \".*test.*\" with flags 0"));
        assertThat(violation.invalidValue(), is("invalid_text"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.String.Pattern.class)));
    }

    @Test
    public void testCustomGroup() {
        // make sure good values pass
        service.process("valid", "good");

        var t = assertThrows(ValidationException.class, () -> service.process("", "good"));
        var violations = t.violations();

        assertThat(violations, hasSize(1));
        var violation = violations.getFirst();
        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD, "process(java.lang.String,java.lang.String)"),
                                      PathElement.create(Location.PARAMETER, "validateCustomGroup")));
        assertThat(violation.message(), containsString("is blank"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.String.NotBlank.class)));

        t = assertThrows(ValidationException.class, () -> service.process("\t\n", "good"));
        violations = t.violations();
        assertThat(violations, hasSize(1));
        violation = violations.getFirst();
        location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD, "process(java.lang.String,java.lang.String)"),
                                      PathElement.create(Location.PARAMETER, "validateCustomGroup")));
        assertThat(violation.message(), containsString("is blank"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.String.NotBlank.class)));

        t = assertThrows(ValidationException.class, () -> service.process(null, "good"));
        violations = t.violations();
        assertThat(violations, hasSize(1));
        violation = violations.getFirst();
        location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD, "process(java.lang.String,java.lang.String)"),
                                      PathElement.create(Location.PARAMETER, "validateCustomGroup")));
        assertThat(violation.message(), containsString("is null"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.NotNull.class)));
    }

    @Test
    public void testCustomConstraint() {
        // make sure good values pass
        service.process("valid", "good");

        var t = assertThrows(ValidationException.class, () -> service.process("valid", "bad"));
        var violations = t.violations();

        assertThat(violations, hasSize(1));
        var violation = violations.getFirst();
        List<PathElement> location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD, "process(java.lang.String,java.lang.String)"),
                                      PathElement.create(Location.PARAMETER, "validateCustomConstraint")));
        assertThat(violation.message(), containsString("Must be \"good\" string"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(CustomConstraint.class)));

        t = assertThrows(ValidationException.class, () -> service.process("valid", null));
        violations = t.violations();
        assertThat(violations, hasSize(1));
        violation = violations.getFirst();
        location = violation.location();
        assertThat(location, contains(PathElement.create(Location.TYPE, ValidatedService.class.getName()),
                                      PathElement.create(Location.METHOD, "process(java.lang.String,java.lang.String)"),
                                      PathElement.create(Location.PARAMETER, "validateCustomConstraint")));
        assertThat(violation.message(), containsString("is null"));
        assertThat(violation.rootObject(), is(Optional.of(service)));
        assertThat(violation.rootType(), sameInstance(ValidatedService.class));
        assertThat(violation.annotation().typeName(), is(TypeName.create(Validation.NotNull.class)));


    }
}
