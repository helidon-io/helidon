/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics.cdi;

import java.lang.reflect.Field;

import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(OverridingOciMetricsBean.class)
@AddConfig(key = "oci.metrics.product", value = TestOverridingBean.PRODUCT)
@AddConfig(key = "oci.metrics.fleet", value = TestOverridingBean.FLEET)
class TestOverridingBean {

    static final String PRODUCT = "overriding-product-name";
    static final String FLEET = "overriding-fleet";

    // The injected bean should be the test one which overrides the default implementation.
    @Inject
    private OciMetricsBean bean;

    @Test
    void checkOverriding() throws NoSuchFieldException, IllegalAccessException {
        assertThat("Config key", bean.configKey(), is(equalTo("oci.metrics")));
        assertThat("Injected bean", bean, instanceOf(OverridingOciMetricsBean.class));
        OciMetricsSupport ociMetricsSupport = bean.ociMetricsSupport();

        String namespace = getStringField("namespace", ociMetricsSupport);
        String resourceGroup = getStringField("resourceGroup", ociMetricsSupport);

        assertThat("Effective namespace", namespace, is(equalTo(PRODUCT)));
        assertThat("Effective namespace", resourceGroup, is(equalTo(FLEET)));
    }

    private String getStringField(String fieldName, OciMetricsSupport ociMetricsSupport)
            throws NoSuchFieldException, IllegalAccessException {
        // Except for testing, there is no need for OciMetricsSupport to expose methods returning the namespace and resource
        // group so just use reflection rather than add those methods. It's only a test so using reflection is not so bad.
        Field field = OciMetricsSupport.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(ociMetricsSupport);
    }
}
