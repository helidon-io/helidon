/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.spi;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityLevel;
import io.helidon.security.Subject;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Example of an authentication provider implementation - synchronous.
 * This is a full-blows example of a provider that requires additional configuration on a resource.
 */
public class AtnProviderSync extends SynchronousProvider implements AuthenticationProvider {
    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {

        // first obtain the configuration of this request
        // either from annotation, custom object or config
        AtnObject myObject = getCustomObject(providerRequest.endpointConfig());

        if (null == myObject) {
            // I do not have my required information, this request is probably not for me
            return AuthenticationResponse.abstain();
        }

        if (myObject.isValid()) {
            // now authenticate - this example just creates a subject
            // based on the value (user subject) and size (group subject)
            return AuthenticationResponse.success(Subject.builder()
                                                          .addPrincipal(Principal.create(myObject.getValue()))
                                                          .addGrant(Role.create("role_" + myObject.getSize()))
                                                          .build());
        } else {
            return AuthenticationResponse.failed("Invalid request");
        }
    }

    private AtnObject getCustomObject(EndpointConfig epConfig) {
        // order I choose - this depends on type of security you implement and your choice:
        // 1) custom object in request (as this must be explicitly done by a developer)
        Optional<? extends AtnObject> opt = epConfig.instance(AtnObject.class);
        if (opt.isPresent()) {
            return opt.get();
        }

        // 2) configuration in request
        opt = epConfig.config("atn-object").flatMap(conf -> conf.as(AtnObject::from).asOptional());
        if (opt.isPresent()) {
            return opt.get();
        }

        // 3) annotations on target
        List<AtnAnnot> annots = new ArrayList<>();
        for (SecurityLevel securityLevel : epConfig.securityLevels()) {
            annots.addAll(securityLevel.combineAnnotations(AtnAnnot.class, EndpointConfig.AnnotationScope.values()));
        }
        if (annots.isEmpty()) {
            return null;
        } else {
            return AtnObject.from(annots.get(0));
        }
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(AtnAnnot.class);
    }

    /**
     * This is an example annotation to see how to work with them.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
    @Documented
    public @interface AtnAnnot {
        /**
         * This is an example value.
         *
         * @return some value
         */
        String value();

        /**
         * This is an example value.
         *
         * @return some size
         */
        int size() default 4;
    }

    /**
     * This is an example custom object.
     * Also acts as an object to get configuration in config.
     */
    public static class AtnObject {
        private String value;
        private int size = 4;

        /**
         * Load this object instance from configuration.
         *
         * @param config configuration
         * @return a new instance
         */
        public static AtnObject from(Config config) {
            AtnObject result = new AtnObject();
            config.get("value").asString().ifPresent(result::setValue);
            config.get("size").asInt().ifPresent(result::setSize);
            return result;
        }

        static AtnObject from(AtnAnnot annot) {
            AtnObject result = new AtnObject();
            result.setValue(annot.value());
            result.setSize(annot.size());
            return result;
        }

        static AtnObject from(String value, int size) {
            AtnObject result = new AtnObject();
            result.setValue(value);
            result.setSize(size);
            return result;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public boolean isValid() {
            return null != value;
        }
    }
}
