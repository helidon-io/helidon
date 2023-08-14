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

package io.helidon.inject.configdriven.processor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.TypeElement;

import io.helidon.common.processor.TypeInfoFactory;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.processor.InjectionAnnotationProcessor;
import io.helidon.inject.tools.ServicesToProcess;
import io.helidon.inject.tools.ToolsException;

/**
 * Annotation processor implementation to handle types annotated with {@code ConfigDriven}.
 */
public class ConfigDrivenProcessor extends InjectionAnnotationProcessor {
    private static final TypeName CONFIG_DRIVEN_BASE = TypeName.create(
            "io.helidon.inject.configdriven.runtime.ConfigDrivenServiceProviderBase");
    private static final TypeName NAMED_INSTANCE = TypeName.create("io.helidon.inject.configdriven.api.NamedInstance");

    /**
     * Required constructor for a service loaded via {@link java.util.ServiceLoader}.
     */
    public ConfigDrivenProcessor() {
        super(true);
    }

    @Override
    protected Set<String> supportedServiceClassTargetAnnotations() {
        Set<String> supported = new HashSet<>(super.supportedServiceClassTargetAnnotations());
        supported.add(ConfigDrivenAnnotation.TYPE_NAME);
        return supported;
    }

    @Override
    protected void processExtensions(ServicesToProcess services,
                                     TypeInfo service,
                                     Set<TypeName> serviceTypeNamesToCodeGenerate,
                                     Collection<TypedElementInfo> allElementsOfInterest) {

        if (!service.hasAnnotation(ConfigDrivenAnnotation.TYPE)) {
            return;
        }

        ConfigDrivenAnnotation configDriven = ConfigDrivenAnnotation.create(service);
        // the type must be either a valid prototype, or a prototype blueprint (in case this is the same module)
        if ("<error>".equals(configDriven.configBeanType().className())) {
            throw new ToolsException("The config bean type must be set to the Blueprint type if they are within the same "
                                             + "module! Failed on: " + service.typeName().resolvedName());
        }
        TypeInfo configBeanTypeInfo = TypeInfoFactory.create(processingEnv, asElement(configDriven.configBeanType()))
                .orElseThrow();

        ConfigBean configBean = ConfigBean.create(configBeanTypeInfo);
         /*
        Now we have all the information we need
        - type that is annotated as @ConfigDriven
        - which type drives it
        - config prefix for the type driving it
        - if repeatable etc.
         */

        // we do not support inheritance of config driven (for now)
        // now stuff needed for activator generation
        TypeName serviceType = service.typeName();
        TypeName activatorImplType = activatorCreator().toActivatorImplTypeName(serviceType);
        TypeName superType = TypeName.builder(CONFIG_DRIVEN_BASE)
                .addTypeArgument(serviceType)
                .addTypeArgument(configBean.typeName())
                .build();
        boolean accepted = services.addParentServiceType(serviceType, superType, Optional.of(true));
        assert (accepted);

        services.defaultConstructor(serviceType, generateConstructor());
        generateCode(activatorImplType,
                     serviceType,
                     configBean,
                     superType,
                     configDriven.activateByDefault())
                .forEach(fn -> services.addExtraCodeGen(serviceType, fn));
    }

    private String generateConstructor() {
        return "super(\"root\");\n"
                + "\n"
                + "\t\tthis.configBean = null;\n"
                + "\t\tserviceInfo(serviceInfo);";
    }

    private List<String> generateCode(TypeName activatorImplType,
                                      TypeName serviceType,
                                      ConfigBean configBean,
                                      TypeName superType,
                                      boolean activateByDefault) {
        TypeName namedInstanceWithGeneric = TypeName.builder(NAMED_INSTANCE)
                .addTypeArgument(configBean.typeName())
                .build();
        String configBeanName = configBean.typeName().resolvedName();

        return List.of(
                // config bean field
                "\n\tprivate final " + configBeanName + " configBean;\n",
                // constructor with NamedInstance
                "\n\tprivate " + activatorImplType.className() + "(" + namedInstanceWithGeneric.resolvedName()
                        + " configBean) {\n"
                        + "\t\tsuper(configBean.name());\n"
                        + "\t\tassertIsRootProvider(false, true);\n"
                        + "\t\tserviceInfo(serviceInfo);\n"
                        + "\t\tthis.configBean = configBean.instance();\n"
                        + "\t}\n",
                // create config beans from config
                generateCreateConfigBeans(configBean, namedInstanceWithGeneric),
                // configBeanType()
                "\n\t@Override\n"
                        + "\tpublic Class<" + configBeanName + "> configBeanType() {\n"
                        + "\t\treturn " + configBeanName + ".class;\n"
                        + "\t}\n",
                // createInstance(NamedInstance)
                "\n\t@Override\n"
                        + "\tprotected " + superType.resolvedName() + " createInstance("
                        + namedInstanceWithGeneric.resolvedName() + " "
                        + "configBean) {\n"
                        + "\t\treturn new " + activatorImplType.className() + "(configBean);\n"
                        + "\t}\n",
                // drivesActivation()
                "\n\t@Override\n"
                        + "\tprotected boolean drivesActivation() {\n"
                        + "\t\treturn " + activateByDefault + ";\n"
                        + "\t}\n",
                // configBean()
                "\n\t@Override\n"
                        + "\tpublic " + configBeanName + " configBean() {\n"
                        + "\t\tif (isRootProvider()) {\n"
                        + "\t\t\tthrow new NullPointerException(\"Config bean is not available on root config driven"
                        + " instance\");\n"
                        + "\t\t}\n"
                        + "\t\treturn configBean;\n"
                        + "\t}\n"
        );
    }

    private String generateCreateConfigBeans(ConfigBean configBean, TypeName namedInstanceWithGeneric) {
        String prefix = configBean.configPrefix();
        boolean atLeastOne = configBean.annotation().atLeastOne();
        boolean repeatable = configBean.annotation().repeatable();
        boolean wantDefault = configBean.annotation().wantDefault();

        StringBuilder method = new StringBuilder();
        method.append("\n\t@Override\n")
                .append("\tpublic List<")
                .append(namedInstanceWithGeneric.resolvedName())
                .append("> createConfigBeans(io.helidon.common.config.Config config){\n")
                .append("\t\tvar beanConfig = config.get(\"")
                .append(prefix)
                .append("\");\n")
                .append("\t\tif (!beanConfig.exists()) {\n");

        // the config does not exist
        if (atLeastOne) {
            // throw an exception, we need at least one instance
            method.append("\t\t\tthrow new io.helidon.common.config.ConfigException(\"");
            if (repeatable) {
                method.append("Expecting list of configurations");
            } else {
                method.append("Expecting configuration");
            }
            method.append(" at \\\"")
                    .append(configBean.configPrefix())
                    .append("\\\"\");");
        } else if (wantDefault) {
            method.append("\t\t\treturn List.of(new io.helidon.inject.configdriven.api.NamedInstance<>(")
                    .append(configBean.typeName().resolvedName())
                    .append(".create(io.helidon.common.config.Config.empty()), io.helidon.inject.configdriven.api.NamedInstance"
                                    + ".DEFAULT_NAME));\n");
        } else {
            method.append("\t\t\treturn List.of();\n");
        }
        method.append("\t\t}\n");

        // the bean config does exist
        if (repeatable) {
            method.append("\t\treturn createRepeatableBeans(beanConfig, ")
                    .append(wantDefault)
                    .append(", ")
                    .append(configBean.typeName().resolvedName())
                    .append("::create);\n");
        } else {
            method.append("\t\tif (beanConfig.isList()) {\n")
                    .append("\t\t\tthrow new io.helidon.common.config.ConfigException(\"Expecting a single node at \\\"")
                    .append(configBean.configPrefix())
                    .append("\\\", but got a list\");\n")
                    .append("\t\t}\n");
            method.append("\t\treturn java.util.List.of(new ")
                    .append(NAMED_INSTANCE.resolvedName())
                    .append("<>(")
                    .append(configBean.typeName().resolvedName())
                    .append(".create(beanConfig), ")
                    .append(NAMED_INSTANCE.resolvedName())
                    .append(".DEFAULT_NAME));\n");
        }
        method.append("\t}\n");

        return method.toString();
    }

    private TypeElement asElement(TypeName typeName) {
        return processingEnv.getElementUtils().getTypeElement(typeName.resolvedName());
    }
}
