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

package io.helidon.pico.config.test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.pico.config.spi.ConfigBeanAttributeVisitor;
import io.helidon.pico.config.testsubjects.DefaultASingletonConfig;
import io.helidon.pico.config.testsubjects.DefaultMySimpleConfig;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.testsupport.TestUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigBeanTest {

    @Test
    public void metaAttributes() {
        assertEquals(
                "{__meta={io.helidon.pico.config.spi.ConfigBeanInfo=MetaConfigBeanInfo(drivesActivation=true, "
                        + "defaultConfigBeanUsingDefaults=false, repeatable=true, key=my-simple-config)}, "
                        + "port={key=port, type=int}}",
                     TestUtils.sort(DefaultMySimpleConfig.__getMetaAttributes()).toString());
        assertEquals(
                "{__meta={io.helidon.pico.config.spi.ConfigBeanInfo=MetaConfigBeanInfo(drivesActivation=false, "
                        + "defaultConfigBeanUsingDefaults=true, repeatable=false, key=my-singleton-config)}, "
                        + "hostAddress={key=host-address, required=true, type=class java.lang.String, value=127.0.0"
                        + ".1}, listOfSimpleConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".MySimpleConfig, key=list-of-simple-config, type=interface java.util.List}, "
                        + "listOfSingletonConfigConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfig, key=list-of-singleton-config-config, type=interface java.util.List}, "
                        + "mapOfSimpleConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".MySimpleConfig, key=map-of-simple-config, type=interface java.util.Map}, "
                        + "mapOfSingletonConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfig, key=my-singleton-config, required=false, type=interface java.util.Map},"
                        + " password={key=password, required=false, type=char}, port={key=port, required=false, "
                        + "type=int, value=8080}, setOfSimpleConfig={componentType=interface io.helidon.pico.config"
                        + ".testsubjects.MySimpleConfig, key=set-of-simple-config, type=interface java.util.Set}, "
                        + "setOfSingletonConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfig, key=set-of-singleton-config, type=interface java.util.Set}, "
                        + "theSimpleConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".MySimpleConfig, key=the-simple-config, type=class java.util.Optional}, "
                        + "theSingletonConfig={componentType=interface io.helidon.pico.config.testsubjects"
                        + ".ASingletonConfig, key=the-singleton-config, type=class java.util.Optional}}",
                     TestUtils.sort(DefaultASingletonConfig.__getMetaAttributes()).toString());
    }

    @Test
    public void visitor() {
        final List<String> visited = new LinkedList<>();
        final MyCustomAttributeVisitor visitor = new MyCustomAttributeVisitor();

        {
            DefaultMySimpleConfig cfg = DefaultMySimpleConfig.builder().build();
            cfg.visitAttributes(visitor::visit, visited);
            assertEquals("[port:int]", visited.toString());
        }

        visited.clear();
        {
            DefaultASingletonConfig cfg = DefaultASingletonConfig.builder().build();
            cfg.visitAttributes(visitor::visit, visited);
            assertEquals(
                    "[port:int, hostAddress:java.lang.String, password:char[], theSimpleConfig:java.util"
                            + ".Optional[interface io.helidon.pico.config.testsubjects.MySimpleConfig], "
                            + "listOfSimpleConfig:java.util.List[interface io.helidon.pico.config.testsubjects"
                            + ".MySimpleConfig], setOfSimpleConfig:java.util.Set[interface io.helidon.pico.config"
                            + ".testsubjects.MySimpleConfig], mapOfSimpleConfig:java.util.Map[class java.lang.String,"
                            + " interface io.helidon.pico.config.testsubjects.MySimpleConfig], "
                            + "theSingletonConfig:java.util.Optional[interface io.helidon.pico.config.testsubjects"
                            + ".ASingletonConfig], listOfSingletonConfigConfig:java.util.List[interface io.helidon"
                            + ".pico.config.testsubjects.ASingletonConfig], setOfSingletonConfig:java.util"
                            + ".Set[interface io.helidon.pico.config.testsubjects.ASingletonConfig], "
                            + "mapOfSingletonConfig:java.util.Map[class java.lang.String, interface io.helidon.pico"
                            + ".config.testsubjects.ASingletonConfig]]",
                    visited.toString());
        }
    }

    static class MyCustomAttributeVisitor implements ConfigBeanAttributeVisitor<Object> {
        @Override
        public void visit(String key,
                          Supplier<Object> valueSupplier,
                          Map<String, Object> meta,
                          Object userDefinedCtx,
                          Class<?> type,
                          Class<?>... typeArguments) {
            String val = key + ":" + DefaultTypeName.create(type).declaredName();
            if (typeArguments.length > 0) {
                val += Arrays.asList(typeArguments);
            }
            ((List<String>) userDefinedCtx).add(val);
            assertNotNull(meta);
            assertNotNull(valueSupplier);
            assertNotNull(type);
        }
    }


    static String toSBeanDescription(Object configBean) {
        if (Objects.isNull(configBean)) {
            return null;
        }

        String str = configBean.toString();
        if (Objects.isNull(str)) {
            return null;
        }

        int pos = str.indexOf("}");
        return (pos > 0) ? str.substring(0, pos + 1) : str;
    }
}
