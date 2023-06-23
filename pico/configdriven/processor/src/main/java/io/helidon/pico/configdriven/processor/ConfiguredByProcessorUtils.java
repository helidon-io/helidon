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

package io.helidon.pico.configdriven.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.Builder;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.common.config.Config;
import io.helidon.common.types.TypeName;

import static io.helidon.common.types.TypeNameDefault.create;

final class ConfiguredByProcessorUtils {

    static final String TAG_OVERRIDE_BEAN = "overrideBean";

    private ConfiguredByProcessorUtils() {
    }

    /**
     * Returns the additive code generated methods (source form) to add over what the base
     * {@link io.helidon.pico.processor.PicoAnnotationProcessor} provides - thereby making this a "config driven service".
     *
     * @param activatorImplTypeName the activator implementation type name
     * @param configBeanType        the config bean type
     * @param hasParent             flag indicating whether the implementation has another config-driven parent activator
     * @param configuredByAttributes meta attributes for the configuredBy annotation
     * @return the list of extra source code to generate
     * @see ConfiguredByAnnotationProcessor
     */
    static List<String> createExtraCodeGen(TypeName activatorImplTypeName,
                                           TypeName configBeanType,
                                           boolean hasParent,
                                           Map<String, String> configuredByAttributes) {
        List<String> result = new ArrayList<>();
        TypeName configBeanImplName = toDefaultImpl(configBeanType);

        String comment = "\n\t/**\n"
                + "\t * Config-driven service constructor.\n"
                + "\t * \n"
                + "\t * @param configBean config bean\n"
                + "\t */";
        if (hasParent) {
            result.add(comment + "\n\tprotected " + activatorImplTypeName.className() + "(" + configBeanType + " configBean) {\n"
                               + "\t\tsuper(configBean);\n"
                               + "\t\tserviceInfo(serviceInfo);\n"
                               + "\t}\n");
        } else {
            result.add("\n\tprivate " + configBeanType + " configBean;\n");
            result.add(comment + "\n\tprotected " + activatorImplTypeName.className() + "(" + configBeanType + " configBean) {\n"
                               + "\t\tthis.configBean = Objects.requireNonNull(configBean);\n"
                               + "\t\tassertIsRootProvider(false, true);\n"
                               + "\t\tserviceInfo(serviceInfo);\n"
                               + "\t}\n");
        }

        comment = "\n\t/**\n"
                + "\t * Creates an instance given a config bean.\n"
                + "\t * \n"
                + "\t * @param configBean config bean\n"
                + "\t */\n";
        result.add(comment + "\t@Override\n"
                           + "\tprotected " + activatorImplTypeName + " createInstance(Object configBean) {\n"
                           + "\t\treturn new " + activatorImplTypeName.className() + "((" + configBeanType + ") configBean);\n"
                           + "\t}\n");

        if (!hasParent) {
            result.add("\t@Override\n"
                               + "\tpublic Optional<CB> configBean() {\n"
                               + "\t\treturn Optional.ofNullable((CB) configBean);\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic Optional<" + Config.class.getName() + "> rawConfig() {\n"
                               + "\t\tif (configBean == null) {\n"
                               + "\t\t\treturn Optional.empty();\n"
                               + "\t\t}\n"
                               + "\t\treturn ((" + configBeanImplName + ") configBean).__config();\n"
                               + "\t}\n");
        }

        result.add("\t@Override\n"
                           + "\tpublic Class<?> configBeanType() {\n"
                           + "\t\treturn " + configBeanType + ".class;\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic Map<String, Map<String, Object>> configBeanAttributes() {\n"
                           + "\t\treturn " + configBeanImplName + ".__metaAttributes();\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic <R> void visitAttributes(CB configBean, " + AttributeVisitor.class.getName()
                           + "<Object> visitor, R userDefinedCtx) {\n"
                           + "\t\t" + AttributeVisitor.class.getName() + "<Object> beanVisitor = visitor::visit;\n"
                           + "\t\t((" + configBeanImplName + ") configBean).visitAttributes(beanVisitor, userDefinedCtx);\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic CB toConfigBean(" + Config.class.getName() + " config) {\n"
                           + "\t\treturn (CB) " + configBeanImplName + "\n\t\t\t.toBuilder(config)\n\t\t\t.build();\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic " + configBeanImplName + ".Builder "
                           + "toConfigBeanBuilder(" + Config.class.getName() + " config) {\n"
                           + "\t\treturn " + configBeanImplName + ".toBuilder(config);\n"
                           + "\t}\n");

        if (!hasParent) {
            result.add("\t@Override\n"
                               + "\tprotected CB acceptConfig(io.helidon.common.config.Config config) {\n"
                               + "\t\tthis.configBean = (CB) super.acceptConfig(config);\n"
                               + "\t\treturn (CB) this.configBean;\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic String toConfigBeanInstanceId(CB configBean) {\n"
                               + "\t\treturn ((" + configBeanImplName
                               + ") configBean).__instanceId();\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic void configBeanInstanceId(CB configBean, String val) {\n"
                               + "\t\t((" + configBeanImplName + ") configBean).__instanceId(val);\n"
                               + "\t}\n");
        }

        String overridesEnabledStr = configuredByAttributes.get(TAG_OVERRIDE_BEAN);
        if (Boolean.parseBoolean(overridesEnabledStr)) {
            String drivesActivationStr = configuredByAttributes.get(ConfigBeanInfo.TAG_DRIVES_ACTIVATION);
            result.add("\t@Override\n"
                               + "\tprotected boolean drivesActivation() {\n"
                               + "\t\treturn " + Boolean.parseBoolean(drivesActivationStr) + ";\n"
                               + "\t}\n");
        }

        return result;
    }

    static List<String> createExtraActivatorClassComments() {
        return List.of("@param <CB> the config bean type");
    }

    static TypeName toDefaultImpl(TypeName configBeanType) {
        return create(configBeanType.packageName(),
                      Builder.DEFAULT_IMPL_PREFIX + configBeanType.className() + Builder.DEFAULT_SUFFIX);
    }

}
