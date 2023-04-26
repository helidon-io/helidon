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

package io.helidon.builder.config;

import java.util.Map;
import java.util.Objects;

import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ConfigBean()
class MetaConfigBeanInfoTest {

    @Test
    void testToMetaConfigBeanInfoFromConfigBean() {
        ConfigBean cfg = Objects.requireNonNull(getClass().getAnnotation(ConfigBean.class));
        MetaConfigBeanInfo metaCfg = ConfigBeanInfo.toMetaConfigBeanInfo(cfg, ConfigBean.class);
        assertThat(metaCfg.annotationType(), sameInstance(ConfigBean.class));
        assertThat(metaCfg.repeatable(), is(false));
        assertThat(metaCfg.drivesActivation(), is(false));
        assertThat(metaCfg.atLeastOne(), is(false));
        assertThat(metaCfg.wantDefaultConfigBean(), is(false));
        assertThat(metaCfg.value(), is(""));
    }

    @Test
    void testToMetaConfigBeanInfoFromMetaAttributes() {
        Map<String, Object> metaMap = Map.of(ConfigBeanInfo.TAG_KEY, "fake-config",
                                             ConfigBeanInfo.TAG_REPEATABLE, "true",
                                             ConfigBeanInfo.TAG_DRIVES_ACTIVATION, "true",
                                             ConfigBeanInfo.TAG_AT_LEAST_ONE, "true",
                                             ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN, "true",
                                             ConfigBeanInfo.TAG_LEVEL_TYPE, "ROOT");
        MetaConfigBeanInfo metaCfg = ConfigBeanInfo.toMetaConfigBeanInfo(metaMap);
        assertThat(metaCfg.annotationType(), sameInstance(ConfigBean.class));
        assertThat(metaCfg.repeatable(), is(true));
        assertThat(metaCfg.drivesActivation(), is(true));
        assertThat(metaCfg.atLeastOne(), is(true));
        assertThat(metaCfg.wantDefaultConfigBean(), is(true));
        assertThat(metaCfg.levelType(), is(ConfigBean.LevelType.ROOT));
        assertThat(metaCfg.value(), is("fake-config"));
    }

    @Test
    void toConfigAttributeName() {
        assertAll(
                () -> assertThat(ConfigBeanInfo.toConfigAttributeName("maxInitialLineLength"), is("max-initial-line-length")),
                () -> assertThat(ConfigBeanInfo.toConfigAttributeName("port"), is("port")),
                () -> assertThat(ConfigBeanInfo.toConfigAttributeName("listenAddress"), is("listen-address")),
                () -> assertThat(ConfigBeanInfo.toConfigAttributeName("Http2Config"), is("http2-config"))
        );
    }

    @Test
    void toConfigABeanName() {
        assertAll(
                () -> assertThat(ConfigBeanInfo.toConfigBeanName("MyClient"), is("my-client")),
                () -> assertThat(ConfigBeanInfo.toConfigBeanName("Http2Config"), is("http2")),
                () -> assertThat(ConfigBeanInfo.toConfigBeanName("Http2ConfigTest"), is("http2-config-test"))
        );
    }

}
