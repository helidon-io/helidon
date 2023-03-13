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

package io.helidon.builder.processor.tools;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.types.TypeName;

import static io.helidon.common.types.TypeInfo.KIND_CLASS;
import static io.helidon.common.types.TypeInfo.KIND_ENUM;
import static io.helidon.common.types.TypeInfo.KIND_INTERFACE;
import static io.helidon.common.types.TypeInfo.KIND_PACKAGE;
import static io.helidon.common.types.TypeInfo.KIND_RECORD;
import static io.helidon.common.types.TypeInfo.MODIFIER_ABSTRACT;
import static io.helidon.common.types.TypeInfo.MODIFIER_FINAL;
import static io.helidon.common.types.TypeInfo.MODIFIER_PRIVATE;
import static io.helidon.common.types.TypeInfo.MODIFIER_PROTECTED;
import static io.helidon.common.types.TypeInfo.MODIFIER_PUBLIC;
import static io.helidon.common.types.TypeInfo.MODIFIER_STATIC;

/**
 * Provides functions to aid with bean naming and parsing.
 */
public class BeanUtils {

    private BeanUtils() {
    }

    /**
     * Returns true if the provided type name is a boolean type.
     *
     * @param typeName  the type name
     * @return true if the type name provided is boolean type
     */
    public static boolean isBooleanType(String typeName) {
        return Boolean.class.getName().equals(typeName) || boolean.class.getName().equals(typeName);
    }

    /**
     * Returns true if the provided type is a boolean type.
     *
     * @param type      the type
     * @return true if the type name provided is boolean type
     */
    public static boolean isBooleanType(Class<?> type) {
        return isBooleanType(type.getName());
    }

    /**
     * Returns true if the return type is valid for a bean (e.g., not void, etc.).
     *
     * @param typeName the return type name for the method, represented as a string
     * @return true if valid
     */
    public static boolean isValidMethodType(String typeName) {
        return !typeName.isBlank()
                && !typeName.equals(void.class.getName())
                && !typeName.equals(Void.class.getName());
    }

    /**
     * Validates the method, and will optionally throw if the throw message supplier is passed.
     *
     * @param methodName                the method name
     * @param methodTypeName            the method type
     * @param throwIfInvalid            flag indicating if an exception should be raised if invalid
     * @param attributeNameRef          the reference that will be populated to hold the attribute names - in preferred naming order
     * @return true if the method name represents a valid bean getter/is method
     * @throws java.lang.RuntimeException if the throw message is used and the method name is invalid
     */
    public static boolean validateAndParseMethodName(String methodName,
                                                     String methodTypeName,
                                                     boolean throwIfInvalid,
                                                     AtomicReference<Optional<List<String>>> attributeNameRef) {
        attributeNameRef.set(Optional.empty());

        if (!isValidMethodType(methodTypeName)) {
            return invalidMethod(methodName, throwIfInvalid, "invalid return type");
        }

        boolean isBoolean = isBooleanType(methodTypeName);
        if (isBoolean) {
            if (!methodName.startsWith("is") && !methodName.startsWith("get")) {
                return invalidMethod(methodName, throwIfInvalid, "invalid method name (must start with get or is)");
            }

            if (methodName.startsWith("is") && methodName.length() == 2) {
                return invalidMethod(methodName, throwIfInvalid, "invalid method name (must start with get or is)");
            }

            if (methodName.startsWith("is")) {
                return validBooleanIsMethod(methodName, attributeNameRef, throwIfInvalid);
            }
        }

        if (!methodName.startsWith("get") || methodName.length() == 3) {
            return invalidMethod(methodName, throwIfInvalid, "invalid method name (must start with get or is)");
        }

        return validMethod(methodName, attributeNameRef, throwIfInvalid);
    }

    /**
     * Returns true if the word provided is considered to be a reserved word and should otherwise be avoided from generation.
     *
     * @param word the word
     * @return true if it appears to be a reserved word
     */
    public static boolean isReservedWord(String word) {
        word = word.toUpperCase();
        return word.equals(KIND_CLASS)
                || word.equals(KIND_INTERFACE)
                || word.equals(KIND_PACKAGE)
                || word.equals(KIND_ENUM)
                || word.equals(MODIFIER_STATIC)
                || word.equals(MODIFIER_FINAL)
                || word.equals(MODIFIER_PUBLIC)
                || word.equals(MODIFIER_PROTECTED)
                || word.equals(MODIFIER_PRIVATE)
                || word.equalsIgnoreCase(KIND_RECORD)
                || word.equals(MODIFIER_ABSTRACT);
    }

    /**
     * Returns true if the given type is known to be a built-in java type (e.g., package name starts with "java").
     *
     * @param type the fully qualified type name
     * @return true if the given type is definitely known to be built-in Java type
     */
    public static boolean isBuiltInJavaType(TypeName type) {
        return type.primitive() || type.name().startsWith("java.");
    }

    private static boolean validMethod(String name,
                                       AtomicReference<Optional<List<String>>> attributeNameRef,
                                       boolean throwIfInvalid) {
        assert (name.trim().equals(name));
        String attrName = name.substring(3);
        char c = attrName.charAt(0);

        if (!validMethodCase(attrName, c, throwIfInvalid)) {
            return false;
        }

        c = Character.toLowerCase(c);
        String altName = "" + c + attrName.substring(1);
        attributeNameRef.set(Optional.of(List.of(isReservedWord(altName) ? name : altName)));

        return true;
    }

    private static boolean validBooleanIsMethod(String name,
                                       AtomicReference<Optional<List<String>>> attributeNameRef,
                                       boolean throwIfInvalid) {
        assert (name.trim().equals(name));
        char c = name.charAt(2);

        if (!validMethodCase(name, c, throwIfInvalid)) {
            return false;
        }

        c = Character.toLowerCase(c);
        String altName = "" + c + name.substring(3);
        attributeNameRef.set(Optional.of(isReservedWord(altName) ? List.of(name) : List.of(altName, name)));

        return true;
    }

    private static boolean validMethodCase(String name,
                                           char c,
                                           boolean throwIfInvalid) {
        if (!Character.isAlphabetic(c)) {
            return invalidMethod(name,
                                 throwIfInvalid,
                                 "invalid bean attribute name (must start with uppercase alpha char)");
        }

        if (!Character.isUpperCase(c)) {
            return invalidMethod(name,
                                 throwIfInvalid,
                                 "invalid bean attribute name (must start with uppercase alpha char)");
        }

        return true;
    }

    private static boolean invalidMethod(String methodName,
                                         boolean throwIfInvalid,
                                         String message) {
        if (throwIfInvalid) {
            throw new RuntimeException(message + ": " + methodName);
        }

        return false;
    }

}
