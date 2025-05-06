/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.opentelemetry;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;

@Prototype.Blueprint
@Prototype.Configured
@Prototype.CustomMethods(OtlpSpanExporterConfigSupport.class)
interface OtlpSpanExporterConfigBlueprint extends BasicSpanExporterConfigBlueprint {

    /**
     * {@link io.helidon.tracing.providers.opentelemetry.OtlpExporterProtocol} to use for the OTLP span exporter.
     *
     * @return exporter protocol
     */
    @Option.Configured
    @Option.Default(OtlpExporterProtocol.DEFAULT_NAME)
    OtlpExporterProtocol exporterProtocol();

    /**
     * Headers added to each outbound transmission of span data.
     *
     * @return headers
     */
    @Option.Configured
    @Option.Singular
    Map<String, String> headers();

    @Option.Configured
    Optional<Resource> privateKey();

    @Option.Configured
    Optional<Resource> clientCertificate();

    @Option.Configured
    Optional<Resource> trustedCertificate();

}
