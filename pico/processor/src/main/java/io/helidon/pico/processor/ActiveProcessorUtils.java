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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.ModuleInfoDescriptor;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ServicesToProcess;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import static io.helidon.pico.processor.GeneralProcessorUtils.hasValue;
import static io.helidon.pico.processor.GeneralProcessorUtils.toPath;
import static io.helidon.pico.tools.ModuleUtils.REAL_MODULE_INFO_JAVA_NAME;
import static io.helidon.pico.tools.ModuleUtils.inferSourceOrTest;

/**
 * Carries methods that are relative only during active APT processing.
 *
 * @see GeneralProcessorUtils
 */
final class ActiveProcessorUtils implements Messager {
    static final boolean MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR = false;
    static final String TARGET_DIR = "/target/";
    static final String SRC_MAIN_JAVA_DIR = "/src/main/java";

    private final System.Logger logger;
    private final ProcessingEnvironment processingEnv;
    private RoundEnvironment roundEnv;

    ActiveProcessorUtils(AbstractProcessor processor,
                         ProcessingEnvironment processingEnv,
                         RoundEnvironment roundEnv) {
        this.logger = System.getLogger(processor.getClass().getName());
        this.processingEnv = Objects.requireNonNull(processingEnv);
        this.roundEnv = roundEnv;

        Options.init(processingEnv);
        debug("*** Processing " + processor.getClass().getSimpleName() + " ***");
    }

    @Override
    public void debug(String message,
                      Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            if (logger.isLoggable(loggerLevel())) {
                logger.log(loggerLevel(), getClass().getSimpleName() + ": Debug: " + message, t);
            }
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, message);
        }
    }

    @Override
    public void debug(String message) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            if (logger.isLoggable(loggerLevel())) {
                logger.log(loggerLevel(), getClass().getSimpleName() + ": Debug: " + message);
            }
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, message);
        }
    }

    @Override
    public void log(String message) {
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    @Override
    public void warn(String message,
                     Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG) && t != null) {
            logger.log(System.Logger.Level.WARNING, getClass().getSimpleName() + ": Warning: " + message, t);
            t.printStackTrace();
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
        }
    }

    @Override
    public void warn(String message) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            logger.log(System.Logger.Level.WARNING, getClass().getSimpleName() + ": Warning: " + message);
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
        }
    }

    @Override
    public void error(String message,
                      Throwable t) {
        logger.log(System.Logger.Level.ERROR, getClass().getSimpleName() + ": Error: " + message, t);
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
        }
    }

    /**
     * Determines if the given type element is defined in the module being processed. If so then the return value is set to
     * {@code true} and the moduleName is cleared out. If not then the return value is set to {@code false} and the
     * {@code moduleName} is set to the module name if it has a qualified module name, and not from an internal java module system
     * type.
     *
     * @param type       the type element to analyze
     * @param moduleName the module name to populate if it is determinable
     * @return true if the type is defined in this module, false otherwise
     */
    boolean isTypeInThisModule(TypeElement type,
                               AtomicReference<String> moduleName) {
        moduleName.set(null);
        if (roundEnv != null && roundEnv.getRootElements().contains(type)) {
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
            debug("unable to determine if contract is external: " + type + "; " + t.getMessage(), t);

            // assumed external
            return false;
        }
    }

    /**
     * Attempts to load the {@link ModuleInfoDescriptor} for the (src or test) module being processed.
     *
     * @param typeSuffix     this function will populate this with an empty string for src and "test" for test
     * @param moduleInfoFile this function will populate this with the file path to the module-info source file
     * @param srcPath        this function will populate this with the source path
     * @return the module info descriptor if the module being processed has one available
     */
    // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
    Optional<ModuleInfoDescriptor> thisModuleDescriptor(AtomicReference<String> typeSuffix,
                                                        AtomicReference<File> moduleInfoFile,
                                                        AtomicReference<File> srcPath) {
        return tryFindModuleInfoTheConventionalWay(StandardLocation.SOURCE_OUTPUT, typeSuffix, moduleInfoFile, srcPath)
                .or(() -> tryFindModuleInfoTheConventionalWay(StandardLocation.SOURCE_PATH, typeSuffix, moduleInfoFile, srcPath))
                // attempt to retrieve from src/main/java if we can't recover to this point
                .or(() -> tryFindModuleInfoTheUnconventionalWayFromSourceMain(moduleInfoFile, srcPath));
    }

    /**
     * Determines the module being processed, and relays module-info information into the provided services to process.
     *
     * @param servicesToProcess the services to process instance
     */
    void relayModuleInfoToServicesToProcess(ServicesToProcess servicesToProcess) {
        // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
        AtomicReference<String> typeSuffix = new AtomicReference<>();
        AtomicReference<File> moduleInfoFile = new AtomicReference<>();
        AtomicReference<File> srcPath = new AtomicReference<>();
        ModuleInfoDescriptor thisModuleDescriptor = thisModuleDescriptor(typeSuffix, moduleInfoFile, srcPath).orElse(null);
        if (thisModuleDescriptor != null) {
            servicesToProcess.lastKnownModuleInfoDescriptor(thisModuleDescriptor);
        } else {
            String thisModuleName = Options.getOption(Options.TAG_MODULE_NAME).orElse(null);
            if (thisModuleName == null) {
                servicesToProcess.clearModuleName();
            } else {
                servicesToProcess.moduleName(thisModuleName);
            }
        }
        if (typeSuffix.get() != null) {
            servicesToProcess.lastKnownTypeSuffix(typeSuffix.get());
        }
        if (srcPath.get() != null) {
            servicesToProcess.lastKnownSourcePathBeingProcessed(srcPath.get().toPath());
        }
        if (moduleInfoFile.get() != null) {
            servicesToProcess.lastKnownModuleInfoFilePath(moduleInfoFile.get().toPath());
        }
    }

    System.Logger.Level loggerLevel() {
        return (Options.isOptionEnabled(Options.TAG_DEBUG)) ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
    }

    RoundEnvironment roundEnv() {
        return roundEnv;
    }

    void roundEnv(RoundEnvironment roundEnv) {
        this.roundEnv = roundEnv;
    }

    // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
    private Optional<ModuleInfoDescriptor> tryFindModuleInfoTheUnconventionalWayFromSourceMain(
            AtomicReference<File> moduleInfoFile,
            AtomicReference<File> srcPath) {
        if (srcPath != null && srcPath.get() != null && srcPath.get().getPath().contains(TARGET_DIR)) {
            String path = srcPath.get().getPath();
            int pos = path.indexOf(TARGET_DIR);
            path = path.substring(0, pos);
            File srcRoot = new File(path, SRC_MAIN_JAVA_DIR);
            File file = new File(srcRoot, REAL_MODULE_INFO_JAVA_NAME);
            if (file.exists()) {
                try {
                    return Optional.of(
                            ModuleInfoDescriptor.create(file.toPath(), ModuleInfoDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS));
                } catch (Exception e) {
                    debug("unable to read source module-info.java from: " + srcRoot + "; " + e.getMessage(), e);
                }

                if (moduleInfoFile != null) {
                    moduleInfoFile.set(file);
                }
            }
        }

        debug("unable to find module-info.java from: " + srcPath);
        return Optional.empty();
    }

    // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
    private Optional<ModuleInfoDescriptor> tryFindModuleInfoTheConventionalWay(StandardLocation location,
                                                                               AtomicReference<String> typeSuffix,
                                                                               AtomicReference<File> moduleInfoFile,
                                                                               AtomicReference<File> srcPath) {
        Filer filer = processingEnv.getFiler();
        try {
            FileObject f = filer.getResource(location, "", REAL_MODULE_INFO_JAVA_NAME);
            URI uri = f.toUri();
            Path filePath = toPath(uri).orElse(null);
            if (filePath != null) {
                Path parent = filePath.getParent();
                if (srcPath != null) {
                    srcPath.set(parent.toFile());
                }
                if (typeSuffix != null) {
                    String type = inferSourceOrTest(parent);
                    typeSuffix.set(type);
                }
                if (filePath.toFile().exists()) {
                    if (moduleInfoFile != null) {
                        moduleInfoFile.set(filePath.toFile());
                    }
                    return Optional.of(ModuleInfoDescriptor.create(filePath));
                }
            }
        } catch (Exception e) {
            debug("unable to retrieve " + location + " from filer: " + e.getMessage());
        }

        return Optional.empty();
    }

}
