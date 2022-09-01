package io.helidon.core.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// NameUtils from Micronaut Core
public class NameUtils {

    private static final Pattern DOT_UPPER = Pattern.compile("\\.[A-Z\\$]");

    /**
     * Converts a property name to class name according to the JavaBean convention.
     *
     * @param name The property name
     * @return The class name
     */
    public static String capitalize(String name) {
        final String rest = name.substring(1);

        // Funky rule so that names like 'pNAME' will still work.
        if (Character.isLowerCase(name.charAt(0)) && (rest.length() > 0) && Character.isUpperCase(rest.charAt(0))) {
            return name;
        }

        return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + rest;
    }

    /**
     * Decapitalizes a given string according to the rule:
     * <ul>
     * <li>If the first or only character is Upper Case, it is made Lower Case
     * <li>UNLESS the second character is also Upper Case, when the String is
     * returned unchanged.
     * </ul>
     *
     * @param name The String to decapitalize
     * @return The decapitalized version of the String
     */
    public static String decapitalize(String name) {
        if (name == null) {
            return null;
        }
        int length = name.length();
        if (length == 0) {
            return name;
        }
        // Decapitalizes the first character if a lower case
        // letter is found within 2 characters after the first
        // Abc -> abc
        // AB  -> AB
        // ABC -> ABC
        // ABc -> aBc
        boolean firstUpper = Character.isUpperCase(name.charAt(0));
        if (firstUpper) {
            if (length == 1) {
                return Character.toString(Character.toLowerCase(name.charAt(0)));
            }
            for (int i = 1; i < Math.min(length, 3); i++) {
                if (!Character.isUpperCase(name.charAt(i))) {
                    char[] chars = name.toCharArray();
                    chars[0] = Character.toLowerCase(chars[0]);
                    return new String(chars);
                }
            }
        }

        return name;
    }

    /**
     * Returns the simple name for a class represented as string.
     *
     * @param className The class name
     * @return The simple name of the class
     */
    public static String getSimpleName(String className) {
        Matcher matcher = DOT_UPPER.matcher(className);
        if (matcher.find()) {
            int position = matcher.start();
            return className.substring(position + 1);
        }
        return className;
    }

}
