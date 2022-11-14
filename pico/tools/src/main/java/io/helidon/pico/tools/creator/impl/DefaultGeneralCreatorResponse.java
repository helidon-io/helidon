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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.pico.tools.creator.GeneralCodeGenDetail;
import io.helidon.pico.tools.creator.GeneralCreatorResponse;
import io.helidon.pico.types.TypeName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.GeneralCreatorResponse}.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralCreatorResponse extends DefaultGeneralResponse implements GeneralCreatorResponse {
    // source related ...
    private final Collection<TypeName> serviceTypeNames;
    @JsonIgnore /*@Singular("serviceTypeDetail")*/ private final Map<TypeName, GeneralCodeGenDetail> serviceTypeDetails;
    // resource related ...
    @JsonIgnore /*@Singular("metaInfServiceList")*/ private final Map<String, List<String>> metaInfServices;

    protected DefaultGeneralCreatorResponse(DefaultGeneralCreatorResponseBuilder builder) {
        super(builder);
        this.serviceTypeNames = Objects.nonNull(builder.serviceTypeNames)
                ? Collections.unmodifiableCollection(builder.serviceTypeNames) : Collections.emptyList();
        this.serviceTypeDetails = Objects.nonNull(builder.serviceTypeDetails)
                ? Collections.unmodifiableMap(builder.serviceTypeDetails) : Collections.emptyMap();
        this.metaInfServices = Objects.nonNull(builder.metaInfServices)
                ? Collections.unmodifiableMap(builder.metaInfServices) : Collections.emptyMap();
    }

    public static DefaultGeneralCreatorResponseBuilder builder() {
        return new DefaultGeneralCreatorResponseBuilder() {};
    }


    public static abstract class DefaultGeneralCreatorResponseBuilder extends DefaultGeneralResponse.DefaultGeneralResponseBuilder {
        private Collection<TypeName> serviceTypeNames;
        private Map<TypeName, GeneralCodeGenDetail> serviceTypeDetails;
        private Map<String, List<String>> metaInfServices;

        public DefaultGeneralCreatorResponse build() {
            return new DefaultGeneralCreatorResponse(this);
        }

        public DefaultGeneralCreatorResponseBuilder serviceTypeNames(Collection<TypeName> val) {
            this.serviceTypeNames = Objects.nonNull(val) ? new LinkedList<>(val) : null;
            return this;
        }

        public DefaultGeneralCreatorResponseBuilder serviceTypeDetails(Map<TypeName, GeneralCodeGenDetail> val) {
            this.serviceTypeDetails = Objects.nonNull(val) ? new LinkedHashMap<>(val) : null;
            return this;
        }

        public DefaultGeneralCreatorResponseBuilder serviceTypeDetail(TypeName key, GeneralCodeGenDetail val) {
            if (Objects.isNull(serviceTypeDetails)) {
                this.serviceTypeDetails = new LinkedHashMap<>();
            }
            this.serviceTypeDetails.put(key, val);
            return this;
        }

        public DefaultGeneralCreatorResponseBuilder metaInfServices(Map<String, List<String>> val) {
            this.metaInfServices = Objects.nonNull(val) ? new LinkedHashMap<>(val) : null;
            return this;
        }
    }

}
