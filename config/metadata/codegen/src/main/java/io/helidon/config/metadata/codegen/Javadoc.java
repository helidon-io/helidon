/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.codegen;

import java.util.regex.Pattern;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.RoundContext;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.CodegenUtil.capitalize;

/*
Possible improvements:
- @link - create a proper javadoc reference (i.e. always fully qualified reference), such as:
    {@link:io.helidon.common.Type#method(java.lang.String)}, so we can generate a nice reference for docs
- @value - if possible, find the actual value (string, int etc.) and add it as `thevalue`
- @see - create a proper javadoc reference (as for @link)
 */
final class Javadoc {
    private static final Pattern JAVADOC_CODE = Pattern.compile("\\{@code (.*?)}");
    private static final Pattern JAVADOC_LINK = Pattern.compile("\\{@link (.*?)}");
    private static final Pattern JAVADOC_LINKPLAIN = Pattern.compile("\\{@linkplain (.*?)}");
    private static final Pattern JAVADOC_VALUE = Pattern.compile("\\{@value (.*?)}");
    private static final Pattern JAVADOC_SEE = Pattern.compile("@see (.*?\n)");

    private Javadoc() {
    }

    // for existing usages
    static String parse(RoundContext roundContext, TypeInfo currentType, String javadoc) {
        return parse(roundContext, currentType, javadoc, true);
    }

    /**
     * Parses a Javadoc comment (provided as a string) into text that can be used for display/docs of the configuration option.
     * <p>
     * The following steps are done:
     * <ul>
     *     <li>{@code @param} is stripped from the text</li>
     *     <li>Any {@code @code} section: the code tag is removed, and surrounded with {@code '}</li>
     *     <li>Any {@code @link} section: the link tag is removed</li>
     *     <li>Any {@code @linkplain} section: the linkplain tag is removed</li>
     *     <li>Any {@code @value} section: the value tag is removed, {code #} is replaced with {@code .}</li>
     *     <li>Any {@code @see} section: the see tag is removed, prefixed with {@code See},
     *                  {code #} is replaced with {@code .}</li>
     *     <li>{@code @return} is stripped from the text, and the first letter is capitalized</li>
     * </ul>
     *
     * @param docComment "raw" javadoc from the source code
     * @return description of the option
     */
    static String parse(RoundContext roundContext, TypeInfo currentType, String docComment, boolean includeReturn) {
        if (docComment == null) {
            return "";
        }

        String javadoc = docComment;
        int index = javadoc.indexOf("@param");
        if (index > -1) {
            javadoc = docComment.substring(0, index);
        }
        // replace all {@code xxx} with 'xxx'
        javadoc = JAVADOC_CODE.matcher(javadoc).replaceAll(it -> javadocCode(it.group(1)));
        // replace all {@link ...} with just the link
        javadoc = JAVADOC_LINK.matcher(javadoc).replaceAll(it -> javadocLink(it.group(1)));
        // replace all {@link ...} with just the name
        javadoc = JAVADOC_LINKPLAIN.matcher(javadoc).replaceAll(it -> javadocLink(it.group(1)));
        // replace all {@value ...} with just the reference
        javadoc = JAVADOC_VALUE.matcher(javadoc)
                .replaceAll(it -> javadocConstantValue(roundContext, currentType, it.group(1)));

        int count = 9;
        index = javadoc.indexOf(" @return");
        if (index == -1) {
            count = 8;
            index = javadoc.indexOf("@return");
        }
        if (index > -1) {
            if (includeReturn) {
                javadoc = javadoc.substring(0, index) + capitalize(javadoc.substring(index + count).trim());
            } else {
                // need to find the next @ not preceded by {
                int endIndex = javadoc.length();
                // and we need to start from the end of the current @return
                int nextIndex = index + count;
                while (true) {
                    int nextAt = javadoc.indexOf('@', nextIndex);
                    if (nextAt == -1 || nextAt == 0 || nextAt == javadoc.length() - 1) {
                        break;
                    }

                    if (javadoc.charAt(nextAt - 1) == '{') {
                        nextIndex = nextAt + 1;
                        continue;
                    }
                    endIndex = nextAt;
                    break;
                }
                javadoc = javadoc.substring(0, index) + javadoc.substring(endIndex);
            }
        }

        // replace all {@see ...} with just the reference - after removing @return
        javadoc = JAVADOC_SEE.matcher(javadoc).replaceAll(it -> javadocSee(it.group(1)));

        return javadoc.trim();
    }

    private static String javadocSee(String originalValue) {
        return "See " + javadocValue(originalValue);
    }

    private static String javadocCode(String originalValue) {
        return '`' + originalValue + '`';
    }

    private static String javadocLink(String originalValue) {
        return javadocValue(originalValue);
    }

    private static String javadocValue(String originalValue) {
        if (originalValue.startsWith("#")) {
            return originalValue.substring(1);
        }
        // Do not replace # in href links, such as
        // <a href="https://en.wikipedia.org/wiki/ISO_8601#Durations">ISO_8601 Durations</a>
        int index = 0;
        StringBuilder result = new StringBuilder();
        while (true) {
            int indexOfHref = originalValue.indexOf("href=\"", index);
            if (indexOfHref == -1) {
                result.append(removeHash(originalValue.substring(index)));
                break;
            }
            int endOfHref = originalValue.indexOf('\"', indexOfHref + 6);
            if (endOfHref == -1) {
                // broken link, just append the rest
                result.append(originalValue.substring(index));
                break;
            }
            result.append(originalValue, index, endOfHref + 1);
            index = endOfHref + 1;
        }
        return result.toString();
    }

    private static String removeHash(String originalValue) {
        return originalValue.replace('#', '.');
    }

    private static String javadocConstantValue(RoundContext roundContext, TypeInfo currentType, String originalValue) {
        if (originalValue.startsWith("#")) {
            // constant is in this class
            String constantName = originalValue.substring(1);

            return constantValue(currentType, originalValue, constantName);
        } else {
            int separator = originalValue.lastIndexOf('#');
            if (separator < 0) {
                return javadocValue(originalValue);
            }
            TypeName typeName = TypeName.create(originalValue.substring(0, separator));
            if (typeName.packageName().isEmpty()) {
                typeName = TypeName.builder(typeName)
                        .packageName(currentType.typeName().packageName())
                        .build();
            }
            String constantName = originalValue.substring(separator + 1);
            // constant is in a different class, if there is no package information, we are in trouble - we will consider
            // this to be in this package, otherwise we will just use the value as present in the javadoc
            var constantType = roundContext.typeInfo(typeName);
            if (constantType.isEmpty()) {
                return javadocValue(originalValue);
            }

            return constantValue(constantType.get(), originalValue, constantName);
        }
    }

    private static String constantValue(TypeInfo currentType, String originalValue, String constantName) {
        var fieldValue = currentType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(ElementInfoPredicates::isStatic)
                .filter(ElementInfoPredicates.elementName(constantName))
                .findFirst()
                .flatMap(TypedElementInfo::defaultValue);

        if (fieldValue.isEmpty()) {
            return javadocValue(originalValue);
        }
        var constantValue = fieldValue.get();
        // this is a value
        // if it contains `, we must replace it
        // if it contains $, we must escape it
        constantValue = constantValue.replace('`', '"');
        constantValue = constantValue.replaceAll("\\$", "\\\\\\$");
        return "`" + constantValue + "`";
    }
}
