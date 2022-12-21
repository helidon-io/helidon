/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link ConfigMappers}.
 */
public class ConfigMappersTest {

    /**
     * This is API contract check.
     * <p>
     * All {@code public static} single argument ({@code String} or {@code Config}) methods named {@code to*}
     * are used as built-in mappers ({@link ConfigMappers#builtInMappers()}).
     */
    @Test
    public void testAllToTypeStaticMethodsAreRegistered() {

        Class[] methodArray = Arrays.stream(ConfigMappers.class.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())) //public
                .filter(method -> Modifier.isStatic(method.getModifiers())) //static
                .filter(method -> method.getName().startsWith("to")) //to*
                .filter(method -> method.getParameterCount() == 1) //single parameter
                .filter(this::expectedMapperParamType)
                .map(Method::getReturnType)
                .distinct()
                .toArray(Class[]::new);

        assertThat(ConfigMappers.BUILT_IN_MAPPERS.keySet(), hasItems(methodArray));
    }

    private boolean expectedMapperParamType(Method method) {
        Class<?> type = method.getParameterTypes()[0];

        //String, CharSequence or Config parameter
        return String.class.equals(type)
                || Config.class.equals(type)
                || CharSequence.class.equals(type);
    }

    @Test
    public void testEssentialMappers() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create());

        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(
                        "text-text", "string value",
                        "int-p", "2147483647",
                        "long-p", "9223372036854775807",
                        "double-p", "1234.5678")))
                .build();

        assertThat(manager.map(config, Config.class), is(config));
        assertThat(manager.map(config.get("text-text"), String.class), is("string value"));
        assertThat(manager.map(config.get("int-p"), OptionalInt.class).getAsInt(), is(2147483647));
        assertThat(manager.map(config.get("long-p"), OptionalLong.class).getAsLong(), is(9223372036854775807L));
        assertThat(manager.map(config.get("double-p"), OptionalDouble.class).getAsDouble(), is(1234.5678));
    }

    private <T> void assertMapper(String stringValue, Class<T> type, T expectedValue) {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create());

        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of("key", stringValue)))
                .build();

        assertThat(manager.map(config.get("key"), type), is(expectedValue));
    }

    @Test
    public void testBuiltinMappers() throws MalformedURLException, ParseException {
        //primitive types
        assertMapper("13", Byte.class, (byte) 13);
        assertMapper("13", Byte.TYPE, (byte) 13);
        assertMapper("13", byte.class, (byte) 13);

        assertMapper("-13", Short.class, (short) -13);
        assertMapper("-13", Short.TYPE, (short) -13);
        assertMapper("-13", short.class, (short) -13);

        assertMapper("2147483647", Integer.class, 2147483647);
        assertMapper("2147483647", Integer.TYPE, 2147483647);
        assertMapper("2147483647", int.class, 2147483647);

        assertMapper("9223372036854775807", Long.class, 9223372036854775807L);
        assertMapper("9223372036854775807", Long.TYPE, 9223372036854775807L);
        assertMapper("9223372036854775807", long.class, 9223372036854775807L);

        assertMapper("1234.5678", Float.class, 1234.5678f);
        assertMapper("1234.5678", Float.TYPE, 1234.5678f);
        assertMapper("1234.5678", float.class, 1234.5678f);

        assertMapper("1234.5678", Double.class, 1234.5678);
        assertMapper("1234.5678", Double.TYPE, 1234.5678);
        assertMapper("1234.5678", double.class, 1234.5678);

        assertMapper("true", Boolean.class, true);
        assertMapper("true", Boolean.TYPE, true);
        assertMapper("true", boolean.class, true);

        assertMapper("L", Character.class, 'L');
        assertMapper("L", Character.TYPE, 'L');
        assertMapper("L", char.class, 'L');

        //java.lang
        assertMapper(this.getClass().getName(), Class.class, this.getClass());
        //javax.math
        assertMapper(this.getClass().getName(), Class.class, this.getClass());
        assertMapper("922337203.6854775807", BigDecimal.class, new BigDecimal("922337203.6854775807"));
        assertMapper("9223372036854775807", BigInteger.class, new BigInteger("9223372036854775807"));
        //java.time
        assertMapper("PT10H", Duration.class, Duration.ofHours(10));
        assertMapper("2017-03-14", LocalDate.class, LocalDate.of(2017, Month.MARCH, 14));
        assertMapper("2017-03-14T17:04", LocalDateTime.class, LocalDateTime.of(2017, Month.MARCH, 14, 17, 4));
        assertMapper("17:04", LocalTime.class, LocalTime.of(17, 4));
        assertMapper("2017-03-14T17:04+01:00[Europe/Prague]", ZonedDateTime.class,
                     ZonedDateTime.of(LocalDateTime.of(2017, Month.MARCH, 14, 17, 4), ZoneId.of("Europe/Prague")));
        assertMapper("Europe/Prague", ZoneId.class, ZoneId.of("Europe/Prague"));
        assertMapper("2007-12-03T10:15:30.00Z", Instant.class, Instant.ofEpochSecond(1196676930));
        assertMapper("17:04+01:00", OffsetTime.class, OffsetTime.of(17, 4, 0, 0, ZoneOffset.ofHours(1)));
        assertMapper("2017-03-14T17:04+01:00", OffsetDateTime.class,
                     OffsetDateTime.of(2017, 3, 14, 17, 4, 0, 0, ZoneOffset.ofHours(1)));
        assertMapper("P-3Y-4D", Period.class, Period.of(-3, 0, -4));
        assertMapper("+0100", ZoneOffset.class, ZoneOffset.of("+0100"));
        //java.io
        assertMapper("/tmp/myfile.txt", File.class, new File("/tmp/myfile.txt"));
        //java.nio
        assertMapper("/tmp/myfile.txt", Path.class, Paths.get("/tmp/myfile.txt"));
        assertMapper("ISO-8859-2", Charset.class, Charset.forName("iso-8859-2"));
        //java.net
        assertMapper("mailto:java-net@java.sun.com", URI.class, URI.create("mailto:java-net@java.sun.com"));
        assertMapper("http://localhost:8080/config", URL.class, new URL("http://localhost:8080/config"));
        //java.util
        assertMapper("0b6a4e86-0955-11e7-93ae-92361f002671", UUID.class, UUID.fromString("0b6a4e86-0955-11e7-93ae-92361f002671"));
        assertMapper("2011-12-03T10:15:30+01:00", Date.class,
                     new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2011-12-03T10:15:30+0100"));
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse("2011-12-03T10:15:30+0100"));
        assertMapper("2011-12-03T10:15:30+01:00", Calendar.class, calendar);
        assertMapper("2011-12-03T10:15:30+01:00", GregorianCalendar.class, calendar);
        assertMapper("Europe/Prague", TimeZone.class, TimeZone.getTimeZone(ZoneId.of("Europe/Prague")));
        assertMapper("Europe/Prague", SimpleTimeZone.class, new SimpleTimeZone(3_600_000, "Europe/Prague"));
        // java.util.regex.Pattern does not support equals -> individual test, see testBuiltinMappersPattern
    }

    @Test
    public void testBuiltinMappersPattern() throws MalformedURLException {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create());

        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of("key", "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$")))
                .build();

        assertThat(manager.map(config.get("key"), Pattern.class).toString(),
                   is(Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$").toString()));
    }

    @Test
    public void testBuiltinMappersRootProperties() throws MalformedURLException {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create());
        Config config = createConfig();

        Properties rootProperties = manager.map(config, Properties.class);
        assertThat(rootProperties.entrySet(), hasSize(5));
        assertThat(rootProperties, hasEntry("key1", "value1"));
        assertThat(rootProperties, hasEntry("key2.key21", "value21"));
        assertThat(rootProperties, hasEntry("key2.key22", "value22"));
        assertThat(rootProperties, hasEntry("key2.key23", "value23"));
        assertThat(rootProperties, hasEntry("key3", "value3"));
    }

    @Test
    public void testBuiltinMappersSubNodeProperties() throws MalformedURLException {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create());
        Config config = createConfig().get("key2");

        Properties key2Properties = manager.map(config, Properties.class);
        assertThat(key2Properties.entrySet(), hasSize(3));
        assertThat(key2Properties, hasEntry("key2.key21", "value21"));
        assertThat(key2Properties, hasEntry("key2.key22", "value22"));
        assertThat(key2Properties, hasEntry("key2.key23", "value23"));
    }

    @Test
    void testBuiltinEnumMapper() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create(), true);
        Config config = createEnumConfig();
        MyType type = manager.map(config.get("type-a"), MyType.class);
        assertThat("Lower-case mapping", type, is(MyType.A));

        type = manager.map(config.get("type-A"), MyType.class);
        assertThat("Upper-case mapping", type, is(MyType.A));

        type = manager.map(config.get("type-special-b"), MyType.class);
        assertThat("Mixed-clase and hyphen mapping", type, is(MyType.SPECIAL_B));

        type = manager.map(config.get("type-special_b"), MyType.class); // <-- That's an underscore, not a hyphen.
        assertThat("Mixed-clase and no hyphen mapping", type, is(MyType.SPECIAL_B));

        ConfigMappingException e = assertThrows(ConfigMappingException.class,
                                                    () -> manager.map(config.get("type-absent"), MyType.class),
                                                    "Unmatched enum value");
        assertThat("Unmapped enum value error message", e.getMessage(), containsString("no match"));

        assertThrows(MissingValueException.class,
                     () -> manager.map(config.get("missing-value"), MyType.class),
                     "Missing value in config");
    }

    @Test
    void testBuiltInEnumMapperWithConfusingEnum() {
        ConfigMapperManager manager = BuilderImpl.buildMappers(ConfigMapperManager.MapperProviders.create(), true);

        Config confusingConfig = createConfusingEnumConfig();

        ConfusingType type = manager.map(confusingConfig.get("type-RED"), ConfusingType.class);
        assertThat("Exact match to RED", type, is(ConfusingType.RED));

        type = manager.map(confusingConfig.get("type-Red"), ConfusingType.class);
        assertThat("Exact match to Red", type, is(ConfusingType.Red));

        type = manager.map(confusingConfig.get("type-blue"), ConfusingType.class);
        assertThat("Case-insensitive match to BLUE", type, is(ConfusingType.BLUE));

        ConfigMappingException e = assertThrows(ConfigMappingException.class,
                     () -> manager.map(confusingConfig.get("type-red"), ConfusingType.class),
                     "Ambiguous enum value");
        assertThat("Ambiguous value error message", e.getMessage(), containsString("ambiguous"));
    }

    @Test
    void testEnumUsingAs() {
        Config config = createEnumConfig();
        MyType myType = config.get("type-a").as(MyType.class).orElse(null);
        assertThat("'as' mapping of 'a'", myType, allOf(notNullValue(), is(MyType.A)));

        myType = config.get("type-A").as(MyType.class).orElse(null);
        assertThat("'as' mapping of 'A'", myType, allOf(notNullValue(), is(MyType.A)));

        assertThrows(MissingValueException.class,
                     () -> Config.empty().as(MyType.class).get(),
                     "Missing value in config");
    }

    @Test
    void testEnumUsingAsWithConfusingEnum() {
        Config config = createConfusingEnumConfig();

        ConfusingType cType = config.get("type-Red").as(ConfusingType.class).orElse(null);
        assertThat("'as' mapping of 'Red'", cType, allOf(notNullValue(), is(ConfusingType.Red)));

        cType = config.get("type-RED").as(ConfusingType.class).orElse(null);
        assertThat("'as' mapping of 'RED'", cType, allOf(notNullValue(), is(ConfusingType.RED)));

        cType = config.get("type-blue").as(ConfusingType.class).orElse(null);
        assertThat("'as' mapping of 'blue'", cType, allOf(notNullValue(), is(ConfusingType.BLUE)));


        ConfigMappingException e = assertThrows(ConfigMappingException.class,
                                                    () -> config.get("type-red")
                                                            .as(ConfusingType.class)
                                                            .ifPresent((ConfusingType t) -> {}),
                                                    "Ambiguous value failure using 'as'");
        assertThat("Ambiguous value error message using 'as'", e.getMessage(), containsString("ambiguous"));

        e = assertThrows(ConfigMappingException.class,
                         () -> config.get("type-absent")
                                 .as(ConfusingType.class)
                                 .ifPresent((ConfusingType t) -> {}),
                         "Unmatched value failure using 'as'");
        assertThat("Unmatched value error message using 'as'", e.getMessage(), containsString("no match"));
    }

    private Config createConfig() {
        return Config.builder()
                .sources(ConfigSources.create(Map.of(
                        "key1", "value1",
                        "key2.key21", "value21",
                        "key2.key22", "value22",
                        "key2.key23", "value23",
                        "key3", "value3"
                )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
    }

    public enum MyType {A, B, SPECIAL_B};

    public enum ConfusingType {RED, Red, BLUE};

    private Config createEnumConfig() {
        return Config.just(ConfigSources.create(Map.of(
                "type-a", "a",
                "type-A", "A",
                "type-special-b", "Special-b",
                "type-special_b", "Special_b",
                "type-absent", "absent"
        )));
    }

    private Config createConfusingEnumConfig() {
        return Config.just(ConfigSources.create(Map.of(
                "type-RED", "RED",
                "type-Red", "Red",
                "type-red", "red",
                "type-blue", "blue",
                "type-absent", "not-there")));
    }
}
