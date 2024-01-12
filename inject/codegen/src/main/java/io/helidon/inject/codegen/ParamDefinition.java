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

package io.helidon.inject.codegen;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Definition of a single parameter (probably an injection point).
 *
 * @param owningElement      if this is an argument, the constructor or method this belongs to
 * @param methodConstantName in case this param belongs to a method, the constant of the method, otherwise null
 * @param elementInfo        element info of field or argument
 * @param constantName       name of the constant that holds the IpId of this parameter
 * @param declaredType       type of the field as required by the injection point
 * @param translatedType     type used for injection into this param (e.g. using Supplier where #type uses Provider),
 *                           same instance as #type if not translated
 * @param assignmentHandler  to provide source for assigning the result from injection context
 * @param kind               kind of the owning element (field, method, constructor)
 * @param ipName             name of the field or method
 * @param ipParamName        name of the field or parameter
 * @param fieldId            unique identification of this param within the type (field name, methodid + param name)
 * @param isStatic           whether the field is static
 * @param annotations        annotations on this injection param
 * @param qualifiers         qualifiers of this injection param
 * @param contract           contract expected for this injection param (ignoring list, supplier, optional etc.)
 * @param access             access modifier of this param
 * @param methodId           id of the method (unique identification of method within the class)
 */
record ParamDefinition(TypedElementInfo owningElement,
                       String methodConstantName,
                       TypedElementInfo elementInfo,
                       String constantName,
                       TypeName declaredType,
                       TypeName translatedType,
                       Consumer<ContentBuilder<?>> assignmentHandler,
                       ElementKind kind,
                       String ipName,
                       String ipParamName,
                       String fieldId,
                       boolean isStatic,
                       List<Annotation> annotations,
                       Set<Annotation> qualifiers,
                       TypeName contract,
                       AccessModifier access,
                       String methodId) {

}
