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

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.testing.junit5.Testing;
import io.helidon.validation.ValidationException;
import io.helidon.validation.Validators;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class ValidatorsTest {

    @Test
    public void testNotNull() {
        var response = Validators.validateNotNull("test");
        assertThat(response.valid(), is(true));

        response = Validators.validateNotNull(null);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("is null"));

        Validators.checkNotNull("test");
        assertThrows(ValidationException.class, () -> Validators.checkNotNull(null));
    }

    @Test
    public void testNull() {
        var response = Validators.validateNull(null);
        assertThat(response.valid(), is(true));

        response = Validators.validateNull("test");
        assertThat(response.valid(), is(false));

        Validators.checkNull(null);
        assertThrows(ValidationException.class, () -> Validators.checkNull("test"));
    }

    @Test
    public void testEmail() {
        var response = Validators.validateEmail("test@example.com");
        assertThat(response.valid(), is(true));

        response = Validators.validateEmail("invalid-email");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("does not match e-mail pattern"));

        Validators.checkEmail("test@example.com");
        assertThrows(ValidationException.class, () -> Validators.checkEmail("invalid-email"));
    }

    @Test
    public void testNotBlank() {
        var response = Validators.validateNotBlank("test");
        assertThat(response.valid(), is(true));

        response = Validators.validateNotBlank("   ");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("is blank"));

        Validators.checkNotBlank("test");
        assertThrows(ValidationException.class, () -> Validators.checkNotBlank("   "));
    }

    @Test
    public void testNotEmpty() {
        var response = Validators.validateNotEmpty("test");
        assertThat(response.valid(), is(true));

        response = Validators.validateNotEmpty("");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("is empty"));

        Validators.checkNotEmpty("test");
        assertThrows(ValidationException.class, () -> Validators.checkNotEmpty(""));
    }

    @Test
    public void testLength() {
        var response = Validators.validateLength("test", 2, 10);
        assertThat(response.valid(), is(true));

        response = Validators.validateLength("a", 2, 10);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("is shorter than 2 or longer than 10 characters"));

        response = Validators.validateLength("very long string", 2, 10);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("is shorter than 2 or longer than 10 characters"));

        Validators.checkLength("test", 2, 10);
        assertThrows(ValidationException.class, () -> Validators.checkLength("a", 2, 10));
    }

    @Test
    public void testPattern() {
        var response = Validators.validatePattern("abc123", "^[a-zA-Z0-9]+$");
        assertThat(response.valid(), is(true));

        response = Validators.validatePattern("abc-123", "^[a-zA-Z0-9]+$");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("does not match pattern \"^[a-zA-Z0-9]+$\" with flags 0"));

        Validators.checkPattern("abc123", "^[a-zA-Z0-9]+$");
        assertThrows(ValidationException.class, () -> Validators.checkPattern("abc-123", "^[a-zA-Z0-9]+$"));
    }

    @Test
    public void testNegative() {
        var response = Validators.validateNegative(-5);
        assertThat(response.valid(), is(true));

        response = Validators.validateNegative(5);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("5 is not negative"));

        Validators.checkNegative(-5);
        assertThrows(ValidationException.class, () -> Validators.checkNegative(5));
    }

    @Test
    public void testNegativeOrZero() {
        var response = Validators.validateNegativeOrZero(-5);
        assertThat(response.valid(), is(true));

        response = Validators.validateNegativeOrZero(0);
        assertThat(response.valid(), is(true));

        response = Validators.validateNegativeOrZero(5);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("5 is not negative or zero"));

        Validators.checkNegativeOrZero(-5);
        Validators.checkNegativeOrZero(0);
        assertThrows(ValidationException.class, () -> Validators.checkNegativeOrZero(5));
    }

    @Test
    public void testPositive() {
        var response = Validators.validatePositive(5);
        assertThat(response.valid(), is(true));

        response = Validators.validatePositive(-5);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("-5 is not positive"));

        Validators.checkPositive(5);
        assertThrows(ValidationException.class, () -> Validators.checkPositive(-5));
    }

    @Test
    public void testPositiveOrZero() {
        var response = Validators.validatePositiveOrZero(5);
        assertThat(response.valid(), is(true));

        response = Validators.validatePositiveOrZero(0);
        assertThat(response.valid(), is(true));

        response = Validators.validatePositiveOrZero(-5);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("-5 is not positive or zero"));

        Validators.checkPositiveOrZero(5);
        Validators.checkPositiveOrZero(0);
        assertThrows(ValidationException.class, () -> Validators.checkPositiveOrZero(-5));
    }

    @Test
    public void testNumberMin() {
        var response = Validators.validateMin(10, "5");
        assertThat(response.valid(), is(true));

        response = Validators.validateMin(3, "5");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("3 is smaller than 5"));

        Validators.checkMin(10, "5");
        assertThrows(ValidationException.class, () -> Validators.checkMin(3, "5"));
    }

    @Test
    public void testNumberMax() {
        var response = Validators.validateMax(5, "10");
        assertThat(response.valid(), is(true));

        response = Validators.validateMax(15, "10");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("15 is bigger than 10"));

        Validators.checkMax(5, "10");
        assertThrows(ValidationException.class, () -> Validators.checkMax(15, "10"));
    }

    @Test
    public void testDigits() {
        var response = Validators.validateDigits(123.45, 3, 2);
        assertThat(response.valid(), is(true));

        response = Validators.validateDigits(1234.5, 3, 2);
        assertThat(response.valid(), is(false));
        assertThat(response.message(),
                   is("1234.5 has invalid number of digits, they should be up to 3 (integer digits) and up to 2 (fractional "
                              + "digits)"));

        Validators.checkDigits(123.45, 3, 2);
        assertThrows(ValidationException.class, () -> Validators.checkDigits(1234.5, 3, 2));
    }

    @Test
    public void testIntegerMin() {
        var response = Validators.validateMin(10, 5);
        assertThat(response.valid(), is(true));

        response = Validators.validateMin(3, 5);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("3 is less than 5"));

        Validators.checkMin(10, 5);
        assertThrows(ValidationException.class, () -> Validators.checkMin(3, 5));
    }

    @Test
    public void testIntegerMax() {
        var response = Validators.validateMax(5, 10);
        assertThat(response.valid(), is(true));

        response = Validators.validateMax(15, 10);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("15 is more than 10"));

        Validators.checkMax(5, 10);
        assertThrows(ValidationException.class, () -> Validators.checkMax(15, 10));
    }

    @Test
    public void testLongMin() {
        var response = Validators.validateMin(10L, 5L);
        assertThat(response.valid(), is(true));

        response = Validators.validateMin(3L, 5L);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("3 is less than 5"));

        Validators.checkMin(10L, 5L);
        assertThrows(ValidationException.class, () -> Validators.checkMin(3L, 5L));
    }

    @Test
    public void testLongMax() {
        var response = Validators.validateMax(5L, 10L);
        assertThat(response.valid(), is(true));

        response = Validators.validateMax(15L, 10L);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("15 is more than 10"));

        Validators.checkMax(5L, 10L);
        assertThrows(ValidationException.class, () -> Validators.checkMax(15L, 10L));
    }

    @Test
    public void testTrue() {
        var response = Validators.validateTrue(true);
        assertThat(response.valid(), is(true));

        response = Validators.validateTrue(false);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("must be true"));

        Validators.checkTrue(true);
        assertThrows(ValidationException.class, () -> Validators.checkTrue(false));
    }

    @Test
    public void testFalse() {
        var response = Validators.validateFalse(false);
        assertThat(response.valid(), is(true));

        response = Validators.validateFalse(true);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("must be false"));

        Validators.checkFalse(false);
        assertThrows(ValidationException.class, () -> Validators.checkFalse(true));
    }

    @Test
    public void testCalendarFuture() {
        var futureTime = Instant.now().plusSeconds(3600);
        var response = Validators.validateCalendarFuture(futureTime);
        assertThat(response.valid(), is(true));

        var pastTime = Instant.now().minusSeconds(3600);
        response = Validators.validateCalendarFuture(pastTime);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), containsString("must be future date/time"));

        Validators.checkCalendarFuture(futureTime);
        assertThrows(ValidationException.class, () -> Validators.checkCalendarFuture(pastTime));
    }

    @Test
    public void testCalendarFutureOrPresent() {
        var futureTime = Instant.now().plusSeconds(3600);
        var response = Validators.validateCalendarFutureOrPresent(futureTime);
        assertThat(response.valid(), is(true));

        // we cannot really check "now", as the check will run in a future millisecond most likely

        var pastTime = Instant.now().minusSeconds(3600);
        response = Validators.validateCalendarFutureOrPresent(pastTime);
        assertThat(response.valid(), is(false));
        assertThat(response.violations().getFirst().invalidValue(), sameInstance(pastTime));
        assertThat(response.message(), containsString("must be present or future date/time"));

        Validators.checkCalendarFutureOrPresent(futureTime);
        assertThrows(ValidationException.class, () -> Validators.checkCalendarFutureOrPresent(pastTime));
    }

    @Test
    public void testCalendarPast() {
        var pastTime = Instant.now().minusSeconds(3600);
        var response = Validators.validateCalendarPast(pastTime);
        assertThat(response.valid(), is(true));

        var futureTime = Instant.now().plusSeconds(3600);
        response = Validators.validateCalendarPast(futureTime);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), containsString("must be past date/time"));

        Validators.checkCalendarPast(pastTime);
        assertThrows(ValidationException.class, () -> Validators.checkCalendarPast(futureTime));
    }

    @Test
    public void testCalendarPastOrPresent() {
        var pastTime = Instant.now().minusSeconds(3600);
        var response = Validators.validateCalendarPastOrPresent(pastTime);
        assertThat(response.valid(), is(true));

        var now = Instant.now();
        response = Validators.validateCalendarPastOrPresent(now);
        assertThat(response.valid(), is(true));

        var futureTime = Instant.now().plusSeconds(3600);
        response = Validators.validateCalendarPastOrPresent(futureTime);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), containsString("must be past or present date/time"));

        Validators.checkCalendarPastOrPresent(pastTime);
        Validators.checkCalendarPastOrPresent(now);
        assertThrows(ValidationException.class, () -> Validators.checkCalendarPastOrPresent(futureTime));
    }

    @Test
    public void testCollectionSize() {
        List<String> list = Arrays.asList("a", "b", "c");
        var response = Validators.validateCollectionSize(list, 2, 5);
        assertThat(response.valid(), is(true));

        response = Validators.validateCollectionSize(list, 5, 10);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("size (3) should be between 5 and 10"));

        response = Validators.validateCollectionSize(list, 1, 2);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("size (3) should be between 1 and 2"));

        Validators.checkCollectionSize(list, 2, 5);
        assertThrows(ValidationException.class, () -> Validators.checkCollectionSize(list, 5, 10));
    }

    @Test
    public void testCollectionSizeWithArray() {
        String[] array = {"a", "b", "c"};
        var response = Validators.validateCollectionSize(array, 2, 5);
        assertThat(response.valid(), is(true));

        response = Validators.validateCollectionSize(array, 5, 10);
        assertThat(response.valid(), is(false));

        Validators.checkCollectionSize(array, 2, 5);
        assertThrows(ValidationException.class, () -> Validators.checkCollectionSize(array, 5, 10));
    }

    @Test
    public void testCollectionSizeWithMap() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        var response = Validators.validateCollectionSize(map, 2, 5);
        assertThat(response.valid(), is(true));

        response = Validators.validateCollectionSize(map, 5, 10);
        assertThat(response.valid(), is(false));

        Validators.checkCollectionSize(map, 2, 5);
        assertThrows(ValidationException.class, () -> Validators.checkCollectionSize(map, 5, 10));
    }
}
