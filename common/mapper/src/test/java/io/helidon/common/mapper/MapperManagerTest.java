/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import io.helidon.common.GenericType;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.mapper.spi.MapperProvider;
import io.helidon.common.serviceloader.HelidonServiceLoader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link MapperManager}.
 */
class MapperManagerTest {
    private static MapperManager mm;

    @BeforeAll
    static void createMapperManager() {
        mm = MapperManager.builder(HelidonServiceLoader.builder(ServiceLoader.load(MapperProvider.class))
                                           .useSystemServiceLoader(false)
                                           .build())
                .useBuiltIn(true)
                .build();
    }

    @Test
    void testBuiltIns() throws MalformedURLException {
        test(mm, "true", Boolean.class, true);
        test(mm, "true", Boolean.class, true);
        test(mm, "1", Boolean.class, true);
        test(mm, "yes", Boolean.class, true);
        test(mm, "y", Boolean.class, true);
        test(mm, "on", Boolean.class, true);
        test(mm, "false", Boolean.class, false);
        test(mm, "random", Boolean.class, false);
        test(mm, "42", Byte.class, (byte) 42);
        test(mm, "42", Short.class, (short) 42);
        test(mm, "42", Integer.class, 42);
        test(mm, "42", Long.class, 42L);
        test(mm, "42", Float.class, 42f);
        test(mm, "42", Double.class, 42.0);
        test(mm, "a", Character.class, 'a');
        test(mm, "a", Character.TYPE, 'a');
        test(mm, MapperManager.class.getName(), Class.class, MapperManager.class);
        test(mm, "42", BigDecimal.class, new BigDecimal("42"));
        test(mm, "42", BigInteger.class, new BigInteger("42"));
        test(mm, "pom.xml", File.class, new File("pom.xml"));
        test(mm, "pom.xml", Path.class, Paths.get("pom.xml"));
        test(mm, "UTF-8", Charset.class, StandardCharsets.UTF_8);
        test(mm, "http://localhost:8080/path", URI.class, URI.create("http://localhost:8080/path"));
        test(mm, "http://localhost:8080/path", URL.class, URI.create("http://localhost:8080/path").toURL());
        test(mm, "+1000", ZoneOffset.class, ZoneOffset.ofHours(10));
        test(mm, "Europe/Prague", ZoneId.class, ZoneId.of("Europe/Prague"));
        UUID uuid = UUID.randomUUID();
        test(mm, uuid.toString(), UUID.class, uuid);
        Duration duration = Duration.ofMinutes(79);
        test(mm, duration.toString(), Duration.class, duration);
        Period period = Period.ofDays(27);
        test(mm, period.toString(), Period.class, period);

        // pattern does not have equals implemented
        String regex = ".*\\d.*?";
        Pattern pattern = mm.map(regex, String.class, Pattern.class);
        assertThat("From String to Pattern", pattern.pattern(), is(regex));
    }

    @Test
    void testAssignableTypeWorksWithoutMapper() {
        String myString = "some string";
        CharSequence result = mm.map(myString, String.class, CharSequence.class);
        assertThat(result, is(myString));

        BigInteger bigInt = new BigInteger("1025");
        Number number = mm.map(bigInt, BigInteger.class, Number.class);
        assertThat(number, is(bigInt));
    }

    @Test
    void testWrongTypes() {
        assertThrows(MapperException.class, () -> mm.map("two", String.class, Character.class));
        assertThrows(MapperException.class, () -> mm.map("wrong.class.native", String.class, Class.class));
        assertThrows(MapperException.class, () -> mm.map("wrong url ", String.class, URL.class));
    }

    @Test
    void testDefaultAsString() {
        int number = 478;
        String result = mm.map(number, Integer.TYPE, String.class);
        assertThat(result, is("478"));
    }

    @Test
    void testYearMonth() {
        YearMonth it = mm.map("2010-12", String.class, YearMonth.class);
        assertYearMonth(it);
    }

    @Test
    void testYearMonthPattern() {
        YearMonth it = withPattern("dd.MM.yyyy",
                                   () -> mm.map("24.12.2010", String.class, YearMonth.class));
        assertYearMonth(it);
    }

    @Test
    void testYearMonthFormatter() {
        YearMonth it = withFormatter("dd.MM.yyyy",
                                     () -> mm.map("24.12.2010", String.class, YearMonth.class));
        assertYearMonth(it);
    }

    @Test
    void testOffsetTime() {
        OffsetTime it = mm.map("14:28:45+01:00", String.class, OffsetTime.class);
        assertOffsetTime(it);
    }

    @Test
    void testOffsetTimePattern() {
        OffsetTime it = withPattern("HH.mm.ss.x",
                                    () -> mm.map("14.28.45.+01", String.class, OffsetTime.class));
        assertOffsetTime(it);
    }

    @Test
    void testOffsetTimeFormatter() {
        OffsetTime it = withFormatter("HH.mm.ss.x",
                                      () -> mm.map("14.28.45.+01", String.class, OffsetTime.class));
        assertOffsetTime(it);
    }

    @Test
    void testOffsetDateTime() {
        OffsetDateTime it = mm.map("2010-12-24T14:28:45+01:00", String.class, OffsetDateTime.class);
        assertOffsetDateTime(it);
    }

    @Test
    void testOffsetDateTimePattern() {
        OffsetDateTime it = withPattern("dd.MM.yyyy.HH.mm.ss.x",
                                        () -> mm.map("24.12.2010.14.28.45.+01", String.class, OffsetDateTime.class));
        assertOffsetDateTime(it);
    }

    @Test
    void testOffsetDateTimeFormatter() {
        OffsetDateTime it = withFormatter("dd.MM.yyyy.HH.mm.ss.x",
                                          () -> mm.map("24.12.2010.14.28.45.+01", String.class, OffsetDateTime.class));
        assertOffsetDateTime(it);
    }

    @Test
    void testInstant() {
        Instant it = mm.map("2010-12-24T14:28:45Z", String.class, Instant.class);
        assertInstant(it);
    }

    @Test
    void testInstantPattern() {
        Instant it = withPattern("dd.MM.yyyy.HH.mm.ss.X",
                                 () -> mm.map("24.12.2010.14.28.45.Z", String.class, Instant.class));
        assertInstant(it);
    }

    @Test
    void testInstantFormatter() {
        Instant it = withFormatter("dd.MM.yyyy.HH.mm.ss.X",
                                   () -> mm.map("24.12.2010.14.28.45.Z", String.class, Instant.class));
        assertInstant(it);
    }

    @Test
    void testZonedDateTime() {
        ZonedDateTime it = mm.map("2010-12-24T14:28:45+01:00[Europe/Prague]", String.class, ZonedDateTime.class);
        assertZonedDateTime(it);
    }

    @Test
    void testZonedDateTimePattern() {
        ZonedDateTime it = withPattern("dd.MM.yyyy.HH.mm.ss.x.VV",
                                       () -> mm.map("24.12.2010.14.28.45.+01.Europe/Prague", String.class, ZonedDateTime.class));
        assertZonedDateTime(it);
    }

    @Test
    void testZonedDateTimeFormatter() {
        ZonedDateTime it = withFormatter("dd.MM.yyyy.HH.mm.ss.x.VV",
                                         () -> mm.map("24.12.2010.14.28.45.+01.Europe/Prague",
                                                      String.class,
                                                      ZonedDateTime.class));
        assertZonedDateTime(it);
    }

    @Test
    void testLocalTime() {
        LocalTime it = mm.map("14:28:45", String.class, LocalTime.class);
        assertLocalTime(it);
    }

    @Test
    void testLocalTimePattern() {
        LocalTime it = withPattern("HH.mm.ss",
                                   () -> mm.map("14.28.45", String.class, LocalTime.class));
        assertLocalTime(it);
    }

    @Test
    void testLocalTimeFormatter() {
        LocalTime it = withFormatter("HH.mm.ss",
                                     () -> mm.map("14.28.45", String.class, LocalTime.class));
        assertLocalTime(it);
    }

    @Test
    void testLocalDateTime() {
        LocalDateTime it = mm.map("2010-12-24T14:28:45", String.class, LocalDateTime.class);
        assertLocalDateTime(it);
    }

    @Test
    void testLocalDateTimePattern() {
        LocalDateTime it = withPattern("dd.MM.yyyy.HH.mm.ss",
                                       () -> mm.map("24.12.2010.14.28.45", String.class, LocalDateTime.class));
        assertLocalDateTime(it);
    }

    @Test
    void testLocalDateTimeFormatter() {
        LocalDateTime it = withFormatter("dd.MM.yyyy.HH.mm.ss",
                                         () -> mm.map("24.12.2010.14.28.45", String.class, LocalDateTime.class));
        assertLocalDateTime(it);
    }

    @Test
    void testLocalDate() {
        LocalDate it = mm.map("2010-12-24", String.class, LocalDate.class);
        assertLocalDate(it);
    }

    @Test
    void testLocalDatePattern() {
        LocalDate it = withPattern("dd.MM.yyyy", () -> mm.map("24.12.2010", String.class, LocalDate.class));
        assertLocalDate(it);
    }

    @Test
    void testLocalDateFormatter() {
        LocalDate it = withFormatter("dd.MM.yyyy", () -> mm.map("24.12.2010", String.class, LocalDate.class));
        assertLocalDate(it);
    }

    @Test
    void testUsingServiceLoader() {
        MapperManager mm = MapperManager.create();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class);
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.INTEGER_TYPE);
        assertThat(result, is(11));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, GenericType.create(Long.class));
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class);
        assertThat(longResult, is(10L));

        Short shortResult = mm.map(source, String.class, Short.class);
        assertThat(shortResult, is((short) 10));
        // must be the same
        shortResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.SHORT_TYPE);
        assertThat(shortResult, is((short) 10));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, NotMappedType.class));
    }

    @Test
    void testUsingCustomProviders() {
        MapperManager mm = MapperManager.builder(HelidonServiceLoader.builder(ServiceLoader.load(MapperProvider.class))
                                                         .useSystemServiceLoader(false)
                                                         .build())
                .addMapperProvider(new ServiceLoaderMapper1())
                .build();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class);
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.INTEGER_TYPE);
        assertThat(result, is(10));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, GenericType.create(Long.class));
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class);
        assertThat(longResult, is(10L));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, Short.class));
        assertThrows(MapperException.class, () -> mm.map(source, ServiceLoaderMapper2.STRING_TYPE,
                                                         ServiceLoaderMapper2.SHORT_TYPE));
        assertThrows(MapperException.class, () -> mm.map(source, String.class, NotMappedType.class));
    }

    @Test
    void testUsingServiceLoaderAndCustomMappers() {
        MapperManager mm = MapperManager.builder()
                .addMapper(String::valueOf, Integer.class, String.class)
                .addMapper(String::valueOf, ServiceLoaderMapper2.SHORT_TYPE, ServiceLoaderMapper2.STRING_TYPE)
                .build();

        String source = "10";
        // using classes
        Integer result = mm.map(source, String.class, Integer.class);
        assertThat(result, is(10));

        // using generic types
        result = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.INTEGER_TYPE);
        assertThat(result, is(11));

        // search for opposite (use class, find type and vice versa)
        Long longResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, GenericType.create(Long.class));
        assertThat(longResult, is(10L));
        // must be the same
        longResult = mm.map(source, String.class, Long.class);
        assertThat(longResult, is(10L));

        Short shortResult = mm.map(source, String.class, Short.class);
        assertThat(shortResult, is((short) 10));
        // must be the same
        shortResult = mm.map(source, ServiceLoaderMapper2.STRING_TYPE, ServiceLoaderMapper2.SHORT_TYPE);
        assertThat(shortResult, is((short) 10));

        assertThrows(MapperException.class, () -> mm.map(source, String.class, NotMappedType.class));

        // and add tests for integer and short types
        String stringResult = mm.map(42, Integer.class, String.class);
        assertThat(stringResult, is("42"));
        stringResult = mm.map(42, GenericType.create(Integer.class), ServiceLoaderMapper2.STRING_TYPE);
        assertThat(stringResult, is("42"));

        stringResult = mm.map((short) 42, Short.class, String.class);
        assertThat(stringResult, is("42"));
        stringResult = mm.map((short) 42, ServiceLoaderMapper2.SHORT_TYPE, ServiceLoaderMapper2.STRING_TYPE);
        assertThat(stringResult, is("42"));
    }

    private void assertYearMonth(YearMonth it) {
        assertThat("Month", it.getMonth(), is(Month.DECEMBER));
        assertThat("Year", it.getYear(), is(2010));
    }

    private void assertOffsetTime(OffsetTime it) {
        assertThat("Seconds", it.getSecond(), is(45));
        assertThat("Minutes", it.getMinute(), is(28));
        assertThat("Hours", it.getHour(), is(14));
        assertThat("Offset", it.getOffset(), is(ZoneOffset.ofHours(1)));
    }

    private void assertOffsetDateTime(OffsetDateTime it) {
        assertThat("Seconds", it.getSecond(), is(45));
        assertThat("Minutes", it.getMinute(), is(28));
        assertThat("Hours", it.getHour(), is(14));
        assertThat("Day of month", it.getDayOfMonth(), is(24));
        assertThat("Month", it.getMonth(), is(Month.DECEMBER));
        assertThat("Year", it.getYear(), is(2010));
        assertThat("Offset", it.getOffset(), is(ZoneOffset.ofHours(1)));
    }

    private void assertInstant(Instant instant) {
        OffsetDateTime it = instant.atOffset(ZoneOffset.UTC);
        assertThat("Seconds", it.getSecond(), is(45));
        assertThat("Minutes", it.getMinute(), is(28));
        assertThat("Hours", it.getHour(), is(14));
        assertThat("Day of month", it.getDayOfMonth(), is(24));
        assertThat("Month", it.getMonth(), is(Month.DECEMBER));
        assertThat("Year", it.getYear(), is(2010));
    }

    private void assertZonedDateTime(ZonedDateTime it) {
        assertThat("Seconds", it.getSecond(), is(45));
        assertThat("Minutes", it.getMinute(), is(28));
        assertThat("Hours", it.getHour(), is(14));
        assertThat("Day of month", it.getDayOfMonth(), is(24));
        assertThat("Month", it.getMonth(), is(Month.DECEMBER));
        assertThat("Year", it.getYear(), is(2010));
        assertThat("Offset", it.getOffset(), is(ZoneOffset.ofHours(1)));
        assertThat("ZoneId", it.getZone(), is(ZoneId.of("Europe/Prague")));
    }

    private void assertLocalDate(LocalDate it) {
        assertThat("Day of month", it.getDayOfMonth(), is(24));
        assertThat("Month", it.getMonth(), is(Month.DECEMBER));
        assertThat("Year", it.getYear(), is(2010));
    }

    private void assertLocalDateTime(LocalDateTime it) {
        assertThat("Seconds", it.getSecond(), is(45));
        assertThat("Minutes", it.getMinute(), is(28));
        assertThat("Hours", it.getHour(), is(14));
        assertThat("Day of month", it.getDayOfMonth(), is(24));
        assertThat("Month", it.getMonth(), is(Month.DECEMBER));
        assertThat("Year", it.getYear(), is(2010));
    }

    private void assertLocalTime(LocalTime it) {
        assertThat("Seconds", it.getSecond(), is(45));
        assertThat("Minutes", it.getMinute(), is(28));
        assertThat("Hours", it.getHour(), is(14));
    }

    private <T> T withPattern(String format, Callable<T> callable) {
        Context context = Context.create();
        context.register(MapperManager.FORMAT_CLASSIFIER, format);
        return Contexts.runInContext(context, callable);
    }

    private <T> T withFormatter(String format, Callable<T> callable) {
        Context context = Context.create();
        context.register(MapperManager.FORMAT_CLASSIFIER, DateTimeFormatter.ofPattern(format));
        return Contexts.runInContext(context, callable);
    }

    private <T> void test(MapperManager mm, String stringValue, Class<T> type, T expected) {
        T mappedValue = mm.map(stringValue, String.class, type);
        assertThat("From String to " + type.getName(), mappedValue, is(expected));
    }

    private static final class NotMappedType {

    }
}