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

package io.helidon.pico.processor;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import io.helidon.pico.tools.Messager;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

final class ProcessorUtils {
    private ProcessorUtils() {
    }

    /**
     * Determines the root throwable stack trace element from a chain of throwable causes.
     *
     * @param t the throwable
     * @return the root throwable error stack trace element
     */
    static StackTraceElement rootStackTraceElementOf(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getStackTrace()[0];
    }

    /**
     * Converts a collection to a comma delimited string.
     *
     * @param coll the collection
     * @return the concatenated, delimited string value
     */
    static String toString(Collection<?> coll) {
        return toString(coll, null, null);
    }

    /**
     * Provides specialization in concatenation, allowing for a function to be called for each element as well as to
     * use special separators.
     *
     * @param coll      the collection
     * @param fnc       the optional function to translate the collection item to a string
     * @param separator the optional separator
     * @param <T> the type held by the collection
     * @return the concatenated, delimited string value
     */
    static <T> String toString(Collection<T> coll,
                               Function<T, String> fnc,
                               String separator) {
        Function<T, String> fn = (fnc == null) ? String::valueOf : fnc;
        separator = (separator == null) ? ", " : separator;
        return coll.stream().map(fn).collect(Collectors.joining(separator));
    }

    /**
     * Splits given using a comma-delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @return the list of string values
     */
    static List<String> toList(String str) {
        return toList(str, ",");
    }

    /**
     * Splits a string given a delimiter, and returns a trimmed list of string for each item.
     *
     * @param str the string to split
     * @param delim the delimiter
     * @return the list of string values
     */
    static List<String> toList(String str,
                               String delim) {
        String[] split = str.split(delim);
        return Arrays.stream(split).map(String::trim).collect(Collectors.toList());
    }

    /**
     * Will return non-empty File if the uri represents a local file on the fs.
     *
     * @param uri the uri of the artifact
     * @return the file instance, or empty if not local
     */
    static Optional<Path> toPath(URI uri) {
        if (uri.getHost() != null) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(uri));
    }

    /**
     * Determines if the given type element is defined in the module being processed. If so then the return value is set to
     * {@code true} and the moduleName is cleared out. If not then the return value is set to {@code false} and the
     * {@code moduleName} is set to the module name if it has a qualified module name, and not from an internal java module system
     * type.
     *
     * @param type          the type element to analyze
     * @param moduleName    the module name to populate
     * @param roundEnv      the round environment
     * @param processingEnv the processing environment
     * @param msgr          the messager
     * @return true if the type is defined in this module, false otherwise
     */
    static boolean isInThisModule(TypeElement type,
                                  AtomicReference<String> moduleName,
                                  RoundEnvironment roundEnv,
                                  ProcessingEnvironment processingEnv,
                                  Messager msgr) {
        moduleName.set(null);
        if (roundEnv.getRootElements().contains(type)) {
            return true;
        }

        ModuleElement module = processingEnv.getElementUtils().getModuleOf(type);
        if (!module.isUnnamed()) {
            String name = module.getQualifiedName().toString();
            if (hasValue(name)) {
                moduleName.set(name);
            }
        }

        // if there is no module-info in use we need to try to find the type is in our source path and if
        // not found then just assume it is external
        try {
            Trees trees = Trees.instance(processingEnv);
            TreePath path = trees.getPath(type);
            if (path == null) {
                return false;
            }
            JavaFileObject sourceFile = path.getCompilationUnit().getSourceFile();
            return (sourceFile != null);
        } catch (Throwable t) {
            msgr.debug("unable to determine if contract is external: " + type + "; " + t.getMessage(), t);

            // assumed external
            return false;
        }
    }

    static boolean hasValue(String val) {
        return (val != null && !val.isBlank());
    }

}
