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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
 */
class ModuleUtils {
    static final System.Logger LOGGER = System.getLogger(ModuleUtils.class.getName());

    /**
     * The "real" module-info.java file name.
     */
    static final String REAL_MODULE_INFO_JAVA_NAME = ModuleInfoDescriptor.DEFAULT_MODULE_INFO_JAVA_NAME;

    /**
     * The pico generated module-info.java.pico file name.
     */
    static final String PICO_MODULE_INFO_JAVA_NAME = REAL_MODULE_INFO_JAVA_NAME + "." + PicoServicesConfig.NAME;

    /**
     * The tag for the module name.
     */
    // the standard java module-info's name
    static final String TAG_MODULE_NAME = ModuleInfoDescriptor.TAG_MODULE_NAME;

    /**
     * The tag fpr the pico module name.
     */
    static final String TAG_PICO_MODULE_NAME = PicoServicesConfig.FQN + "." + TAG_MODULE_NAME;

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
    static String toSuggestedGeneratedPackageName(
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
            moduleName = (defaultName == null) ? ModuleInfoDescriptor.DEFAULT_MODULE_NAME : moduleName;
        }
        return (typeSuffix == null) ? moduleName : moduleName + normalizedModuleNameTypeSuffix(typeSuffix);
    }

    /**
     * Returns the module's {@link io.helidon.pico.Named} name.
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
     * @param defaultToUnnamed if true, will return the default name, otherwise null is returned
     * @return the module name suggested to use, most appropriate for the name of {@link
     *         io.helidon.pico.Application} or {@link io.helidon.pico.Module}
     */
    static String toSuggestedModuleName(
            Path basePath,
            Path sourcePath,
            boolean defaultToUnnamed) {
        AtomicReference<String> typeSuffix = new AtomicReference<>();
        ModuleInfoDescriptor descriptor = findModuleInfo(basePath, sourcePath, typeSuffix, null, null);
        return toSuggestedModuleName((descriptor != null) ? descriptor.name() : null, typeSuffix.get(),
                                     defaultToUnnamed ? ModuleInfoDescriptor.DEFAULT_MODULE_NAME : null);
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
    static ModuleInfoDescriptor findModuleInfo(
            Path basePath,
            Path sourcePath,
            AtomicReference<String> typeSuffix,
            AtomicReference<File> moduleInfoPath,
            AtomicReference<File> srcPath) {
        // if we found a module-info in the source path, then that has to be the module to use
        Set<Path> moduleInfoPaths = findFile(sourcePath, ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
        if (1 == moduleInfoPaths.size()) {
            File moduleInfoFile = new File(first(moduleInfoPaths, false).toFile(),
                                           ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
            if (moduleInfoPath != null) {
                moduleInfoPath.set(moduleInfoFile);
            }
            if (srcPath != null) {
                srcPath.set(moduleInfoFile.getParentFile());
            }
            return ModuleInfoDescriptor.create(moduleInfoFile.toPath());
        }

        // if we did not find it, then there is a chance we are in the tests directory; try to infer the module name
        if (basePath != null) {
            if (typeSuffix != null) {
                typeSuffix.set(inferSourceOrTest(basePath, sourcePath));
            }
            if (!basePath.equals(sourcePath)) {
                moduleInfoPaths = findFile(sourcePath.getParent(), basePath, ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
                if (1 == moduleInfoPaths.size()) {
                    // looks to be a potential test module, get the base name from the source tree...
                    File moduleInfoFile = new File(first(moduleInfoPaths, false).toFile(),
                                                   ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
                    if (moduleInfoPath != null) {
                        moduleInfoPath.set(moduleInfoFile);
                    }
                    if (srcPath != null) {
                        srcPath.set(moduleInfoFile.getParentFile());
                    }
                    return ModuleInfoDescriptor.create(moduleInfoFile.toPath());
                } else if (moduleInfoPaths.size() > 0) {
                    LOGGER.log(System.Logger.Level.WARNING, "ambiguous which module-info to select: " + moduleInfoPaths);
                }
            }
        }

        return null;
    }

    /**
     * Attempts to infer 'test' or base (null) given the path.
     *
     * @param path the path
     * @return 'test' or null (for base)
     */
    static String inferSourceOrTest(
            Path path) {
        return inferSourceOrTest(path.getParent().getParent(), path);
    }

    /**
     * If the relative path contains "/test/" then returns "test" else returns null.
     *
     * @param basePath   the base path to start the search
     * @param sourcePath the source path, assumed a child of the base path
     * @return 'test' or null (for base)
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
            return ModuleInfoDescriptor.DEFAULT_TEST_SUFFIX;
        }

        return null;
    }

    /**
     * Will return non-null File iff the uri represents a local file on the fs.
     *
     * @param uri the uri of the artifact
     * @return the file instance, or null if not local.
     */
    static File toFile(
            URI uri) {
        if (uri == null || uri.getHost() != null) {
            return null;
        }
        return new File(uri.getPath());
    }

    /**
     * Translates to the source path coordinate given a source file and type name.
     * Only available during annotation processing.
     *
     * @param file the source file
     * @param type the type name
     * @return the source path
     */
    static File toSourcePath(
            File file,
            TypeElement type) {
        TypeName typeName = TypeTools.createTypeNameFromElement(type).orElseThrow();
        String typePath = TypeTools.toFilePath(typeName);
        String filePath = file.getPath();
        if (filePath.endsWith(typePath)) {
            filePath = filePath.substring(0, filePath.length() - typePath.length());
            return new File(filePath);
        }
        return null;
    }

    private static Set<Path> findFile(
            Path startPath,
            Path untilPath,
            String fileToFind) {
        if (!startPath.toString().contains(untilPath.toString())) {
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

        return Collections.emptySet();
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
