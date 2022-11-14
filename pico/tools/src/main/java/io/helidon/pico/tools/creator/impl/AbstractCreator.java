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

package io.helidon.pico.tools.creator.impl;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.Application;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.spi.ext.AbstractServiceProvider;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.GeneralCreatorRequest;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.ModuleUtils;
import io.helidon.pico.tools.utils.TemplateHelper;
import io.helidon.pico.types.TypeName;

/**
 * Abstract base for any codegen creator.
 */
public class AbstractCreator {

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

    protected AbstractCreator() {
    }

    protected System.Logger getLogger() {
        return logger;
    }

    /**
     * Creates a codegen filer that is not reliant on annotation processing, but still capable of creating source
     * files and resources.
     *
     * @param paths          the paths for where files should be read or written.
     * @param isAnalysisOnly true if analysis only, where no code or resources will be physically written to disk
     * @return the code gen filer instance to use
     */
    protected CodeGenFiler createDirectCodeGenFiler(CodeGenPaths paths, boolean isAnalysisOnly) {
        AbstractFilerMsgr filer = AbstractFilerMsgr.createDirectFiler(paths, logger);
        return new CodeGenFiler(filer, !isAnalysisOnly);
    }

     /**
     * The generated sticker string.
     *
     * @param req the creator request
     * @return the sticker
     */
    protected String getGeneratedSticker(GeneralCreatorRequest req) {
        String generator = req.getGenerator();
        return TemplateHelper.getDefaultGeneratedSticker(Objects.nonNull(generator) ? generator : getClass().getName());
    }

    /**
     * Generates the {@link io.helidon.pico.Activator} source code for the provided service providers. Custom
     * service providers (see {@link io.helidon.pico.spi.ext.AbstractServiceProvider#isCustom()}) do not qualify to
     * have activators code generated.
     *
     * @param sp the collection of service providers
     * @return the code generated string for the service provider given
     */
    protected String toActivatorCodeGen(ServiceProvider<?> sp) {
        if (sp instanceof AbstractServiceProvider && ((AbstractServiceProvider) sp).isCustom()) {
            return null;
        }
        return ServiceProviderBindable.toRootProvider(sp).activator().getClass().getName() + ".INSTANCE";
    }

    /**
     * Generates the {@link io.helidon.pico.Activator} source code for the provided service providers.
     *
     * @param coll the collection of service providers
     * @return the code generated string for the collection of service providers given
     */
    @SuppressWarnings("unused")
    protected String toActivatorCodeGen(Collection<ServiceProvider<?>> coll) {
        return CommonUtils.toString(coll, this::toActivatorCodeGen, null);
    }

    /**
     * Automatically adds the requirements to the module-info descriptor for what pico requires.
     *
     * @param moduleInfo the module info descriptor
     * @param generator  the generator calling us
     * @return the modified descriptor, fluent style
     */
    protected SimpleModuleDescriptor addPicoProviderRequirementsTo(SimpleModuleDescriptor moduleInfo,
                                                                   String generator) {
        String preComment = "    // " + PicoServicesConfig.NAME + " - generated by " + generator;
        moduleInfo.addIfAbsent(SimpleModuleDescriptor.Item.requiresModuleName(MODULE_NAME)
                                       .isTransitive(true)
                                       .precomment(preComment));
        return moduleInfo;
    }

    protected SimpleModuleDescriptor createModuleInfo(PicoModuleBuilderRequest req) {
        String generator = req.getGenerator(getClass());
        String moduleInfoPath = req.getModuleInfoPath();
        String moduleName = req.getModuleName();
        TypeName moduleTypeName = req.getModuleTypeName();
        TypeName applicationTypeName = req.getApplicationTypeName();
        String classPrefixName = req.getClassPrefixName();
        boolean isModuleCreated = req.isModuleCreated();
        boolean isApplicationCreated = req.isApplicationCreated();
        Collection<String> modulesRequired = req.getModulesRequired();
        Map<TypeName, Set<TypeName>> contracts = req.getServiceTypeContracts();
        Map<TypeName, Set<TypeName>> externalContracts = req.getExternalContracts();

        SimpleModuleDescriptor descriptor;
        if (Objects.nonNull(moduleInfoPath)) {
            descriptor = SimpleModuleDescriptor.uncheckedLoad(new File(moduleInfoPath));
            assert (descriptor.getName().equals(moduleName) || Objects.isNull(moduleName))
                    : "bad module name: " + moduleName;
            moduleName = descriptor.getName();
        } else {
            descriptor = new SimpleModuleDescriptor(moduleName, null);
            descriptor.headerComment("// @Generated(" + TemplateHelper.getDefaultGeneratedSticker(generator) + ")");
        }

        boolean isTestModule = SimpleModuleDescriptor.DEFAULT_TEST_SUFFIX.equals(classPrefixName);
        if (isTestModule) {
            descriptor.addIfAbsent(SimpleModuleDescriptor.Item.requiresModuleName(ModuleUtils.getNormalizedBaseModuleName(
                    moduleName)));
        }

        if (isModuleCreated && Objects.nonNull(moduleTypeName)) {
            if (!isTestModule) {
                descriptor.addIfAbsent(SimpleModuleDescriptor.Item.exportsPackage(moduleTypeName));
            }
            descriptor.addIfAbsent(SimpleModuleDescriptor.Item.providesContract(Module.class, moduleTypeName.name())
                                           .precomment("    // " + PicoServicesConfig.NAME + " module - generated by "
                                                               + generator));
        }
        if (isApplicationCreated && Objects.nonNull(applicationTypeName)) {
            if (!isTestModule) {
                descriptor.addIfAbsent(SimpleModuleDescriptor.Item.exportsPackage(applicationTypeName));
            }
            descriptor.addIfAbsent(SimpleModuleDescriptor.Item.providesContract(Application.class,
                                                                                applicationTypeName.name())
                                           .precomment("    // " + PicoServicesConfig.NAME + " application - "
                                                               + "generated by " + generator));
        }

        String preComment =
                "    // " + PicoServicesConfig.NAME + " external contract usage - generated by " + generator;
        if (Objects.nonNull(modulesRequired)) {
            for (String externalModuleName : modulesRequired) {
                if (!needToDeclareModuleUsage(externalModuleName)) {
                    continue;
                }

                boolean added = Objects.nonNull(
                        descriptor.addIfAbsent(SimpleModuleDescriptor.Item.requiresModuleName(externalModuleName)
                                                       .precomment(preComment)));
                if (added) {
                    preComment = null;
                }
            }
        }

        Set<TypeName> allExternalContracts = toAllContracts(externalContracts);
        if (Objects.nonNull(allExternalContracts)) {
            for (TypeName cn : allExternalContracts) {
                String packageName = cn.packageName();
                if (!needToDeclarePackageUsage(packageName)) {
                    continue;
                }

                boolean added = Objects.nonNull(
                        descriptor.addIfAbsent(SimpleModuleDescriptor.Item.usesExternalContract(cn.name())
                                                       .precomment(preComment)));
                if (added) {
                    preComment = null;
                }
            }
        }

        if (!isTestModule && Objects.nonNull(contracts)) {
            preComment = "    // " + PicoServicesConfig.NAME + " contract usage - generated by " + generator;
            for (Map.Entry<TypeName, Set<TypeName>> e : contracts.entrySet()) {
                for (TypeName contract : e.getValue()) {
                    if (!allExternalContracts.contains(contract)) {
                        String packageName = contract.packageName();
                        if (!needToDeclarePackageUsage(packageName)) {
                            continue;
                        }
                        boolean added = Objects.nonNull(descriptor.addIfAbsent(
                                new SimpleModuleDescriptor.Item(
                                        packageName, descriptor.getOrdering()).exports(true).precomment(preComment)));
                        if (added) {
                            preComment = null;
                        }
                    }
                }
            }
        }

        return addPicoProviderRequirementsTo(descriptor, generator);
    }

    protected Set<TypeName> toAllContracts(Map<TypeName, Set<TypeName>> servicesToContracts) {
        if (Objects.isNull(servicesToContracts)) {
            return null;
        }

        Set<TypeName> result = new LinkedHashSet<>();
        servicesToContracts.forEach((serviceTypeName, cn) -> result.addAll(cn));
        return result;
    }

    /**
     * Checks whether the package name need to be declared.
     *
     * @param packageName the package name
     * @return true if the package name needs to be declared
     */
    protected static boolean needToDeclarePackageUsage(String packageName) {
        return !(
                packageName.startsWith("java.")
                        || packageName.startsWith("sun.")
                        || packageName.toLowerCase().endsWith(".impl"));
    }

    /**
     * Checks whether the module name needs to be declared.
     *
     * @param moduleName
     * @return true if the module name needs to be declared
     */
    public static boolean needToDeclareModuleUsage(String moduleName) {
        return Objects.nonNull(moduleName) && !moduleName.equals(SimpleModuleDescriptor.DEFAULT_MODULE_NAME)
                    && !(moduleName.startsWith("java.")
                        || moduleName.startsWith("sun.")
                        || moduleName.startsWith("jakarta.inject")
                        || moduleName.startsWith(PicoServicesConfig.FQN));
    }

}
