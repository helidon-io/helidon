package io.helidon.validation.tests.validation;

import java.util.List;

import io.helidon.testing.junit5.Testing;
import io.helidon.validation.ConstraintViolation;
import io.helidon.validation.ValidationException;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
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
        MatcherAssert.assertThat(violation.location(), CoreMatchers.is(ConstraintViolation.Location.RETURN_VALUE));
        assertThat(violation.message(), containsString("is blank"));
    }

    @Test
    public void testInvalidParameterMinValue() {
        var result = assertThrows(ValidationException.class, () -> service.process(new ValidatedType("good_test_value", 40)));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();
        assertThat(violation.location(), is(ConstraintViolation.Location.RECORD_COMPONENT));
        assertThat(violation.message(), containsString("is less than 42"));
    }

    @Test
    public void testInvalidParameterPattern() {
        var result = assertThrows(ValidationException.class, () -> service.process(new ValidatedType("invalid_text", 42)));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();
        assertThat(violation.location(), is(ConstraintViolation.Location.RECORD_COMPONENT));
        assertThat(violation.message(), containsString("pattern"));
    }

    @Test
    public void testInvalidParameterList() {
        var result = assertThrows(ValidationException.class,
                                  () -> service.process(List.of(new ValidatedType("invalid_text", 42))));

        var violations = result.violations();

        assertThat(violations, hasSize(1));

        var violation = violations.getFirst();
        assertThat(violation.location(), is(ConstraintViolation.Location.RECORD_COMPONENT));
        assertThat(violation.message(), containsString("pattern"));
    }
}
