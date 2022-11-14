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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.tools.creator.ActivatorCreatorConfigOptions;
import io.helidon.pico.tools.creator.ExternalModuleCreatorRequest;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ExternalModuleCreatorRequest}.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultExternalModuleCreatorRequest extends DefaultGeneralCreatorRequest implements ExternalModuleCreatorRequest {
    /*@Singular("packageNameToScan")*/ private final List<String> packageNamesToScan;
    /*@Singular("serviceTypeQualifier")*/ private final Map<String, Set<QualifierAndValue>> serviceTypeToQualifiersMap;
    private final boolean isInnerClassesProcessed;
    /*@Builder.Default*/ private final ActivatorCreatorConfigOptions activatorCreatorConfigOptions/* =
            new DefaultActivatorCreatorConfigOptions(false, true, false)*/;
    private final boolean isFailOnWarning;

    protected DefaultExternalModuleCreatorRequest(DefaultExternalModuleCreatorRequestBuilder builder) {
        super(builder);
        this.packageNamesToScan = Objects.isNull(builder.packageNamesToScan)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.packageNamesToScan);
        this.serviceTypeToQualifiersMap = Objects.isNull(builder.serviceTypeToQualifiersMap)
                ? Collections.emptyMap() : Collections.unmodifiableMap(builder.serviceTypeToQualifiersMap);
        this.isInnerClassesProcessed = builder.isInnerClassesProcessed;
        this.activatorCreatorConfigOptions = builder.activatorCreatorConfigOptions;
        this.isFailOnWarning = builder.isFailOnWarning;
    }

    public static DefaultExternalModuleCreatorRequestBuilder builder() {
        return new DefaultExternalModuleCreatorRequestBuilder() {};
    }

    public static abstract class DefaultExternalModuleCreatorRequestBuilder extends DefaultGeneralCreatorRequest.DefaultGeneralCreatorRequestBuilder {
        private List<String> packageNamesToScan;
        private Map<String, Set<QualifierAndValue>> serviceTypeToQualifiersMap;
        private boolean isInnerClassesProcessed;
        private ActivatorCreatorConfigOptions activatorCreatorConfigOptions;
        private boolean isFailOnWarning;

        public DefaultExternalModuleCreatorRequest build() {
            return new DefaultExternalModuleCreatorRequest(this);
        }

        public DefaultExternalModuleCreatorRequestBuilder packageNamesToScan(List<String> val) {
            this.packageNamesToScan = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultExternalModuleCreatorRequestBuilder packageNameToScan(String val) {
            if (Objects.isNull(this.packageNamesToScan)) {
                this.packageNamesToScan = new LinkedList<>();
            }
            this.packageNamesToScan.add(val);
            return this;
        }

        public DefaultExternalModuleCreatorRequestBuilder serviceTypeToQualifiersMap(Map<String, Set<QualifierAndValue>> val) {
            this.serviceTypeToQualifiersMap = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return this;
        }

        public DefaultExternalModuleCreatorRequestBuilder serviceTypeQualifier(String key, Set<QualifierAndValue> val) {
            if (Objects.isNull(this.serviceTypeToQualifiersMap)) {
                this.serviceTypeToQualifiersMap = new LinkedHashMap<>();
            }
            this.serviceTypeToQualifiersMap.put(key, val);
            return this;
        }

        public DefaultExternalModuleCreatorRequestBuilder isInnerClassesProcessed(boolean val) {
            this.isInnerClassesProcessed = val;
            return this;
        }

        public DefaultExternalModuleCreatorRequestBuilder activatorCreatorConfigOptions(ActivatorCreatorConfigOptions val) {
            this.activatorCreatorConfigOptions = val;
            return this;
        }

        public DefaultExternalModuleCreatorRequestBuilder isFailOnWarning(boolean val) {
            this.isFailOnWarning = val;
            return this;
        }
    }

}
