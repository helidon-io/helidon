/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.spi.test;

import io.helidon.pico.builder.config.ConfigBean;
import io.helidon.pico.builder.config.spi.ConfigBeanInfo;
import io.helidon.pico.builder.config.spi.MetaConfigBeanInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ConfigBean()
class MetaConfigBeanInfoTest {

    @Test
    void testToMetaConfigBeanInfo() {
        ConfigBean cfg = getClass().getAnnotation(ConfigBean.class);
        assertNotNull(cfg);
        MetaConfigBeanInfo metaCfg = ConfigBeanInfo.toMetaConfigBeanInfo(cfg, ConfigBean.class);
        assertThat(metaCfg.annotationType(), sameInstance(ConfigBean.class));
        assertThat(metaCfg.repeatable(), is(true));
        assertThat(metaCfg.drivesActivation(), is(true));
        assertThat(metaCfg.atLeastOne(), is(false));
        assertThat(metaCfg.key(), is(""));
    }

    @Test
    void testToConfigKey() {
        assertAll(
                () -> assertThat(ConfigBeanInfo.toConfigKey("maxInitialLineLength"), is("max-initial-line-length")),
                () -> assertThat(ConfigBeanInfo.toConfigKey("port"), is("port")),
                () -> assertThat(ConfigBeanInfo.toConfigKey("listenAddress"), is("listen-address"))
        );
    }

}
