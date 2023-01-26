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

import io.helidon.pico.Application;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.services.AbstractServiceProvider;
import io.helidon.pico.services.DefaultServiceBinder;
import io.helidon.pico.types.TypeName;

import static io.helidon.pico.tools.CommonUtils.hasValue;
import static io.helidon.pico.tools.TypeTools.needToDeclareModuleUsage;
import static io.helidon.pico.tools.TypeTools.needToDeclarePackageUsage;

/**
 * Abstract base for any codegen creator.
 */
public abstract class AbstractCreator {

    /**
     * Logger.
     */
    private final System.Logger logger = System.getLogger(getClass().getName());

    /**
     * The default java source version (this can be explicitly overridden using the builder or maven plugin).
     */
    public static final String DEFAULT_SOURCE = "11";
    /**
     * The default java target version (this can be explicitly overridden using the builder or maven plugin).
     */
    public static final String DEFAULT_TARGET = "11";

    // no special chars since this will be used as a package and class name
    static final String NAME = PicoServicesConfig.NAME;
    static final String MODULE_NAME = PicoServicesConfig.FQN;

    private final TemplateHelper templateHelper;
    private final String templateName;

    AbstractCreator(String templateName) {
        this.templateHelper = TemplateHelper.create();
        this.templateName = templateName;
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
    CodeGenFiler createDirectCodeGenFiler(
            CodeGenPaths paths,
            boolean isAnalysisOnly) {
        AbstractFilerMsgr filer = AbstractFilerMsgr.createDirectFiler(paths, logger);
        return new CodeGenFiler(filer, !isAnalysisOnly);
    }

    /**
     * The generated sticker string.
     *
     * @param req the creator request
     * @return the sticker
     */
    String toGeneratedSticker(
            GeneralCreatorRequest req) {
        String generator = (null == req) ? null : req.generator().orElse(null);
        return templateHelper.generatedStickerFor((generator != null) ? generator : getClass().getName());
    }

    /**
     * Generates the {@link io.helidon.pico.Activator} source code for the provided service providers. Custom
     * service providers (see {@link AbstractServiceProvider#isCustom()}) do not qualify to
     * have activators code generated.
     *
     * @param sp the collection of service providers
     * @return the code generated string for the service provider given
     */
    static String toActivatorCodeGen(
            ServiceProvider<?> sp) {
        if (sp instanceof AbstractServiceProvider && ((AbstractServiceProvider<?>) sp).isCustom()) {
            return null;
        }
        return DefaultServiceBinder.toRootProvider(sp).activator().orElseThrow().getClass().getName() + ".INSTANCE";
    }

    /**
     * Generates the {@link io.helidon.pico.Activator} source code for the provided service providers.
     *
     * @param coll the collection of service providers
     * @return the code generated string for the collection of service providers given
     */
    static String toActivatorCodeGen(
            Collection<ServiceProvider<?>> coll) {
        return CommonUtils.toString(coll, AbstractCreator::toActivatorCodeGen, null);
    }

    /**
     * Automatically adds the requirements to the module-info descriptor for what pico requires.
     *
     * @param moduleInfo the module info descriptor
     * @param generator  the generator calling us
     * @return the modified descriptor, fluent style
     */
    DefaultModuleInfoDescriptor.Builder addPicoProviderRequirementsTo(
            DefaultModuleInfoDescriptor.Builder moduleInfo,
            String generator) {
        Objects.requireNonNull(generator);
        String preComment = "    // " + PicoServicesConfig.NAME + " - generated by " + generator;
        ModuleInfoDescriptor.addIfAbsent(moduleInfo, MODULE_NAME, DefaultModuleInfoItem.builder()
                .requires(true)
                .target(MODULE_NAME)
                .transitiveUsed(true)
                .addPrecomment(preComment));
        return moduleInfo;
    }

    ModuleInfoDescriptor createModuleInfo(
            ModuleInfoCreatorRequest req) {
        String generator = templateHelper.generatedStickerFor(getClass().getName());
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

        DefaultModuleInfoDescriptor.Builder descriptorBuilder;
        if (moduleInfoPath != null) {
            descriptorBuilder = DefaultModuleInfoDescriptor
                    .toBuilder(ModuleInfoDescriptor.create(Paths.get(moduleInfoPath)));
            if (hasValue(moduleName) && ModuleInfoDescriptor.DEFAULT_MODULE_NAME.equals(descriptorBuilder.name())) {
                descriptorBuilder.name(moduleName);
            }
            assert (descriptorBuilder.name().equals(moduleName) || (!hasValue(moduleName)))
                    : "bad module name: " + moduleName + " targeting " + descriptorBuilder.name();
            moduleName = descriptorBuilder.name();
        } else {
            descriptorBuilder = DefaultModuleInfoDescriptor.builder().name(moduleName);
            descriptorBuilder.headerComment("// @Generated(" + templateHelper.generatedStickerFor(generator) + ")");
        }

        boolean isTestModule = ModuleInfoDescriptor.DEFAULT_TEST_SUFFIX.equals(classPrefixName);
        if (isTestModule) {
            ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, moduleName, DefaultModuleInfoItem.builder()
                    .requires(true)
                    .target(moduleName)
                    .transitiveUsed(true));
        }

        if (isModuleCreated && (moduleTypeName != null)) {
            if (!isTestModule) {
                ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, moduleTypeName.packageName(),
                                                 DefaultModuleInfoItem.builder()
                                                         .exports(true)
                                                         .target(moduleTypeName.packageName()));
            }
            ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, io.helidon.pico.Module.class.getName(),
                                             DefaultModuleInfoItem.builder()
                                                     .provides(true)
                                                     .target(io.helidon.pico.Module.class.getName())
                                                     .addWithOrTo(moduleTypeName.name())
                                                     .addPrecomment("    // "
                                                                            + PicoServicesConfig.NAME
                                                                            + " module - generated by "
                                                                            + generator));
        }
        if (isApplicationCreated && applicationTypeName != null) {
            if (!isTestModule) {
                ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, applicationTypeName.packageName(),
                                                 DefaultModuleInfoItem.builder()
                                                         .exports(true)
                                                         .target(applicationTypeName.packageName()));
            }
            ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, Application.class.getName(),
                                             DefaultModuleInfoItem.builder()
                                                     .provides(true)
                                                     .target(Application.class.getName())
                                                     .addWithOrTo(applicationTypeName.name())
                                                     .addPrecomment("    // "
                                                                            + PicoServicesConfig.NAME
                                                                            + " module - generated by "
                                                                            + generator));
        }

        String preComment = "    // " + PicoServicesConfig.NAME + " external contract usage - generated by " + generator;
        if (modulesRequired != null) {
            for (String externalModuleName : modulesRequired) {
                if (!needToDeclareModuleUsage(externalModuleName)) {
                    continue;
                }

                boolean added = ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, externalModuleName,
                                                                 DefaultModuleInfoItem.builder()
                                                                         .requires(true)
                                                                         .target(externalModuleName)
                                                                         .addPrecomment(preComment));
                if (added) {
                    preComment = "";
                }
            }
        }

        Set<TypeName> allExternalContracts = toAllContracts(externalContracts);
        for (TypeName cn : allExternalContracts) {
            String packageName = cn.packageName();
            if (!needToDeclarePackageUsage(packageName)) {
                continue;
            }

            boolean added = ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, cn.name(),
                                                             DefaultModuleInfoItem.builder()
                                                                     .uses(true)
                                                                     .target(cn.name())
                                                                     .addPrecomment(preComment));
            if (added) {
                preComment = "";
            }
        }

        if (!isTestModule && (contracts != null)) {
            preComment = "    // " + PicoServicesConfig.NAME + " contract usage - generated by " + generator;
            for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
                for (TypeName contract : e.getValue()) {
                    if (!allExternalContracts.contains(contract)) {
                        String packageName = contract.packageName();
                        if (!needToDeclarePackageUsage(packageName)) {
                            continue;
                        }
                        boolean added = ModuleInfoDescriptor.addIfAbsent(descriptorBuilder, packageName,
                                                                         DefaultModuleInfoItem.builder()
                                                                                 .exports(true)
                                                                                 .target(packageName)
                                                                                 .addPrecomment(preComment));
                        if (added) {
                            preComment = "";
                        }
                    }
                }
            }
        }

        return addPicoProviderRequirementsTo(descriptorBuilder, generator);
    }

    static Set<TypeName> toAllContracts(
            Map<TypeName, Set<TypeName>> servicesToContracts) {
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
    static CodeGenPaths createCodeGenPaths(
            ServicesToProcess servicesToProcess) {
        Path moduleInfoFilePath = servicesToProcess.lastGeneratedModuleInfoFilePath();
        if (moduleInfoFilePath == null) {
            moduleInfoFilePath = servicesToProcess.lastKnownModuleInfoFilePath();
        }
        return DefaultCodeGenPaths.builder()
                .moduleInfoPath(Optional.ofNullable((moduleInfoFilePath != null) ? moduleInfoFilePath.toString() : null))
                .build();
    }

}
