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

package io.helidon.pico.tools;

import java.io.File;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

import javax.lang.model.element.TypeElement;

import io.helidon.pico.Application;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

import static io.helidon.pico.tools.CommonUtils.first;
import static io.helidon.pico.tools.CommonUtils.hasValue;

/**
 * Module specific utils.
 *
 * @deprecated
 */
public class ModuleUtils {
    static final System.Logger LOGGER = System.getLogger(ModuleUtils.class.getName());

    /**
     * The "real" module-info.java file name.
     */
    public static final String REAL_MODULE_INFO_JAVA_NAME = ModuleInfoDescriptor.DEFAULT_MODULE_INFO_JAVA_NAME;

    /**
     * The pico generated (e.g., module-info.java.pico) file name.
     */
    public static final String PICO_MODULE_INFO_JAVA_NAME = REAL_MODULE_INFO_JAVA_NAME + "." + PicoServicesConfig.NAME;

    static final String SERVICE_PROVIDER_MODULE_INFO_HBS = "module-info.hbs";
    static final String SRC_MAIN_JAVA_DIR = "/src/main/java";
    static final String SRC_TEST_JAVA_DIR = "/src/test/java";

    private ModuleUtils() {
    }

    /**
     * Returns the suggested package name to use.
     *
     * @param descriptor         optionally, the module-info descriptor
     * @param typeNames          optionally, the set of types that are being codegen'ed
     * @param defaultPackageName the default package name to use if all options are exhausted
     * @return the suggested package name
     */
    public static String toSuggestedGeneratedPackageName(
            ModuleInfoDescriptor descriptor,
            Collection<TypeName> typeNames,
            String defaultPackageName) {
        String export = null;

        if (descriptor != null) {
            Optional<ModuleInfoItem> provides = descriptor.first(Application.class.getName());
            if (provides.isEmpty() || provides.get().withOrTo().isEmpty()) {
                provides = descriptor.first(Module.class.getName());
            }
            if (provides.isEmpty() || provides.get().withOrTo().isEmpty()) {
                export = descriptor.firstUnqualifiedPackageExport().orElse(null);
            } else {
                export = DefaultTypeName
                        .createFromTypeName(first(provides.get().withOrTo(), false)).packageName();
            }
        }

        if (export == null && typeNames != null) {
            export = typeNames.stream()
                    .sorted()
                    .map(TypeName::packageName)
                    .findFirst().orElse(null);
        }

        return (export != null) ? export : defaultPackageName;
    }

    /**
     * Common way for naming a module (generally for use by {@link io.helidon.pico.Application} and
     * {@link io.helidon.pico.Module}).
     *
     * @param moduleName  the module name (from module-info)
     * @param typeSuffix  "test" for test, or null for normal src classes
     * @param defaultName the default name to return if it cannot be determined
     * @return the suggested module name or defaultName if it cannot be properly determined
     */
    static String toSuggestedModuleName(
            String moduleName,
            String typeSuffix,
            String defaultName) {
        if (moduleName == null && typeSuffix == null) {
            return defaultName;
        }
        if (moduleName == null) {
            moduleName = (defaultName == null) ? ModuleInfoDescriptor.DEFAULT_MODULE_NAME : defaultName;
        }
        String suffix = normalizedModuleNameTypeSuffix(typeSuffix);
        return (typeSuffix == null || moduleName.endsWith(suffix)) ? moduleName : moduleName + suffix;
    }

    /**
     * Returns the module's name.
     *
     * @param typeSuffix the type suffix.
     * @return the module name suffix
     */
    static String normalizedModuleNameTypeSuffix(
            String typeSuffix) {
        if (!hasValue(typeSuffix)) {
            return "";
        }
        return "/" + typeSuffix;
    }

    /**
     * Given either a base module name or test module name, will always return the base module name.
     *
     * @param moduleName the module name (base or test)
     * @return the base module name
     */
    public static String normalizedBaseModuleName(
            String moduleName) {
        if (!hasValue(moduleName)) {
            return moduleName;
        }
        int pos = moduleName.lastIndexOf("/");
        return (pos >= 0) ? moduleName.substring(0, pos) : moduleName;
    }

    /**
     * Extract the module name, first attempting the source path (test or main), and if not found using
     * the base path, presumably having basePath being a parent in the sourcePath hierarchy.
     *
     * @param basePath         the secondary path to try if module-info was not found in the source path
     * @param sourcePath       the source path
     * @param defaultToUnnamed if true, will return the default name, otherwise empty is returned
     * @return the module name suggested to use, most appropriate for the name of {@link
     *         io.helidon.pico.Application} or {@link io.helidon.pico.Module}
     */
    public static Optional<String> toSuggestedModuleName(
            Path basePath,
            Path sourcePath,
            boolean defaultToUnnamed) {
        AtomicReference<String> typeSuffix = new AtomicReference<>();
        ModuleInfoDescriptor descriptor = findModuleInfo(basePath, sourcePath, typeSuffix, null, null)
                .orElse(null);
        return Optional.ofNullable(toSuggestedModuleName((descriptor != null) ? descriptor.name() : null, typeSuffix.get(),
                                     defaultToUnnamed ? ModuleInfoDescriptor.DEFAULT_MODULE_NAME : null));
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
    static Optional<ModuleInfoDescriptor> findModuleInfo(
            Path basePath,
            Path sourcePath,
            AtomicReference<String> typeSuffix,
            AtomicReference<File> moduleInfoPath,
            AtomicReference<File> srcPath) {
        Objects.requireNonNull(basePath);
        Objects.requireNonNull(sourcePath);
        // if we found a module-info in the source path, then that has to be the module to use
        Set<Path> moduleInfoPaths = findFile(sourcePath, REAL_MODULE_INFO_JAVA_NAME);
        if (1 == moduleInfoPaths.size()) {
            return finishModuleInfoDescriptor(moduleInfoPath, srcPath, moduleInfoPaths, REAL_MODULE_INFO_JAVA_NAME);
        }

        // if we did not find it, then there is a chance we are in the test directory; try to infer the module name
        String suffix = inferSourceOrTest(basePath, sourcePath);
        if (typeSuffix != null) {
            typeSuffix.set(suffix);
        }
        if (!basePath.equals(sourcePath)) {
            Path parent = sourcePath.getParent();
            moduleInfoPaths = findFile(parent, basePath, REAL_MODULE_INFO_JAVA_NAME);
            if (1 == moduleInfoPaths.size()) {
                // looks to be a potential test module, get the base name from the source tree...
                return finishModuleInfoDescriptor(moduleInfoPath, srcPath, moduleInfoPaths, REAL_MODULE_INFO_JAVA_NAME);
            } else if (moduleInfoPaths.size() > 0) {
                LOGGER.log(System.Logger.Level.WARNING, "ambiguous which module-info to select: " + moduleInfoPaths);
            }
        }

        // if we get to here then there was no "real" module-info file found anywhere in the target build directories
        // plan b: look for the pico generated files to infer the name
        Path parent = sourcePath.getParent();
        if (parent != null) {
            String fileName = String.valueOf(sourcePath.getFileName());
            Path scratch = parent.resolve(PicoServicesConfig.NAME).resolve(fileName);
            moduleInfoPaths = findFile(scratch, scratch, PICO_MODULE_INFO_JAVA_NAME);
            if (1 == moduleInfoPaths.size()) {
                // looks to be a potential test module, get the base name from the source tree...
                return finishModuleInfoDescriptor(moduleInfoPath, srcPath, moduleInfoPaths, PICO_MODULE_INFO_JAVA_NAME);
            }
        }

        return Optional.empty();
    }

    private static Optional<ModuleInfoDescriptor> finishModuleInfoDescriptor(
            AtomicReference<File> moduleInfoPath,
            AtomicReference<File> srcPath,
            Set<Path> moduleInfoPaths,
            String moduleInfoName) {
        File moduleInfoFile = new File(first(moduleInfoPaths, false).toFile(), moduleInfoName);
        if (moduleInfoPath != null) {
            moduleInfoPath.set(moduleInfoFile);
        }
        if (srcPath != null) {
            srcPath.set(moduleInfoFile.getParentFile());
        }
        return Optional.of(ModuleInfoDescriptor.create(moduleInfoFile.toPath()));
    }

    /**
     * Attempts to infer 'test' or base '' given the path.
     *
     * @param path the path
     * @return 'test' or '' (for base non-test)
     */
    public static String inferSourceOrTest(
            Path path) {
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
     * If the relative path contains "/test/" then returns "test" else returns "".
     *
     * @param basePath   the base path to start the search
     * @param sourcePath the source path, assumed a child of the base path
     * @return 'test' or ''
     */
    static String inferSourceOrTest(
            Path basePath,
            Path sourcePath) {
        // create a relative path from the two paths
        URI relativePath = basePath.toUri().relativize(sourcePath.toUri());
        String path = relativePath.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.contains("/test/") || path.contains("/generated-test-sources/") || path.contains("/test-classes/")) {
            return ActivatorCreatorCodeGen.DEFAULT_TEST_CLASS_PREFIX_NAME;
        }

        return ActivatorCreatorCodeGen.DEFAULT_CLASS_PREFIX_NAME;
    }

    /**
     * Translates to the source path coordinate given a source file and type name.
     * Only available during annotation processing.
     *
     * @param optFile the source file
     * @param type the type name
     * @return the source path, or empty if it cannot be inferred
     */
    public static Optional<Path> toSourcePath(
            Optional<Path> optFile,
            TypeElement type) {
        if (optFile.isEmpty()) {
            return Optional.empty();
        }
        TypeName typeName = TypeTools.createTypeNameFromElement(type).orElseThrow();
        Path typePath = Paths.get(TypeTools.toFilePath(typeName));
        Path filePath = optFile.get();
        if (filePath.endsWith(typePath)) {
            return Optional.of(filePath.resolveSibling(typePath));
        }
        return Optional.empty();
    }

    /**
     * Returns the base module path given its source path.
     *
     * @param sourcePath the source path
     * @return the base path
     */
    public static Path toBasePath(
            String sourcePath) {
        int pos = sourcePath.lastIndexOf(SRC_MAIN_JAVA_DIR);
        if (pos < 0) {
            pos = sourcePath.lastIndexOf(SRC_TEST_JAVA_DIR);
        }
        if (pos < 0) {
            throw new ToolsException("invalid source path: " + sourcePath);
        }
        Path path = Path.of(sourcePath.substring(0, pos));
        return Objects.requireNonNull(path);
    }

    /**
     * Will return non-empty File if the uri represents a local file on the fs.
     *
     * @param uri the uri of the artifact
     * @return the file instance, or empty if not local
     */
    public static Optional<Path> toPath(
            URI uri) {
        if (uri.getHost() != null) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(uri));
    }

    /**
     * Returns true if the given module name is unnamed.
     *
     * @param moduleName the module name to check
     * @return true if the provided module name is unnamed
     */
    public static boolean isUnnamedModuleName(
            String moduleName) {
        return !hasValue(moduleName)
                || moduleName.equals(ModuleInfoDescriptor.DEFAULT_MODULE_NAME)
                || moduleName.equals(ModuleInfoDescriptor.DEFAULT_MODULE_NAME + "/" + ModuleInfoDescriptor.DEFAULT_TEST_SUFFIX);
    }

    private static Set<Path> findFile(
            Path startPath,
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

    private static Set<Path> findFile(
            Path target,
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

}
