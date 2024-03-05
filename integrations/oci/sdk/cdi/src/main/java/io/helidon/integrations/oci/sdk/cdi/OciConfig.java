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
package io.helidon.integrations.oci.sdk.cdi;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A convenient {@link Qualifier} annotation indicating that the qualified element is sourced from an <a
 * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#File_Name_and_Location">OCI configuration
 * file</a> in some way, because, for example, other means of sourcing the element might otherwise be available.
 *
 * <p>Consider, as an arbitrary example, the {@link com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider}
 * class, whose instances can be created either manually from its {@linkplain
 * com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder
 * associated builder class} or from an {@linkplain
 * com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider#SessionTokenAuthenticationDetailsProvider(com.oracle.bmc.ConfigFileReader.ConfigFile)
 * OCI configuration file}. It might be useful to qualify production in the second case with this qualifier
 * annotation.</p>
 */
@Documented
@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, METHOD, PARAMETER, TYPE })
public @interface OciConfig {

    /**
     * An {@link AnnotationLiteral} that implements the {@link OciConfig} annotation interface.
     */
    final class Literal extends AnnotationLiteral<OciConfig> implements OciConfig {

        private static final long serialVersionUID = 1L;

        /**
         * The sole instance of this class.
         */
        public static final Literal INSTANCE = new Literal();

        private Literal() {
            super();
        }

    }

}
