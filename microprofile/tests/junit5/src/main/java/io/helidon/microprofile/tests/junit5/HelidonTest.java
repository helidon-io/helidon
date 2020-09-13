/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tests.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * An annotation making this test class a CDI bean with support for injection.
 * <p>
 * There is no need to provide {@code beans.xml} (actually it is not recommended, as it would combine beans
 * from all tests), instead use {@link io.helidon.microprofile.tests.junit5.AddBean},
 * {@link io.helidon.microprofile.tests.junit5.AddExtension}, and {@link io.helidon.microprofile.tests.junit5.AddConfig}
 * annotations to control the shape of the container.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(HelidonJunitExtension.class)
public @interface HelidonTest {
    /**
     * Whether discovery is automated or disabled.
     * <p>
     * When discovery is enabled, the whole classpath is scanned for bean archives (jar files containing
     * {@code META-INF/beans.xml}) and all beans and extensions are added automatically.
     * <p>
     * When discovery is disabled, CDI would only contain the CDI implementation itself and beans and extensions added
     * through annotations {@link io.helidon.microprofile.tests.junit5.AddBean} and
     * {@link io.helidon.microprofile.tests.junit5.AddExtension}
     *
     * @return whether to do discovery, defaults to {@code true}
     */
    boolean discovery() default true;
}
