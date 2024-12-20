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

package io.helidon.inject.tools;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Activator;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.ModuleComponent;

/**
 * This class is used to generate the source and resources originating from either annotation processing or maven-plugin
 * invocation. It also provides a circuit breaker in case the filer should be disabled from actually writing out source
 * and resources, and instead will use the filer's messager to report what it would have performed (applicable for apt cases).
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class CodeGenFiler {
    private static final boolean FORCE_MODULE_INFO_INTO_SCRATCH_DIR = true;
    private static final boolean FILER_WRITE_ONCE_PER_TYPE = true;
    private static final Set<TypeName> FILER_TYPES_FILED = new LinkedHashSet<>();
    private static volatile boolean filerWriteEnabled = true;

    private final AbstractFilerMessager filer;
    private final Boolean enabled;
    private final Path targetClassOutputPath;
    private final Path scratchBaseOutputPath;
    private final Path scratchClassClassOutputPath;

    /**
     * Constructor.
     *
     * @param filer the filer to use for creating resources
     */
    CodeGenFiler(AbstractFilerMessager filer) {
        this(filer, null);
    }

    /**
     * Constructor.
     *
     * @param filer the filer to use for creating resources
     * @param enabled true if forcing enablement, false if forcing disablement, null for using defaults
     */
    CodeGenFiler(AbstractFilerMessager filer,
                 Boolean enabled) {
        this.filer = Objects.requireNonNull(filer);
        this.enabled = enabled;
        this.targetClassOutputPath = targetClassOutputPath(filer);
        this.scratchClassClassOutputPath = scratchClassOutputPath(targetClassOutputPath);
        this.scratchBaseOutputPath = scratchClassClassOutputPath.getParent();
    }

    /**
     * Creates a new code gen filer.
     *
     * @param filer the physical filer
     * @return a newly created code gen filer
     */
    public static CodeGenFiler create(AbstractFilerMessager filer) {
        return new CodeGenFiler(filer);
    }

    /**
     * Provides the ability to disable actual file writing (convenient for unit testing). The default is true for
     * enabled.
     *
     * @param enabled if disabled, pass false
     * @return the previous value of this setting
     */
    static boolean filerWriterEnabled(boolean enabled) {
        boolean prev = filerWriteEnabled;
        filerWriteEnabled = enabled;
        return prev;
    }

    /**
     * Returns the path to the target scratch directory for Service.
     *
     * @param targetOutputPath the target class output path
     * @return the target scratch path
     */
    public static Path scratchClassOutputPath(Path targetOutputPath) {
        Path fileName = targetOutputPath.getFileName();
        Path parent = targetOutputPath.getParent();
        if (fileName == null || parent == null) {
            throw new IllegalStateException(targetOutputPath.toString());
        }
        String name = fileName.toString();
        return parent.resolve("inject").resolve(name);
    }

    /**
     * Returns the target class output directory.
     *
     * @param filer the filer
     * @return the path to the target class output directory
     */
    public static Path targetClassOutputPath(Filer filer) {
        if (filer instanceof AbstractFilerMessager.DirectFilerMessager) {
            CodeGenPaths paths = ((AbstractFilerMessager.DirectFilerMessager) filer).codeGenPaths();
            return Path.of(paths.outputPath().orElseThrow());
        }

        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", "___");
            Path path = Path.of(f.toUri());
            if (path.getParent() == null) {
                throw new IllegalStateException(path.toString());
            }
            return path.getParent();
        } catch (Exception e) {
            throw new ToolsException("Unable to determine output path", e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(outputPath=" + targetClassOutputPath + ")";
    }

    boolean filerWriterEnabled() {
        return (enabled != null) ? enabled : filerWriteEnabled;
    }

    AbstractFilerMessager filer() {
        return filer;
    }

    Messager messager() {
        return filer;
    }

    /**
     * Generate the meta-inf services given the provided map.
     *
     * @param paths           paths to where code should be written
     * @param metaInfServices the meta-inf services mapping
     */
    public void codegenMetaInfServices(CodeGenPaths paths,
                                       Map<String, List<String>> metaInfServices) {
        if (metaInfServices == null || metaInfServices.isEmpty()) {
            return;
        }

        Filer filer = filer();
        Messager messager = messager();
        Map<String, Set<String>> mergedMap = new LinkedHashMap<>();
        // load up any existing values, since this compilation may be partial and be run again...
        for (Map.Entry<String, List<String>> e : metaInfServices.entrySet()) {
            String contract = e.getKey();
            Set<String> mergedSet = new LinkedHashSet<>(e.getValue());
            mergedMap.put(contract, mergedSet);
            String outPath = new File(paths.metaInfServicesPath()
                                              .orElse(CodeGenPaths.DEFAULT_META_INF_SERVICES_PATH), contract).getPath();
            try {
                messager.debug("Reading " + outPath);
                FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", outPath);
                try (InputStream is = f.openInputStream();
                        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        mergedSet.add(line);
                    }
                }
            } catch (FilerException | NoSuchFileException | FileNotFoundException x) {
                // don't show the exception in this case
                messager.debug(getClass().getSimpleName() + ":" + x.getMessage());
            } catch (Exception x) {
                ToolsException te =
                        new ToolsException("Failed to find/load existing META-INF/services file: " + x.getMessage(), x);
                messager.warn(te.getMessage(), te);
            }
        }

        for (Map.Entry<String, Set<String>> e : mergedMap.entrySet()) {
            String contract = e.getKey();
            String outPath = new File(paths.metaInfServicesPath().orElseThrow(), contract).getPath();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                for (String value : e.getValue()) {
                    pw.println(value);
                }
            }

            codegenResourceFilerOut(outPath, baos.toString(StandardCharsets.UTF_8));
        }
    }

    /**
     * Code generates a resource.
     *
     * @param outPath   the path to output the resource to
     * @param body      the resource body
     * @return file path coordinates corresponding to the resource in question, or empty if not generated
     */
    public Optional<Path> codegenResourceFilerOut(String outPath,
                                                  String body) {
        return codegenResourceFilerOut(outPath, body, Optional.empty());
    }

    /**
     * Code generates a resource, providing the ability to update the body if the resource already exists.
     *
     * @param outPath   the path to output the resource to
     * @param body      the resource body
     * @param updater   the updater of the body
     * @return file path coordinates corresponding to the resource in question, or empty if not generated
     */
    Optional<Path> codegenResourceFilerOut(String outPath,
                                           String body,
                                           Function<InputStream, String> updater) {
        return codegenResourceFilerOut(outPath, body, Optional.of(updater));
    }

    /**
     * Code generates the {@link ModuleComponent} source.
     *
     * @param moduleDetail the module details
     */
    void codegenModuleFilerOut(ModuleDetail moduleDetail) {
        if (moduleDetail.moduleBody().isEmpty()) {
            return;
        }

        TypeName typeName = moduleDetail.moduleTypeName();
        String body = moduleDetail.moduleBody().orElseThrow();
        codegenJavaFilerOut(typeName, body);
    }

    /**
     * Code generates the {@link Application} source.
     *
     * @param applicationTypeName the application type
     * @param body                the application body of source
     */
    void codegenApplicationFilerOut(TypeName applicationTypeName,
                                    String body) {
        codegenJavaFilerOut(applicationTypeName, body);
    }

    /**
     * Code generates the {@link Activator} source.
     *
     * @param activatorDetail the activator details
     */
    void codegenActivatorFilerOut(ActivatorCodeGenDetail activatorDetail) {
        if (activatorDetail.body().isEmpty()) {
            return;
        }

        TypeName typeName = activatorDetail.serviceTypeName();
        String body = activatorDetail.body().orElseThrow();
        codegenJavaFilerOut(typeName, body);
    }

    /**
     * Code generate a java source file.
     *
     * @param typeName the source type name
     * @param body     the source body
     * @return the new file path coordinates or empty if nothing was written
     */
    public Optional<Path> codegenJavaFilerOut(TypeName typeName,
                                              String body) {
        Messager messager = messager();
        if (!filerWriterEnabled()) {
            messager.log("(disabled) Writing " + typeName + " with:\n" + body);
            return Optional.empty();
        }

        if (FILER_WRITE_ONCE_PER_TYPE && !FILER_TYPES_FILED.add(typeName)) {
            ToolsException te = new ToolsException("Attempt to reprocess: " + typeName);
            messager.error(te.getMessage(), te);
        }

        messager.debug("Writing " + typeName);

        Filer filer = filer();
        try {
            JavaFileObject javaSrc = filer.createSourceFile(typeName.name());
            try (Writer os = javaSrc.openWriter()) {
                os.write(body);
            }
            return Optional.of(Path.of(javaSrc.toUri()));
        } catch (Exception x) {
            ToolsException te = new ToolsException("Failed to write java file: " + typeName, x);
            messager.error(te.getMessage(), te);
            throw te;
        }
    }

    /**
     * Code generate the {@code module-info.java.inject} file.
     *
     * @param newDeltaDescriptor      the descriptor
     * @param overwriteTargetIfExists should the file be overwritten if it already exists
     * @return the module-info coordinates, or empty if nothing was written
     */
    Optional<Path> codegenModuleInfoFilerOut(ModuleInfoDescriptor newDeltaDescriptor,
                                             boolean overwriteTargetIfExists) {
        Objects.requireNonNull(newDeltaDescriptor);

        Messager messager = messager();
        String typeName = ModuleUtils.MODULE_INFO_JAVA_NAME;
        if (!filerWriterEnabled()) {
            messager.log("(disabled) Writing " + typeName + " with:\n" + newDeltaDescriptor);
            return Optional.empty();
        }
        messager.debug("Writing " + typeName);

        Function<InputStream, String> moduleInfoUpdater = inputStream -> {
            ModuleInfoDescriptor existingDescriptor = ModuleInfoDescriptor.create(inputStream);
            ModuleInfoDescriptor newDescriptor = existingDescriptor.mergeCreate(newDeltaDescriptor);
            return newDescriptor.contents();
        };

        Optional<Path> filePath
                = codegenResourceFilerOut(typeName, newDeltaDescriptor.contents(), moduleInfoUpdater);
        if (filePath.isPresent()) {
            messager.debug("Wrote module-info: " + filePath.get());
        } else if (overwriteTargetIfExists) {
            messager.warn("Expected to have written module-info, but failed to write it");
        }

        if (!newDeltaDescriptor.isUnnamed()) {
            ModuleUtils.saveAppPackageName(scratchBaseOutputPath,
                                           ModuleUtils.normalizedBaseModuleName(newDeltaDescriptor.name()));
        }

        return filePath;
    }

    /**
     * Reads in the module-info if it exists, or returns null if it doesn't exist.
     *
     * @param name the name to the module-info file
     * @return the module-info descriptor, or empty if it doesn't exist
     */
    Optional<ModuleInfoDescriptor> readModuleInfo(String name) {
        try {
            CharSequence body = readResourceAsString(name);
            return Optional.ofNullable((body == null) ? null : ModuleInfoDescriptor.create(body.toString()));
        } catch (Exception e) {
            throw new ToolsException("Failed to read module-info: " + name, e);
        }
    }

    /**
     * Reads in a resource from the {@link javax.tools.StandardLocation#CLASS_OUTPUT} location.
     *
     * @param name the name of the resource
     * @return the body of the resource as a string, or null if it doesn't exist
     */
    CharSequence readResourceAsString(String name) {
        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", name);
            return f.getCharContent(true);
        } catch (IOException e) {
            if (FORCE_MODULE_INFO_INTO_SCRATCH_DIR
                    && targetClassOutputPath != null
                    && name.equals(ModuleUtils.MODULE_INFO_JAVA_NAME)) {
                // hack: physically read it from its relocated location
                File newPath = new File(scratchClassClassOutputPath.toFile(), name);
                if (newPath.exists()) {
                    try {
                        return Files.readString(newPath.toPath());
                    } catch (IOException e2) {
                        throw new ToolsException(e2.getMessage(), e2);
                    }
                }
            }

            messager().debug("Unable to load resource: " + name);
            return null;
        }
    }

    /**
     * Attempts to translate the resource name to a file coordinate, or null if translation is not possible.
     *
     * @param name the name of the resource
     * @return the file path coordinates if it can be ascertained, or empty if not possible to ascertain this information
     */
    Optional<Path> toResourceLocation(String name) {
        // hack: physically read it from its relocated location
        if (FORCE_MODULE_INFO_INTO_SCRATCH_DIR
                && targetClassOutputPath != null
                && name.equals(ModuleUtils.MODULE_INFO_JAVA_NAME)) {
            return Optional.of(scratchClassClassOutputPath.resolve(name));
        }

        try {
            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", name);
            Path path = Paths.get(f.toUri());
            return Optional.of(path);
        } catch (IOException e) {
            messager().debug("unable to load resource: " + name);
        }

        return Optional.empty();
    }

    /**
     * Attempts to translate the type name to a file coordinate, or empty if translation is not possible.
     *
     * @param name the name of the type
     * @return the file coordinates if it can be ascertained, or empty if not possible to ascertain this information
     *
     * @see ModuleUtils#toSourcePath for annotation processing use cases
     */
    Optional<Path> toSourceLocation(String name) {
        if (filer instanceof AbstractFilerMessager.DirectFilerMessager) {
            TypeName typeName = TypeName.create(name);
            Optional<Path> path = Optional.ofNullable(((AbstractFilerMessager.DirectFilerMessager) filer)
                                                              .toSourcePath(StandardLocation.SOURCE_PATH, typeName));
            if (path.isPresent()) {
                return path;
            }
        }

        messager().log(CodeGenFiler.class.getSimpleName() + ": unable to determine source location for: " + name);
        return Optional.empty();
    }

    private Filer scratchFiler() throws IOException {
        Files.createDirectories(scratchClassClassOutputPath);
        CodeGenPaths codeGenPaths = CodeGenPaths.builder()
                .outputPath(scratchClassClassOutputPath.toString())
                .build();
        return new AbstractFilerMessager.DirectFilerMessager(codeGenPaths, filer.logger());
    }

    private Optional<Path> codegenResourceFilerOut(String outPath,
                                                   String body,
                                                   Optional<Function<InputStream, String>> optFnUpdater) {
        Messager messager = messager();
        if (!filerWriterEnabled()) {
            messager.log("(disabled) Writing " + outPath + " with:\n" + body);
            return Optional.empty();
        }
        messager.debug("Writing " + outPath);

        Filer filer = filer();
        Function<InputStream, String> fnUpdater = optFnUpdater.orElse(null);

        try {
            if (FORCE_MODULE_INFO_INTO_SCRATCH_DIR
                    && targetClassOutputPath != null
                    && outPath.equals(ModuleUtils.MODULE_INFO_JAVA_NAME)) {
                // hack: physically relocate it elsewhere under our scratch output directory
                filer = scratchFiler();
            }

            FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "", outPath);
            Path fPath = Path.of(f.toUri());
            if (fPath.toFile().exists()) {
                if (fnUpdater != null) {
                    // update it...
                    try (InputStream is = f.openInputStream()) {
                        body = fnUpdater.apply(is);
                    }
                }

                String actualContents = Files.readString(fPath, StandardCharsets.UTF_8);
                if (!actualContents.equals(body)) {
                    Files.writeString(fPath, body, StandardCharsets.UTF_8);
                }
            } else {
                // file does not exist yet... create it the normal way
                f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", outPath);
                try (Writer os = f.openWriter()) {
                    os.write(body);
                }
                fPath = Path.of(f.toUri());
            }

            return Optional.of(fPath);
        } catch (Exception x) {
            ToolsException te = new ToolsException("Error writing resource file: " + x.getMessage(), x);
            messager.error(te.getMessage(), te);
            // should not make it here
            throw te;
        }
    }

}
