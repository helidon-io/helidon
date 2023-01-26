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
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.helidon.pico.ExternalContracts;
import io.helidon.pico.Intercepted;
import io.helidon.pico.tools.CommonUtils;
import io.helidon.pico.tools.JavaxTypeTools;
import io.helidon.pico.tools.ModuleInfoDescriptor;
import io.helidon.pico.tools.ModuleUtils;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeTools;

import jakarta.inject.Singleton;

/**
 * Processor for @{@link jakarta.inject.Singleton} type annotations.
 */
public class ServiceAnnotationProcessor extends BaseAnnotationProcessor<Void> {
    private static final Set<Class<? extends Annotation>> SUPPORTED_TARGETS;
    private static Class<? extends Annotation> javaxSingletonType;
    static {
        SUPPORTED_TARGETS = new HashSet<>();
        SUPPORTED_TARGETS.add(Singleton.class);
        SUPPORTED_TARGETS.add(ExternalContracts.class);
        SUPPORTED_TARGETS.add(Intercepted.class);
        addJavaxTypes(SUPPORTED_TARGETS);
    }

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public ServiceAnnotationProcessor() {
    }

    private static void addJavaxTypes(
            Set<Class<? extends Annotation>> supportedTargets) {
        if (javaxSingletonType != null) {
            return;
        }

        try {
            javaxSingletonType = JavaxTypeTools.INSTANCE.get()
                    .loadAnnotationClass(TypeTools.oppositeOf(Singleton.class.getName())).orElse(null);
            if (javaxSingletonType != null) {
                supportedTargets.add(javaxSingletonType);
            }
            Class<? extends Annotation> applicationScoped = JavaxTypeTools.INSTANCE.get()
                    .loadAnnotationClass(UnsupportedConstructsProcessor.APPLICATION_SCOPED_TYPE_NAME).orElse(null);
            if (applicationScoped != null) {
                supportedTargets.add(applicationScoped);
            }
        } catch (Throwable t) {
            // normal
        }
    }

    @Override
    Set<Class<? extends Annotation>> annoTypes() {
        return Set.copyOf(SUPPORTED_TARGETS);
    }

    @Override
    Set<String> contraAnnotations() {
        return Set.of(CONFIGURED_BY_TYPENAME);
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        try {
            if (!roundEnv.processingOver()
                    && (servicesToProcess().moduleName() == null)) {
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
                          + " @ " + CommonUtils.rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("error during processing: " + t
                                             + " @ " + CommonUtils.rootStackTraceElementOf(t), t);
        } finally {
            if (roundEnv.processingOver()) {
                servicesToProcess().clearModuleName();
            }
        }
    }

    String getThisModuleName(
            ModuleInfoDescriptor descriptor) {
        // user pass it in? if so then that will short-circuit the name calculation part
        String moduleName = Options.getOption(Options.TAG_MODULE_NAME).orElse(null);
        if (moduleName != null) {
            return moduleName;
        }
        return (descriptor == null) ? null : descriptor.name();
    }

    ModuleInfoDescriptor getThisModuleDescriptor(
            AtomicReference<String> typeSuffix,
            AtomicReference<File> moduleInfoFile,
            AtomicReference<File> srcPath) {
        ModuleInfoDescriptor descriptor = tryFindModuleInfo(StandardLocation.SOURCE_OUTPUT,
                                                              typeSuffix,
                                                              moduleInfoFile,
                                                              srcPath);
        if (descriptor != null) {
            descriptor = tryFindModuleInfo(StandardLocation.SOURCE_PATH, typeSuffix, moduleInfoFile, srcPath);
            if (descriptor != null) {
                // attempt to retrieve from src/main/java if we can't recover to this point
                descriptor = getThisModuleDescriptorFromSourceMain(moduleInfoFile, srcPath);
            }
        }
        // note to self: this seems worthy to add at some point
        //        if (Objects.nonNull(srcPath) && Objects.isNull(srcPath.get())) {
        //            // try to determine the current source path..
        //            try {
        //                Filer filer = processingEnv.getFiler();
        //                for (Element e : roundEnv.getRootElements()) {
        //                    if (!(e instanceof TypeElement)) {
        //                        continue;
        //                    }
        //                    TypeName typeName = TypeNameImpl.toName(e);
        //                    FileObject f = filer.getResource(StandardLocation.SOURCE_PATH, typeName.getPackageName
        //                    (), typeName.getClassName() + ".java");
        //                    File location = ModuleUtils.toFile(f.toUri());
        //                }
        //            } catch (Exception e) {
        //                debug("unable to determine source path: " + e.getMessage(), null);
        //            }
        //        }
        return descriptor;
    }

    ModuleInfoDescriptor getThisModuleDescriptorFromSourceMain(
            AtomicReference<File> moduleInfoFile,
            AtomicReference<File> srcPath) {
        if (srcPath != null && srcPath.get() != null && srcPath.get().getPath().contains(TARGET_DIR)) {
            String path = srcPath.get().getPath();
            int pos = path.indexOf(TARGET_DIR);
            path = path.substring(0, pos);
            File srcRoot = new File(path, SRC_MAIN_JAVA_DIR);
            File file = new File(srcRoot, ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
            if (file.exists()) {
                try {
                    return ModuleInfoDescriptor.create(file.toPath(), ModuleInfoDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
                } catch (Exception e) {
                    debug("unable to read source module-info.java from: " + srcRoot + "; " + e.getMessage(), e);
                }

                if (moduleInfoFile != null) {
                    moduleInfoFile.set(file);
                }
            }
        }

        return null;
    }

    ModuleInfoDescriptor tryFindModuleInfo(
            StandardLocation location,
            AtomicReference<String> typeSuffix,
            AtomicReference<File> moduleInfoFile,
            AtomicReference<File> srcPath) {
        Filer filer = processingEnv.getFiler();
        try {
            FileObject f = filer.getResource(location, "", ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
            URI uri = f.toUri();
            Path filePath = ModuleUtils.toPath(uri).orElse(null);
            if (filePath != null) {
                Path parent = filePath.getParent();
                if (srcPath != null) {
                    srcPath.set(parent.toFile());
                }
                if (typeSuffix != null) {
                    String type = ModuleUtils.inferSourceOrTest(parent);
                    typeSuffix.set(type);
                }
                if (filePath.toFile().exists()) {
                    if (moduleInfoFile != null) {
                        moduleInfoFile.set(filePath.toFile());
                    }
                    return ModuleInfoDescriptor.create(filePath);
                }
            }
        } catch (Exception e) {
            debug("unable to retrieve " + location + " from filer: " + e.getMessage());
        }

        return null;
    }

}
