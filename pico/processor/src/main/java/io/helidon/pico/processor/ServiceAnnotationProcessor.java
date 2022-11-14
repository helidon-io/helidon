/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.helidon.pico.ExternalContracts;
import io.helidon.pico.Intercepted;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.processor.JavaxTypeTools;
import io.helidon.pico.tools.processor.Options;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.ModuleUtils;

import jakarta.inject.Singleton;

/**
 * Looks for @Singleton type annotations.
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

    private static void addJavaxTypes(Set<Class<? extends Annotation>> supportedTargets) {
        if (Objects.nonNull(javaxSingletonType)) {
            return;
        }

        try {
            javaxSingletonType = JavaxTypeTools.INSTANCE.get()
                    .loadAnnotationClass(TypeTools.oppositeOf(Singleton.class.getName()));
            if (Objects.nonNull(javaxSingletonType)) {
                supportedTargets.add(javaxSingletonType);
            }
            Class<? extends Annotation> applicationScoped = JavaxTypeTools.INSTANCE.get()
                    .loadAnnotationClass(UnsupportedConstructsProcessor.APPLICATION_SCOPED_TYPE_NAME);
            if (Objects.nonNull(applicationScoped)) {
                supportedTargets.add(applicationScoped);
            }
        } catch (Throwable t) {
            // expected
        }
    }

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return SUPPORTED_TARGETS;
    }

    @Override
    public Set<String> getContraAnnotations() {
        return Collections.singleton(CONFIGURED_BY_TYPENAME);
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        this.processed = true;
        this.roundEnv = roundEnv;

        try {
            if (!roundEnv.processingOver()
                    && Objects.isNull(services.getModuleName())) {
                AtomicReference<String> typeSuffix = new AtomicReference<>();
                AtomicReference<File> moduleInfoFile = new AtomicReference<>();
                AtomicReference<File> srcPath = new AtomicReference<>();
                SimpleModuleDescriptor thisModuleDescriptor =
                        getThisModuleDescriptor(typeSuffix, moduleInfoFile, srcPath);
                if (Objects.nonNull(thisModuleDescriptor)) {
                    services.setLastKnownModuleInfoDescriptor(thisModuleDescriptor);
                } else {
                    String thisModuleName = getThisModuleName(null);
                    services.setModuleName(thisModuleName);
                }
                if (Objects.nonNull(typeSuffix.get())) {
                    services.setLastKnownTypeSuffix(typeSuffix.get());
                }
                if (Objects.nonNull(srcPath.get())) {
                    services.setLastKnownSourcePathBeingProcessed(srcPath.get());
                }
                if (Objects.nonNull(moduleInfoFile.get())) {
                    services.setLastKnownModuleInfoFile(moduleInfoFile.get());
                }
            }

            return super.process(annotations, roundEnv);
        } catch (Throwable t) {
            error(getClass().getSimpleName() + " error during processing; " + t
                          + " @ " + CommonUtils.rootErrorCoordinateOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things
            // to halt
            throw new ToolsException("error during processing: " + t
                                             + " @ " + CommonUtils.rootErrorCoordinateOf(t), t);
        } finally {
            if (roundEnv.processingOver()) {
                services.setModuleName(null);
            }
        }
    }

    protected String getThisModuleName(SimpleModuleDescriptor descriptor) {
        // user pass it in? if so then that will short-circuit the name calculation part
        String moduleName = Options.getOption(Options.TAG_MODULE_NAME);
        if (Objects.nonNull(moduleName)) {
            return moduleName;
        }
        return Objects.isNull(descriptor) ? null : descriptor.getName();
    }

    protected SimpleModuleDescriptor getThisModuleDescriptor(AtomicReference<String> typeSuffix,
                                                             AtomicReference<File> moduleInfoFile,
                                                             AtomicReference<File> srcPath) {
        SimpleModuleDescriptor descriptor = tryFindModuleInfo(StandardLocation.SOURCE_OUTPUT,
                                                              typeSuffix,
                                                              moduleInfoFile,
                                                              srcPath);
        if (Objects.isNull(descriptor)) {
            descriptor = tryFindModuleInfo(StandardLocation.SOURCE_PATH, typeSuffix, moduleInfoFile, srcPath);

            if (Objects.isNull(descriptor)) {
                // attempt to retrieve from src/main/java if we can't recover to this point
                descriptor = getThisModuleDescriptorFromSourceMain(moduleInfoFile, srcPath);
            }
        }
        // note to self: this seems worthy to add at some point --jtrent
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

    protected SimpleModuleDescriptor getThisModuleDescriptorFromSourceMain(AtomicReference<File> moduleInfoFile,
                                                                           AtomicReference<File> srcPath) {
        if (Objects.nonNull(srcPath) && Objects.nonNull(srcPath.get()) && srcPath.get().getPath()
                .contains(TARGET_DIR)) {
            String path = srcPath.get().getPath();
            int pos = path.indexOf(TARGET_DIR);
            path = path.substring(0, pos);
            File srcRoot = new File(path, SRC_MAIN_JAVA_DIR);
            File file = new File(srcRoot, ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
            if (file.exists()) {
                try {
                    return SimpleModuleDescriptor.load(file, SimpleModuleDescriptor.Ordering.NATURAL_PRESERVE_COMMENTS);
                } catch (Exception e) {
                    debug("unable to read source module-info.java from: " + srcRoot + "; " + e.getMessage(), e);
                }

                if (Objects.nonNull(moduleInfoFile)) {
                    moduleInfoFile.set(file);
                }
            }
        }

        return null;
    }

    protected SimpleModuleDescriptor tryFindModuleInfo(StandardLocation location,
                                                       AtomicReference<String> typeSuffix,
                                                       AtomicReference<File> moduleInfoFile,
                                                       AtomicReference<File> srcPath) {
        Filer filer = processingEnv.getFiler();
        try {
            FileObject f = filer.getResource(location, "", ModuleUtils.REAL_MODULE_INFO_JAVA_NAME);
            URI uri = f.toUri();
            File file = ModuleUtils.toFile(uri);
            if (Objects.nonNull(file)) {
                File parent = file.getParentFile();
                if (Objects.nonNull(srcPath)) {
                    srcPath.set(parent);
                }
                if (Objects.nonNull(typeSuffix)) {
                    String type = ModuleUtils.inferSourceOrTest(parent.toPath());
                    if (Objects.nonNull(type)) {
                        typeSuffix.set(type);
                    }
                }

                if (file.exists()) {
                    if (Objects.nonNull(moduleInfoFile)) {
                        moduleInfoFile.set(file);
                    }
                    return SimpleModuleDescriptor.uncheckedLoad(file);
                }
            }
        } catch (Exception e) {
            debug("unable to retrieve " + location + " from filer: " + e.getMessage());
        }

        return null;
    }

}
