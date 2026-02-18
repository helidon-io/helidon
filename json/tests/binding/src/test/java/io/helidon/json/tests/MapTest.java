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

package io.helidon.json.tests;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import io.helidon.common.GenericType;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
public class MapTest {

    private final JsonBinding jsonBinding;

    MapTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testMapSerialization() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");

        String expected = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}";
        assertThat(jsonBinding.serialize(map), is(expected));
    }

    @Test
    public void testMapDeserialization() {
        String json = "{\"key1\":\"value1\",\"key2\":\"value2\",\"key3\":\"value3\"}";

        GenericType<Map<String, String>> type = new GenericType<>() { };
        Map<String, String> map = jsonBinding.deserialize(json, type);

        assertThat(map, notNullValue());
        assertThat(map, instanceOf(HashMap.class));
        assertThat(map.size(), is(3));
        assertThat(map, hasEntry("key1", "value1"));
        assertThat(map, hasEntry("key2", "value2"));
        assertThat(map, hasEntry("key3", "value3"));
    }

    @Test
    public void testMapSerializationWithNulls() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", null);
        map.put("key2", null);
        map.put("key3", null);

        String expected = "{\"key1\":null,\"key2\":null,\"key3\":null}";
        assertThat(jsonBinding.serialize(map), is(expected));
    }

    @Test
    public void testMapDeserializationWithNulls() {
        String json = "{\"key1\":null,\"key2\":null,\"key3\":null}";

        GenericType<Map<String, String>> type = new GenericType<>() { };
        Map<String, String> map = jsonBinding.deserialize(json, type);

        assertThat(map, notNullValue());
        assertThat(map, instanceOf(HashMap.class));
        assertThat(map.size(), is(3));
        assertThat(map, hasEntry("key1", null));
        assertThat(map, hasEntry("key2", null));
        assertThat(map, hasEntry("key3", null));
    }

    @Test
    public void testMapWithIntegerKeys() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "one");
        map.put(2, "two");
        map.put(42, "forty-two");

        String expected = "{\"1\":\"one\",\"2\":\"two\",\"42\":\"forty-two\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Integer, String>> type = new GenericType<>() { };
        Map<Integer, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(3));
        assertThat(deserialized, hasEntry(1, "one"));
        assertThat(deserialized, hasEntry(2, "two"));
        assertThat(deserialized, hasEntry(42, "forty-two"));
    }

    @Test
    public void testMapWithLongKeys() {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(123L, "small");
        map.put(9999999999L, "large");

        String expected = "{\"123\":\"small\",\"9999999999\":\"large\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Long, String>> type = new GenericType<>() { };
        Map<Long, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(123L, "small"));
        assertThat(deserialized, hasEntry(9999999999L, "large"));
    }

    @Test
    public void testMapWithDoubleKeys() {
        Map<Double, String> map = new LinkedHashMap<>();
        map.put(1.5, "one-point-five");
        map.put(3.14159, "pi");

        String expected = "{\"1.5\":\"one-point-five\",\"3.14159\":\"pi\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Double, String>> type = new GenericType<>() { };
        Map<Double, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(1.5, "one-point-five"));
        assertThat(deserialized, hasEntry(3.14159, "pi"));
    }

    @Test
    public void testMapWithBooleanKeys() {
        Map<Boolean, String> map = new LinkedHashMap<>();
        map.put(true, "yes");
        map.put(false, "no");

        String expected = "{\"true\":\"yes\",\"false\":\"no\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Boolean, String>> type = new GenericType<>() { };
        Map<Boolean, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(true, "yes"));
        assertThat(deserialized, hasEntry(false, "no"));
    }

    @Test
    public void testMapWithShortKeys() {
        Map<Short, String> map = new LinkedHashMap<>();
        map.put((short) 100, "hundred");
        map.put((short) 200, "two-hundred");

        String expected = "{\"100\":\"hundred\",\"200\":\"two-hundred\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Short, String>> type = new GenericType<>() { };
        Map<Short, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry((short) 100, "hundred"));
        assertThat(deserialized, hasEntry((short) 200, "two-hundred"));
    }

    @Test
    public void testMapWithByteKeys() {
        Map<Byte, String> map = new LinkedHashMap<>();
        map.put((byte) 10, "ten");
        map.put((byte) 20, "twenty");

        String expected = "{\"10\":\"ten\",\"20\":\"twenty\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Byte, String>> type = new GenericType<>() { };
        Map<Byte, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry((byte) 10, "ten"));
        assertThat(deserialized, hasEntry((byte) 20, "twenty"));
    }

    @Test
    public void testMapWithFloatKeys() {
        Map<Float, String> map = new LinkedHashMap<>();
        map.put(2.5f, "two-point-five");
        map.put(7.77f, "seven-point-seven-seven");

        String expected = "{\"2.5\":\"two-point-five\",\"7.77\":\"seven-point-seven-seven\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Float, String>> type = new GenericType<>() { };
        Map<Float, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(2.5f, "two-point-five"));
        assertThat(deserialized, hasEntry(7.77f, "seven-point-seven-seven"));
    }

    @Test
    public void testMapWithCharacterKeys() {
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('A', "alpha");
        map.put('B', "beta");
        map.put('1', "one");

        String expected = "{\"A\":\"alpha\",\"B\":\"beta\",\"1\":\"one\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Character, String>> type = new GenericType<>() { };
        Map<Character, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(3));
        assertThat(deserialized, hasEntry('A', "alpha"));
        assertThat(deserialized, hasEntry('B', "beta"));
        assertThat(deserialized, hasEntry('1', "one"));
    }

    @Test
    public void testMapWithBigIntegerKeys() {
        Map<BigInteger, String> map = new LinkedHashMap<>();
        map.put(BigInteger.valueOf(123456789), "big-number");
        map.put(BigInteger.valueOf(-999999999), "negative-big");

        String expected = "{\"123456789\":\"big-number\",\"-999999999\":\"negative-big\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<BigInteger, String>> type = new GenericType<>() { };
        Map<BigInteger, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(BigInteger.valueOf(123456789), "big-number"));
        assertThat(deserialized, hasEntry(BigInteger.valueOf(-999999999), "negative-big"));
    }

    @Test
    public void testMapWithBigDecimalKeys() {
        Map<BigDecimal, String> map = new LinkedHashMap<>();
        map.put(BigDecimal.valueOf(123.456), "decimal");
        map.put(BigDecimal.valueOf(-999.999), "negative-decimal");

        String expected = "{\"123.456\":\"decimal\",\"-999.999\":\"negative-decimal\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<BigDecimal, String>> type = new GenericType<>() { };
        Map<BigDecimal, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(BigDecimal.valueOf(123.456), "decimal"));
        assertThat(deserialized, hasEntry(BigDecimal.valueOf(-999.999), "negative-decimal"));
    }

    @Test
    public void testMapWithDateKeys() {
        Map<Date, String> map = new LinkedHashMap<>();
        Date date1 = new Date(1640995200000L); // 2022-01-01
        Date date2 = new Date(1672531200000L); // 2023-01-01
        map.put(date1, "new-year-2022");
        map.put(date2, "new-year-2023");

        String expected = "{\"2022-01-01T00:00:00Z\":\"new-year-2022\",\"2023-01-01T00:00:00Z\":\"new-year-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Date, String>> type = new GenericType<>() { };
        Map<Date, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(date1, "new-year-2022"));
        assertThat(deserialized, hasEntry(date2, "new-year-2023"));
    }

    @Test
    public void testMapWithInstantKeys() {
        Map<Instant, String> map = new LinkedHashMap<>();
        Instant instant1 = Instant.parse("2022-01-01T00:00:00Z");
        Instant instant2 = Instant.parse("2023-01-01T00:00:00Z");
        map.put(instant1, "instant-2022");
        map.put(instant2, "instant-2023");

        String expected = "{\"2022-01-01T00:00:00Z\":\"instant-2022\",\"2023-01-01T00:00:00Z\":\"instant-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Instant, String>> type = new GenericType<>() { };
        Map<Instant, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(instant1, "instant-2022"));
        assertThat(deserialized, hasEntry(instant2, "instant-2023"));
    }

    @Test
    public void testMapWithUuidKeys() {
        Map<UUID, String> map = new LinkedHashMap<>();
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID uuid2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        map.put(uuid1, "uuid-one");
        map.put(uuid2, "uuid-two");

        String expected = "{\"550e8400-e29b-41d4-a716-446655440000\":\"uuid-one\",\"550e8400-e29b-41d4-a716-446655440001\":\"uuid-two\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<UUID, String>> type = new GenericType<>() { };
        Map<UUID, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(uuid1, "uuid-one"));
        assertThat(deserialized, hasEntry(uuid2, "uuid-two"));
    }

    @Test
    public void testMapWithLocalDateKeys() {
        Map<LocalDate, String> map = new LinkedHashMap<>();
        LocalDate date1 = LocalDate.of(2022, 1, 1);
        LocalDate date2 = LocalDate.of(2023, 1, 1);
        map.put(date1, "local-date-2022");
        map.put(date2, "local-date-2023");

        String expected = "{\"2022-01-01\":\"local-date-2022\",\"2023-01-01\":\"local-date-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<LocalDate, String>> type = new GenericType<>() { };
        Map<LocalDate, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(date1, "local-date-2022"));
        assertThat(deserialized, hasEntry(date2, "local-date-2023"));
    }

    @Test
    public void testMapWithPeriodKeys() {
        Map<Period, String> map = new LinkedHashMap<>();
        Period period1 = Period.ofDays(7);
        Period period2 = Period.ofMonths(1);
        map.put(period1, "week");
        map.put(period2, "month");

        String expected = "{\"P7D\":\"week\",\"P1M\":\"month\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Period, String>> type = new GenericType<>() { };
        Map<Period, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(period1, "week"));
        assertThat(deserialized, hasEntry(period2, "month"));
    }

    @Test
    public void testMapWithLocalDateTimeKeys() {
        Map<LocalDateTime, String> map = new LinkedHashMap<>();
        LocalDateTime dt1 = LocalDateTime.of(2022, 1, 1, 12, 0, 0);
        LocalDateTime dt2 = LocalDateTime.of(2023, 1, 1, 12, 0, 0);
        map.put(dt1, "datetime-2022");
        map.put(dt2, "datetime-2023");

        String expected = "{\"2022-01-01T12:00\":\"datetime-2022\",\"2023-01-01T12:00\":\"datetime-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<LocalDateTime, String>> type = new GenericType<>() { };
        Map<LocalDateTime, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(dt1, "datetime-2022"));
        assertThat(deserialized, hasEntry(dt2, "datetime-2023"));
    }

    @Test
    public void testMapWithZonedDateTimeKeys() {
        Map<ZonedDateTime, String> map = new LinkedHashMap<>();
        ZonedDateTime zdt1 = ZonedDateTime.of(2022, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime zdt2 = ZonedDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC"));
        map.put(zdt1, "zoned-2022");
        map.put(zdt2, "zoned-2023");

        String expected = "{\"2022-01-01T12:00Z[UTC]\":\"zoned-2022\",\"2023-01-01T12:00Z[UTC]\":\"zoned-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<ZonedDateTime, String>> type = new GenericType<>() { };
        Map<ZonedDateTime, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(zdt1, "zoned-2022"));
        assertThat(deserialized, hasEntry(zdt2, "zoned-2023"));
    }

    @Test
    public void testMapWithOffsetDateTimeKeys() {
        Map<OffsetDateTime, String> map = new LinkedHashMap<>();
        OffsetDateTime odt1 = OffsetDateTime.of(2022, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime odt2 = OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        map.put(odt1, "offset-2022");
        map.put(odt2, "offset-2023");

        String expected = "{\"2022-01-01T12:00Z\":\"offset-2022\",\"2023-01-01T12:00Z\":\"offset-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<OffsetDateTime, String>> type = new GenericType<>() { };
        Map<OffsetDateTime, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(odt1, "offset-2022"));
        assertThat(deserialized, hasEntry(odt2, "offset-2023"));
    }

    @Test
    public void testMapWithLocalTimeKeys() {
        Map<LocalTime, String> map = new LinkedHashMap<>();
        LocalTime time1 = LocalTime.of(9, 30, 0);
        LocalTime time2 = LocalTime.of(15, 45, 0);
        map.put(time1, "morning");
        map.put(time2, "afternoon");

        String expected = "{\"09:30\":\"morning\",\"15:45\":\"afternoon\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<LocalTime, String>> type = new GenericType<>() { };
        Map<LocalTime, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
        assertThat(deserialized, hasEntry(time1, "morning"));
        assertThat(deserialized, hasEntry(time2, "afternoon"));
    }

    @Test
    public void testMapWithCalendarKeys() {
        Map<Calendar, String> map = new LinkedHashMap<>();
        GregorianCalendar cal1 = new GregorianCalendar(2022, Calendar.JANUARY, 1);
        GregorianCalendar cal2 = new GregorianCalendar(2023, Calendar.JANUARY, 1);
        cal1.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal2.setTimeZone(TimeZone.getTimeZone("UTC"));
        map.put(cal1, "calendar-2022");
        map.put(cal2, "calendar-2023");

        String expected = "{\"2022-01-01T00:00:00Z[UTC]\":\"calendar-2022\",\"2023-01-01T00:00:00Z[UTC]\":\"calendar-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<Calendar, String>> type = new GenericType<>() { };
        Map<Calendar, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
    }

    @Test
    public void testMapWithGregorianCalendarKeys() {
        Map<GregorianCalendar, String> map = new LinkedHashMap<>();
        GregorianCalendar gcal1 = new GregorianCalendar(2022, Calendar.JANUARY, 1);
        GregorianCalendar gcal2 = new GregorianCalendar(2023, Calendar.JANUARY, 1);
        gcal1.setTimeZone(TimeZone.getTimeZone("UTC"));
        gcal2.setTimeZone(TimeZone.getTimeZone("UTC"));
        map.put(gcal1, "gregorian-2022");
        map.put(gcal2, "gregorian-2023");

        String expected = "{\"2022-01-01T00:00:00Z[UTC]\":\"gregorian-2022\",\"2023-01-01T00:00:00Z[UTC]\":\"gregorian-2023\"}";
        assertThat(jsonBinding.serialize(map), is(expected));

        GenericType<Map<GregorianCalendar, String>> type = new GenericType<>() { };
        Map<GregorianCalendar, String> deserialized = jsonBinding.deserialize(expected, type);

        assertThat(deserialized, notNullValue());
        assertThat(deserialized.size(), is(2));
    }

}
