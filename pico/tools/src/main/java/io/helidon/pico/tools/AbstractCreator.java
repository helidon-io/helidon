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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.pico.api.ServiceProvider;
import io.helidon.pico.runtime.AbstractServiceProvider;
import io.helidon.pico.runtime.ServiceBinderDefault;

import static io.helidon.pico.tools.CommonUtils.hasValue;
import static io.helidon.pico.tools.TypeTools.needToDeclareModuleUsage;
import static io.helidon.pico.tools.TypeTools.needToDeclarePackageUsage;

/**
 * Abstract base for any codegen creator.
 */
public abstract class AbstractCreator {
    /**
     * The default java source version (this can be explicitly overridden using the builder or maven plugin).
     */
    public static final String DEFAULT_SOURCE = "11";
    /**
     * The default java target version (this can be explicitly overridden using the builder or maven plugin).
     */
    public static final String DEFAULT_TARGET = "11";

    // no special chars since this will be used as a package and class name
    static final String NAME_PREFIX = "Pico$$";
    static final String PICO_FRAMEWORK_MODULE = "io.helidon.pico.runtime";
    static final String MODULE_NAME_SUFFIX = "Module";

    private final System.Logger logger = System.getLogger(getClass().getName());
    private final TemplateHelper templateHelper;
    private final String templateName;

    AbstractCreator(String templateName) {
        this.templateHelper = TemplateHelper.create();
        this.templateName = templateName;
    }

    /**
     * Generates the {@link io.helidon.pico.api.Activator} source code for the provided service providers. Custom
     * service providers (see {@link AbstractServiceProvider#isCustom()}) do not qualify to
     * have activators code generated.
     *
     * @param sp the collection of service providers
     * @return the code generated string for the service provider given
     */
    static String toActivatorCodeGen(ServiceProvider<?> sp) {
        if (sp instanceof AbstractServiceProvider && ((AbstractServiceProvider<?>) sp).isCustom()) {
            return null;
        }
        return ServiceBinderDefault.toRootProvider(sp).activator().orElseThrow().getClass().getName() + ".INSTANCE";
    }

    /**
     * Generates the {@link io.helidon.pico.api.Activator} source code for the provided service providers.
     *
     * @param coll the collection of service providers
     * @return the code generated string for the collection of service providers given
     */
    static String toActivatorCodeGen(Collection<ServiceProvider<?>> coll) {
        return CommonUtils.toString(coll, AbstractCreator::toActivatorCodeGen, null);
    }

    static Set<TypeName> toAllContracts(Map<TypeName, Set<TypeName>> servicesToContracts) {
        Set<TypeName> result = new LinkedHashSet<>();
        servicesToContracts.forEach((serviceTypeName, cn) -> result.addAll(cn));
        return result;
    }

    /**
     * Creates the {@link io.helidon.pico.tools.CodeGenPaths} given the current batch of services to process.
     *
     * @param servicesToProcess the services to process
     * @return the payload for code gen paths
     */
    static CodeGenPaths createCodeGenPaths(ServicesToProcess servicesToProcess) {
        Path moduleInfoFilePath = servicesToProcess.lastGeneratedModuleInfoFilePath();
        if (moduleInfoFilePath == null) {
            moduleInfoFilePath = servicesToProcess.lastKnownModuleInfoFilePath();
        }
        return CodeGenPaths.builder()
                .moduleInfoPath(Optional.ofNullable((moduleInfoFilePath != null) ? moduleInfoFilePath.toString() : null))
                .build();
    }

    System.Logger logger() {
        return logger;
    }

    TemplateHelper templateHelper() {
        return templateHelper;
    }

    String templateName() {
        return templateName;
    }

    /**
     * Creates a codegen filer that is not reliant on annotation processing, but still capable of creating source
     * files and resources.
     *
     * @param paths          the paths for where files should be read or written.
     * @param isAnalysisOnly true if analysis only, where no code or resources will be physically written to disk
     * @return the code gen filer instance to use
     */
    CodeGenFiler createDirectCodeGenFiler(CodeGenPaths paths,
                                          boolean isAnalysisOnly) {
        AbstractFilerMessager filer = AbstractFilerMessager.createDirectFiler(paths, logger);
        return new CodeGenFiler(filer, !isAnalysisOnly);
    }

    /**
     * The generated sticker string.
     *
     * @param generator type of the generator (annotation processor)
     * @param trigger type of the trigger (class that caused the annotation processing, or annotation processor itself)
     * @param generatedType type of the generated class
     * @return the sticker
     */
    String toGeneratedSticker(TypeName generator, TypeName trigger, TypeName generatedType) {
        return templateHelper.generatedStickerFor(generator,
                                                  trigger,
                                                  generatedType);
    }

    /**
     * Automatically adds the requirements to the module-info descriptor for what pico requires.
     *
     * @param moduleInfo the module info descriptor
     * @param generatedAnno  the generator sticker value
     * @return the modified descriptor, fluent style
     */
    ModuleInfoDescriptor.Builder addPicoProviderRequirementsTo(ModuleInfoDescriptor.Builder moduleInfo,
                                                               String generatedAnno) {
        Objects.requireNonNull(generatedAnno);
        // requirements on the pico services framework itself
        String preComment = "    // Pico services - " + generatedAnno;
        ModuleInfoUtil.addIfAbsent(moduleInfo, PICO_FRAMEWORK_MODULE, ModuleInfoItem.builder()
                .requires(true)
                .target(PICO_FRAMEWORK_MODULE)
                .isTransitiveUsed(true)
                .addPrecomment(preComment));
        return moduleInfo;
    }

    ModuleInfoDescriptor createModuleInfo(ModuleInfoCreatorRequest req) {
        TypeName processor = TypeName.create(getClass());

        String generatedAnno = templateHelper.generatedStickerFor(processor, processor, TypeName.create("module-info"));
        String moduleInfoPath = req.moduleInfoPath().orElse(null);
        String moduleName = req.name().orElse(null);
        TypeName moduleTypeName = req.moduleTypeName();
        TypeName applicationTypeName = req.applicationTypeName().orElse(null);
        String classPrefixName = req.classPrefixName();
        boolean isModuleCreated = req.moduleCreated();
        boolean isApplicationCreated = req.applicationCreated();
        Collection<String> modulesRequired = req.modulesRequired();
        Map<TypeName, Set<TypeName>> contracts = req.contracts();
        Map<TypeName, Set<TypeName>> externalContracts = req.externalContracts();

        ModuleInfoDescriptor.Builder descriptorBuilder;
        if (moduleInfoPath != null) {
            descriptorBuilder = ModuleInfoDescriptor
                    .builder(ModuleInfoDescriptor.create(Paths.get(moduleInfoPath)));
            if (hasValue(moduleName) && ModuleUtils.isUnnamedModuleName(descriptorBuilder.name())) {
                descriptorBuilder.name(moduleName);
            }
            assert (descriptorBuilder.name().equals(moduleName) || (!hasValue(moduleName)))
                    : "bad module name: " + moduleName + " targeting " + descriptorBuilder.name();
            moduleName = descriptorBuilder.name();
        } else {
            descriptorBuilder = ModuleInfoDescriptor.builder().name(moduleName);
            descriptorBuilder.headerComment("// " + generatedAnno);
        }

        boolean isTestModule = "test".equals(classPrefixName);
        if (isTestModule) {
            String baseModuleName = ModuleUtils.normalizedBaseModuleName(moduleName);
            ModuleInfoUtil.addIfAbsent(descriptorBuilder, baseModuleName, ModuleInfoItem.builder()
                    .requires(true)
                    .target(baseModuleName)
                    .isTransitiveUsed(true));
        }

        if (isModuleCreated && (moduleTypeName != null)) {
            if (!isTestModule) {
                ModuleInfoUtil.addIfAbsent(descriptorBuilder, moduleTypeName.packageName(),
                                                 ModuleInfoItem.builder()
                                                         .exports(true)
                                                         .target(moduleTypeName.packageName()));
            }
            ModuleInfoUtil.addIfAbsent(descriptorBuilder, TypeNames.PICO_MODULE,
                                             ModuleInfoItem.builder()
                                                     .provides(true)
                                                     .target(TypeNames.PICO_MODULE)
                                                     .addWithOrTo(moduleTypeName.name())
                                                     .addPrecomment("    // Pico module - " + generatedAnno));
        }
        if (isApplicationCreated && applicationTypeName != null) {
            if (!isTestModule) {
                ModuleInfoUtil.addIfAbsent(descriptorBuilder, applicationTypeName.packageName(),
                                                 ModuleInfoItem.builder()
                                                         .exports(true)
                                                         .target(applicationTypeName.packageName()));
            }
            ModuleInfoUtil.addIfAbsent(descriptorBuilder, TypeNames.PICO_APPLICATION,
                                             ModuleInfoItem.builder()
                                                     .provides(true)
                                                     .target(TypeNames.PICO_APPLICATION)
                                                     .addWithOrTo(applicationTypeName.name())
                                                     .addPrecomment("    // Pico application - " + generatedAnno));
        }

        String preComment = "    // Pico external contract usage - " + generatedAnno;
        if (modulesRequired != null) {
            for (String externalModuleName : modulesRequired) {
                if (!needToDeclareModuleUsage(externalModuleName)) {
                    continue;
                }

                ModuleInfoItem.Builder itemBuilder = ModuleInfoItem.builder()
                        .requires(true)
                        .target(externalModuleName);
                if (hasValue(preComment)) {
                    itemBuilder.addPrecomment(preComment);
                }

                boolean added = ModuleInfoUtil.addIfAbsent(descriptorBuilder, externalModuleName, itemBuilder);
                if (added) {
                    preComment = "";
                }
            }
        }

        Set<TypeName> allExternalContracts = toAllContracts(externalContracts);
        if (!isTestModule && (contracts != null)) {
            preComment = "    // Pico contract usage - " + generatedAnno;
            for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
                for (TypeName contract : e.getValue()) {
                    if (!allExternalContracts.contains(contract)) {
                        String packageName = contract.packageName();
                        if (!needToDeclarePackageUsage(packageName)) {
                            continue;
                        }

                        ModuleInfoItem.Builder itemBuilder = ModuleInfoItem.builder()
                                .exports(true)
                                .target(packageName);
                        if (hasValue(preComment)) {
                            itemBuilder.addPrecomment(preComment);
                        }

                        boolean added = ModuleInfoUtil.addIfAbsent(descriptorBuilder, packageName, itemBuilder);
                        if (added) {
                            preComment = "";
                        }
                    }
                }
            }
        }

        return addPicoProviderRequirementsTo(descriptorBuilder, generatedAnno);
    }

}
