/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.referencecountedcontext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.context.NormalScope;

/**
 * An annotation indicating the bean that is annotated belongs to the
 * {@link ReferenceCountedContext}.
 *
 * <h2>Use Cases</h2>
 *
 * <p>Annotate a class with {@link ReferenceCounted @ReferenceCounted}
 * when all of the following apply:</p>
 *
 * <ul>
 *
 * <li>You need an instance of your class to be associated with
 * exactly one thread.</li>
 *
 * <li>You need the single thread-specific instance of your class to be
 * destroyed eagerly, but not too eagerly.  Specifically, you need the
 * instance to be destroyed when it is no longer in use by any other
 * object that is not itself destroyed.</li>
 *
 * <li>If an instance of your class is destroyed, and some other
 * object asks for an instance of your class, a new instance should be
 * provided.</li>
 *
 * </ul>
 *
 * <p>Another way of thinking of {@link
 * ReferenceCounted @ReferenceCounted}-annotated classes is that they
 * behave as though they were members of a slightly shorter-lived
 * {@linkplain javax.enterprise.context.ApplicationScoped application
 * scope}.</p>
 *
 * <p>Still another way of thinking of {@link
 * ReferenceCounted @ReferenceCounted}-annotated classes is that they
 * behave as though they were members of {@linkplain
 * javax.enterprise.context.RequestScoped request scope}, but with the
 * notional request starting upon first instantiation.</p>
 *
 * @see ReferenceCountedContext
 */
@Documented
@NormalScope(passivating = false)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD })
public @interface ReferenceCounted {

}
