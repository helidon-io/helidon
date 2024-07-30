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
package io.helidon.tracing.providers.jaeger;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

public class OtelTestsJunitExtension implements Extension, BeforeAllCallback, AfterAllCallback {

    private static final String OTEL_AUTO_CONFIGURE_PROP = "otel.java.global-autoconfigure.enabled";
    private static final String OTEL_SDK_DISABLED_PROP = "otel.sdk.disabled";
    private String originalOtelSdkAutoConfiguredSetting;
    private String originalOtelSdkDisabledSetting;

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        if (originalOtelSdkAutoConfiguredSetting != null) {
            System.setProperty(OTEL_AUTO_CONFIGURE_PROP, originalOtelSdkAutoConfiguredSetting);
        }
        if (originalOtelSdkDisabledSetting != null) {
            System.setProperty(OTEL_SDK_DISABLED_PROP, originalOtelSdkDisabledSetting);
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        originalOtelSdkAutoConfiguredSetting = System.setProperty(OTEL_AUTO_CONFIGURE_PROP, "true");
        originalOtelSdkDisabledSetting = System.setProperty(OTEL_SDK_DISABLED_PROP, "false");
    }
}
