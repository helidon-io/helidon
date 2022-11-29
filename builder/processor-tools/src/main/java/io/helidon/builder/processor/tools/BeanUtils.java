/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

        return validMethod(methodName.substring(3), attributeNameRef, throwIfInvalid);
    }

    private static boolean validMethod(String name,
                                       AtomicReference<Optional<List<String>>> attributeNameRef,
                                       boolean throwIfInvalid) {
        assert (name.trim().equals(name));
        char c = name.charAt(0);

        if (!validMethodCase(name, c, throwIfInvalid)) {
            return false;
        }

        c = Character.toLowerCase(c);
        attributeNameRef.set(Optional.of(Collections.singletonList("" + c + name.substring(1))));

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
        attributeNameRef.set(Optional.of(List.of("" + c + name.substring(3), name)));

        return true;
    }

    private static boolean validMethodCase(String name, char c, boolean throwIfInvalid) {
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

    private static boolean invalidMethod(String methodName, boolean throwIfInvalid, String message) {
        if (throwIfInvalid) {
            throw new RuntimeException(message + ": " + methodName);
        }

        return false;
    }

}
