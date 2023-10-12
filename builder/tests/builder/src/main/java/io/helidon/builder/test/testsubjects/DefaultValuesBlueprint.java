/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Blueprint with default values.
 */
@Prototype.Blueprint
interface DefaultValuesBlueprint {
    String DEFAULT_STRING = "defaultValue";
    int DEFAULT_INT = 42;
    boolean DEFAULT_BOOLEAN = true;
    double DEFAULT_DOUBLE = 42.42;
    long DEFAULT_LONG = Long.MAX_VALUE;


    static String defaultString() {
        return DEFAULT_STRING;
    }

    static List<String> defaultStrings() {
        return List.of(DEFAULT_STRING, DEFAULT_STRING);
    }

    @Option.DefaultMethod(type = DefaultValuesBlueprint.class, value = "defaultString")
    String methodString();

    @Option.DefaultMethod(type = DefaultValuesBlueprint.class, value = "defaultStrings")
    List<String> methodStrings();

    @Option.DefaultCode("@java.util.List@.of(\"From code\")")
    List<String> codeStrings();

    @Option.Default(DEFAULT_STRING)
    String string();

    @Option.Default({DEFAULT_STRING, DEFAULT_STRING})
    List<String> strings();

    @Option.Default(DEFAULT_STRING)
    Supplier<String> stringSupplier();

    @Option.DefaultInt(DEFAULT_INT)
    int integer();
    @Option.DefaultInt({DEFAULT_INT, DEFAULT_INT})
    List<Integer> integers();

    @Option.DefaultBoolean(DEFAULT_BOOLEAN)
    boolean aBoolean();
    @Option.DefaultBoolean({DEFAULT_BOOLEAN, DEFAULT_BOOLEAN})
    List<Boolean> booleans();
    @Option.DefaultDouble(DEFAULT_DOUBLE)
    double aDouble();
    @Option.DefaultDouble({DEFAULT_DOUBLE, DEFAULT_DOUBLE})
    List<Double> doubles();
    @Option.DefaultLong(DEFAULT_LONG)
    long aLong();
    @Option.DefaultLong({DEFAULT_LONG, DEFAULT_LONG})
    List<Long> longs();
    @Option.Default("FIRST")
    AnEnum anEnum();
    @Option.Default({"FIRST", "SECOND", "THIRD"})
    List<AnEnum> enums();

    @Option.Default({"key1", "value1", "key2", "value2"})
    Map<String, String> stringMap();

    enum AnEnum {
        FIRST,
        SECOND,
        THIRD
    }
}
