/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.Locale;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

/**
 * Tools for generating code.
 */
public final class CodegenUtil {
    private CodegenUtil() {
    }

    /**
     * Capitalize the first letter of the provided string.
     *
     * @param name string to capitalize
     * @return name with the first character as capital letter
     */
    public static String capitalize(String name) {
        if (name.isBlank() || name.isEmpty()) {
            return name;
        }
        char first = name.charAt(0);
        first = Character.toUpperCase(first);
        return first + name.substring(1);
    }

    /**
     * Create a constant field for a name of an element.
     * <p>
     * For example for {@code maxInitialLineLength} we would get
     * {@code MAX_INITIAL_LINE_LENGTH}.
     *
     * @param elementName name of the element
     * @return name of a constant
     */
    public static String toConstantName(String elementName) {
        /*
        Method name is camel case (such as maxInitialLineLength)
        result is constant like (such as MAX_INITIAL_LINE_LENGTH).
        */
        StringBuilder result = new StringBuilder();

        char[] chars = elementName.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char aChar = chars[i];

            if (Character.isUpperCase(aChar)) {
                if (!result.isEmpty() && Character.isLowerCase(chars[i - 1])) {
                    result.append('_')
                            .append(Character.toLowerCase(aChar));
                } else {
                    result.append(aChar);
                }
            } else if (Character.isLowerCase(aChar)) {
                result.append(aChar);
            } else if (Character.isDigit(aChar)) {
                result.append(aChar);
            } else {
                // not a character, replace with underscore
                result.append('_');
            }
        }

        return result.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Provides copyright header to be added before package declaration.
     *
     * @param generator     type of the generator (annotation processor)
     * @param trigger       type of the class that caused this type to be generated
     * @param generatedType type that is going to be generated
     * @return copyright string (can be multiline)
     */
    public static String copyright(TypeName generator, TypeName trigger, TypeName generatedType) {
        return CopyrightHandler.copyright(generator, trigger, generatedType);
    }

    /**
     * Create a generated annotation.
     *
     * @param generator     type of the generator (annotation processor)
     * @param trigger       type of the class that caused this type to be generated
     * @param generatedType type that is going to be generated
     * @param versionId     version of the generator
     * @param comments      additional comments, never use null (use empty string so they do not appear in annotation)
     * @return a new annotation to add to the generated type
     */
    public static Annotation generatedAnnotation(TypeName generator,
                                                 TypeName trigger,
                                                 TypeName generatedType,
                                                 String versionId,
                                                 String comments) {
        return GeneratedAnnotationHandler.create(generator, trigger, generatedType, versionId, comments);
    }

}
