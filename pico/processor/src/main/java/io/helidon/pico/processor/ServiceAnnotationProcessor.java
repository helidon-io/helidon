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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.helidon.pico.tools.ModuleInfoDescriptor;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;

import static io.helidon.pico.processor.ProcessorUtils.rootStackTraceElementOf;
import static io.helidon.pico.processor.ProcessorUtils.toPath;
import static io.helidon.pico.tools.ModuleUtils.REAL_MODULE_INFO_JAVA_NAME;
import static io.helidon.pico.tools.ModuleUtils.inferSourceOrTest;

/**
 * Processor for @{@link jakarta.inject.Singleton} type annotations.
 */
public class ServiceAnnotationProcessor extends BaseAnnotationProcessor<Void> {

    private static final Set<String> SUPPORTED_TARGETS = Set.of(
            TypeNames.JAKARTA_SINGLETON,
            TypeNames.PICO_EXTERNAL_CONTRACTS,
            TypeNames.PICO_INTERCEPTED,
            TypeNames.JAVAX_SINGLETON,
            TypeNames.JAKARTA_APPLICATION_SCOPED,
            TypeNames.JAVAX_APPLICATION_SCOPED
    );

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ServiceAnnotationProcessor() {
    }

    @Override
    protected Set<String> annoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    protected Set<String> contraAnnotations() {
        return Set.of(TypeNames.PICO_CONFIGURED_BY);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        try {
            if (!roundEnv.processingOver()
                    && (servicesToProcess().moduleName() == null)) {
                // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
                AtomicReference<String> typeSuffix = new AtomicReference<>();
                AtomicReference<File> moduleInfoFile = new AtomicReference<>();
                AtomicReference<File> srcPath = new AtomicReference<>();
                ModuleInfoDescriptor thisModuleDescriptor =
                        getThisModuleDescriptor(typeSuffix, moduleInfoFile, srcPath);
                if (thisModuleDescriptor != null) {
                    servicesToProcess().lastKnownModuleInfoDescriptor(thisModuleDescriptor);
                } else {
                    String thisModuleName = getThisModuleName(null);
                    if (thisModuleName == null) {
                        servicesToProcess().clearModuleName();
                    } else {
                        servicesToProcess().moduleName(thisModuleName);
                    }
                }
                if (typeSuffix.get() != null) {
                    servicesToProcess().lastKnownTypeSuffix(typeSuffix.get());
                }
                if (srcPath.get() != null) {
                    servicesToProcess().lastKnownSourcePathBeingProcessed(srcPath.get().toPath());
                }
                if (moduleInfoFile.get() != null) {
                    servicesToProcess().lastKnownModuleInfoFilePath(moduleInfoFile.get().toPath());
                }
            }

            return super.process(annotations, roundEnv);
        } catch (Throwable t) {
            error(getClass().getSimpleName() + " error during processing; " + t
                          + " @ " + rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("error during processing: " + t
                                             + " @ " + rootStackTraceElementOf(t), t);
        } finally {
            if (roundEnv.processingOver()) {
                servicesToProcess().clearModuleName();
            }
        }
    }

    String getThisModuleName(ModuleInfoDescriptor descriptor) {
        // user pass it in? if so then that will short-circuit the name calculation part
        String moduleName = Options.getOption(Options.TAG_MODULE_NAME).orElse(null);
        if (moduleName != null) {
            return moduleName;
        }
        return (descriptor == null) ? null : descriptor.name();
    }

    // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
    ModuleInfoDescriptor getThisModuleDescriptor(AtomicReference<String> typeSuffix,
                                                 AtomicReference<File> moduleInfoFile,
                                                 AtomicReference<File> srcPath) {
        return tryFindModuleInfo(StandardLocation.SOURCE_OUTPUT, typeSuffix, moduleInfoFile, srcPath)
                        .or(() -> tryFindModuleInfo(StandardLocation.SOURCE_PATH, typeSuffix, moduleInfoFile, srcPath))
                        // attempt to retrieve from src/main/java if we can't recover to this point
                        .or(() -> getThisModuleDescriptorFromSourceMain(moduleInfoFile, srcPath))
                .orElse(null);
    }

    // note: Atomic here is merely a convenience as a pass-by-reference holder, no async is actually needed here
    Optional<ModuleInfoDescriptor> getThisModuleDescriptorFromSourceMain(AtomicReference<File> moduleInfoFile,
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
    Optional<ModuleInfoDescriptor> tryFindModuleInfo(StandardLocation location,
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
