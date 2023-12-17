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

package io.helidon.inject.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoSourceParser;
import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.InjectCodegenTypes;

import static io.helidon.inject.maven.plugin.AbstractCreatorMojo.APPLICATION_PACKAGE_FILE_NAME;

final class MavenUtil {
    private static final String SRC_MAIN_JAVA_DIR = "/src/main/java";
    private static final String SRC_MAIN_JAVA_DIR_WIN = "\\src\\main\\java";
    private static final String SRC_TEST_JAVA_DIR = "/src/test/java";
    private static final String SRC_TEST_JAVA_DIR_WIN = "\\src\\test\\java";
    private static final System.Logger LOGGER = System.getLogger(MavenUtil.class.getName());

    private MavenUtil() {
    }

    /**
     * Creates a new classloader.
     *
     * @param classPath the classpath to use
     * @param parent    the parent loader
     * @return the loader
     */
    public static URLClassLoader createClassLoader(Collection<Path> classPath,
                                                   ClassLoader parent) {
        List<URL> urls = new ArrayList<>(classPath.size());
        try {
            for (Path dependency : classPath) {
                urls.add(dependency.toUri().toURL());
            }
        } catch (MalformedURLException e) {
            throw new CodegenException("Unable to build the classpath", e);
        }

        if (parent == null) {
            parent = Thread.currentThread().getContextClassLoader();
        }
        return new URLClassLoader(urls.toArray(new URL[0]), parent);
    }

    /**
     * Attempts to infer 'test' or base '' given the path.
     *
     * @param path the path
     * @return 'test' or '' (for base non-test)
     */
    public static String inferSourceOrTest(Path path) {
        Objects.requireNonNull(path);
        Path parent = path.getParent();
        if (parent == null) {
            return "";
        }
        Path gparent = parent.getParent();
        if (gparent == null) {
            return "";
        }
        return inferSourceOrTest(gparent, path);
    }

    /**
     * Returns the suggested package name to use.
     *
     * @param typeNames          the set of types that are being code generated
     * @param defaultPackageName the default package name to use if all options are exhausted
     * @return the suggested package name
     */
    public static String toSuggestedGeneratedPackageName(Collection<TypeName> typeNames,
                                                         String defaultPackageName) {
        return innerToSuggestedGeneratedPackageName(null, typeNames, defaultPackageName);
    }

    /**
     * Returns the suggested package name to use.
     *
     * @param typeNames          the set of types that are being code generated
     * @param defaultPackageName the default package name to use if all options are exhausted
     * @param descriptor         the module-info descriptor
     * @return the suggested package name
     */
    public static String toSuggestedGeneratedPackageName(Collection<TypeName> typeNames,
                                                         String defaultPackageName,
                                                         ModuleInfo descriptor) {
        Objects.requireNonNull(descriptor);
        return innerToSuggestedGeneratedPackageName(descriptor, typeNames, defaultPackageName);
    }

    /**
     * Persist the package name into scratch for later usage.
     *
     * @param scratchPath the scratch directory path
     * @param packageName the package name
     * @throws io.helidon.codegen.CodegenException if there are any errors creating or writing the content
     */
    public static void saveAppPackageName(Path scratchPath,
                                          String packageName) {
        File scratchDir = scratchPath.toFile();
        File packageFileName = new File(scratchDir, APPLICATION_PACKAGE_FILE_NAME);
        try {
            Files.createDirectories(packageFileName.getParentFile().toPath());
            Files.writeString(packageFileName.toPath(), packageName, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CodegenException("Unable to save: " + packageFileName, e);
        }
    }

    /**
     * Returns the base module path given its source path.
     *
     * @param sourcePath the source path
     * @return the base path
     */
    static Path toBasePath(String sourcePath) {
        int pos = sourcePath.lastIndexOf(SRC_MAIN_JAVA_DIR);
        if (pos < 0) {
            pos = sourcePath.lastIndexOf(SRC_TEST_JAVA_DIR);
        }
        if (pos < 0) {
            pos = sourcePath.lastIndexOf(SRC_MAIN_JAVA_DIR_WIN);
        }
        if (pos < 0) {
            pos = sourcePath.lastIndexOf(SRC_TEST_JAVA_DIR_WIN);
        }
        if (pos < 0) {
            throw new CodegenException("Invalid source path: " + sourcePath);
        }
        Path path = Path.of(sourcePath.substring(0, pos));
        return Objects.requireNonNull(path);
    }

    /**
     * Extract the module name, first attempting the source path (test or main), and if not found using
     * the base path, presumably having basePath being a parent in the sourcePath hierarchy.
     *
     * @param basePath         the secondary path to try if module-info was not found in the source path
     * @param sourcePath       the source path
     * @param defaultToUnnamed if true, will return the default name, otherwise empty is returned
     * @return the module name suggested to use, most appropriate for the name of {@link
     *         io.helidon.inject.Application} or {@link io.helidon.inject.service.ModuleComponent}
     */
    static Optional<String> toSuggestedModuleName(Path basePath,
                                                  Path sourcePath,
                                                  boolean defaultToUnnamed) {
        AtomicReference<String> typeSuffix = new AtomicReference<>();
        ModuleInfo descriptor = findModuleInfo(basePath, sourcePath, typeSuffix, null, null)
                .orElse(null);

        return Optional.ofNullable(toSuggestedModuleName((descriptor != null) ? descriptor.name() : null,
                                                         typeSuffix.get(),
                                                         defaultToUnnamed ? ModuleInfo.DEFAULT_MODULE_NAME : null));
    }

    /**
     * Attempts to find the descriptor, setting meta-information that is useful for later processing.
     *
     * @param basePath       the base path to start the search
     * @param sourcePath     the source path, assumed a child of the base path
     * @param typeSuffix     the holder that will be set with the type suffix observed
     * @param moduleInfoPath the holder that will be set with the module info path
     * @param srcPath        the holder that will be set with the source path
     * @return the descriptor, or null if one cannot be found
     */
    static Optional<ModuleInfo> findModuleInfo(Path basePath,
                                               Path sourcePath,
                                               AtomicReference<String> typeSuffix,
                                               AtomicReference<File> moduleInfoPath,
                                               AtomicReference<File> srcPath) {
        Objects.requireNonNull(basePath);
        Objects.requireNonNull(sourcePath);
        // if we found a module-info in the source path, then that has to be the module to use
        Set<Path> moduleInfoPaths = findFile(sourcePath, ModuleInfo.FILE_NAME);
        if (1 == moduleInfoPaths.size()) {
            return finishModuleInfoDescriptor(moduleInfoPath, srcPath, moduleInfoPaths, ModuleInfo.FILE_NAME);
        }

        // if we did not find it, then there is a chance we are in the test directory; try to infer the module name
        String suffix = inferSourceOrTest(basePath, sourcePath);
        if (typeSuffix != null) {
            typeSuffix.set(suffix);
        }
        if (!basePath.equals(sourcePath)) {
            Path parent = sourcePath.getParent();
            moduleInfoPaths = findFile(parent, basePath, ModuleInfo.FILE_NAME);
            if (1 == moduleInfoPaths.size()) {
                // looks to be a potential test module, get the base name from the source tree...
                return finishModuleInfoDescriptor(moduleInfoPath, srcPath, moduleInfoPaths, ModuleInfo.FILE_NAME);
            } else if (moduleInfoPaths.size() > 0) {
                LOGGER.log(System.Logger.Level.WARNING, "ambiguous which module-info to select: " + moduleInfoPaths);
            }
        }

        // if we get to here then there was no "real" module-info file found anywhere in the target build directories
        // plan b: look for the injection generated files to infer the name
        Path parent = sourcePath.getParent();
        if (parent != null) {
            String fileName = String.valueOf(sourcePath.getFileName());
            Path scratch = parent.resolve("inject").resolve(fileName);
            moduleInfoPaths = findFile(scratch, scratch, ModuleInfo.FILE_NAME);
            if (1 == moduleInfoPaths.size()) {
                // looks to be a potential test module, get the base name from the source tree...
                return finishModuleInfoDescriptor(moduleInfoPath, srcPath, moduleInfoPaths, ModuleInfo.FILE_NAME);
            }
        }

        return Optional.empty();
    }

    /**
     * Common way for naming a module (generally for use by {@link io.helidon.inject.Application} and
     * {@link io.helidon.inject.service.ModuleComponent}).
     *
     * @param moduleName  the module name (from module-info)
     * @param typeSuffix  "test" for test, or null for normal src classes
     * @param defaultName the default name to return if it cannot be determined
     * @return the suggested module name or defaultName if it cannot be properly determined
     */
    static String toSuggestedModuleName(String moduleName,
                                        String typeSuffix,
                                        String defaultName) {
        if (moduleName == null && typeSuffix == null) {
            return defaultName;
        }
        if (moduleName == null) {
            moduleName = (defaultName == null) ? ModuleInfo.DEFAULT_MODULE_NAME : defaultName;
        }
        String suffix = typeSuffix == null || typeSuffix.isBlank()
                ? ""
                : "/" + typeSuffix;
        return (typeSuffix == null || moduleName.endsWith(suffix)) ? moduleName : moduleName + suffix;
    }

    /**
     * If the relative path contains "/test/" then returns "test" else returns "".
     *
     * @param basePath   the base path to start the search
     * @param sourcePath the source path, assumed a child of the base path
     * @return 'test' or ''
     */
    static String inferSourceOrTest(Path basePath,
                                    Path sourcePath) {
        // create a relative path from the two paths
        URI relativePath = basePath.toUri().relativize(sourcePath.toUri());
        String path = relativePath.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.contains("/test/") || path.contains("/generated-test-sources/") || path.contains("/test-classes/")) {
            return "test";
        }

        return "";
    }

    static String innerToSuggestedGeneratedPackageName(ModuleInfo descriptor,
                                                       Collection<TypeName> typeNames,
                                                       String defaultPackageName) {
        String export = null;
        if (descriptor != null) {
            List<TypeName> provides = descriptor.provides().get(InjectCodegenTypes.APPLICATION);
            if (provides == null || provides.isEmpty()) {
                provides = descriptor.provides().get(InjectCodegenTypes.MODULE_COMPONENT);
            }
            if (provides == null || provides.isEmpty()) {
                export = descriptor.firstUnqualifiedExport().orElse(null);
            } else {
                export = provides.getFirst().packageName();
            }
        }

        if (export == null && typeNames != null) {
            // default to the first one
            export = typeNames.stream()
                    .sorted()
                    .map(TypeName::packageName)
                    .findFirst().orElse(null);
        }

        return (export != null) ? export : defaultPackageName;
    }

    /**
     * Attempts to load the app package name from what was previously recorded.
     *
     * @param scratchPath the scratch directory path
     * @return the app package name that was loaded
     */
    static Optional<String> loadAppPackageName(Path scratchPath) {
        File scratchDir = scratchPath.toFile();
        File packageFileName = new File(scratchDir, APPLICATION_PACKAGE_FILE_NAME);
        if (packageFileName.exists()) {
            String packageName;
            try {
                packageName = Files.readString(packageFileName.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CodegenException("Unable to load: " + packageFileName, e);
            }

            if (packageName != null && !packageName.isBlank()) {
                return Optional.of(packageName);
            }
        }
        return Optional.empty();
    }

    private static Optional<ModuleInfo> finishModuleInfoDescriptor(AtomicReference<File> moduleInfoPath,
                                                                   AtomicReference<File> srcPath,
                                                                   Set<Path> moduleInfoPaths,
                                                                   String moduleInfoName) {
        File moduleInfoFile = new File(moduleInfoPaths.iterator().next().toFile(), moduleInfoName);
        if (moduleInfoPath != null) {
            moduleInfoPath.set(moduleInfoFile);
        }
        if (srcPath != null) {
            srcPath.set(moduleInfoFile.getParentFile());
        }
        return Optional.of(ModuleInfoSourceParser.parse(moduleInfoFile.toPath()));
    }

    private static Set<Path> findFile(Path target,
                                      String fileToFind) {
        Set<Path> result = new LinkedHashSet<>();

        Stack<Path> searchStack = new Stack<>();
        searchStack.add(target);

        while (!searchStack.isEmpty()) {
            Path cwdPath = searchStack.pop();
            if (!cwdPath.toFile().exists()) {
                continue;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cwdPath)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        searchStack.add(entry);
                    }

                    File file = new File(cwdPath.toFile(), fileToFind);
                    if (file.exists()) {
                        result.add(cwdPath);
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(System.Logger.Level.ERROR, "error while processing directory", ex);
            }
        }

        return result;
    }

    private static Set<Path> findFile(Path startPath,
                                      Path untilPath,
                                      String fileToFind) {
        if (startPath == null || !startPath.toString().contains(untilPath.toString())) {
            return Set.of();
        }

        do {
            Set<Path> set = findFile(startPath, fileToFind);
            if (!set.isEmpty()) {
                return set;
            }
            if (startPath.equals(untilPath)) {
                break;
            }
            startPath = startPath.getParent();
        } while (startPath != null);

        return Set.of();
    }
}
