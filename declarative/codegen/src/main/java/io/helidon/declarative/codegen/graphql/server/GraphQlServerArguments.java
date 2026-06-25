/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.graphql.server;

import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypedElementInfo;

import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;

import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ARGUMENT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NAME;
import static java.util.function.Predicate.not;

final class GraphQlServerArguments {
    private GraphQlServerArguments() {
    }

    static void validateSpecialParameter(TypeInfo endpoint,
                                         TypedElementInfo method,
                                         TypedElementInfo parameter,
                                         Set<Annotation> annotations) {
        if (Annotations.findFirst(GRAPHQL_ARGUMENT, annotations).isPresent()) {
            throw new CodegenException("@GraphQl.Argument cannot be used on special GraphQL resolver parameter "
                                               + endpoint.typeName().fqName() + "."
                                               + method.elementName() + " parameter " + parameter.elementName(),
                                       parameter.originatingElementValue());
        }
    }

    static String graphQlName(TypeInfo endpoint,
                              TypedElementInfo method,
                              TypedElementInfo parameter,
                              Set<Annotation> annotations) {
        Optional<String> argumentName = Annotations.findFirst(GRAPHQL_ARGUMENT, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        Optional<String> graphQlName = Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        if (argumentName.isPresent() && graphQlName.isPresent()
                && !argumentName.orElseThrow().equals(graphQlName.orElseThrow())) {
            throw new CodegenException("@GraphQl.Argument value and @GraphQl.Name cannot declare different GraphQL "
                                               + "argument names on " + endpoint.typeName().fqName() + "."
                                               + method.elementName() + " parameter " + parameter.elementName(),
                                       parameter.originatingElementValue());
        }
        return GraphQlServerExtension.validateGraphQlName(argumentName.or(() -> graphQlName)
                                                                  .orElse(parameter.elementName()),
                                                          parameter.originatingElementValue());
    }

    static void validateDefaultValue(String defaultValue,
                                     TypeInfo endpoint,
                                     TypedElementInfo method,
                                     TypedElementInfo parameter) {
        try {
            Parser.parseValue(defaultValue);
        } catch (InvalidSyntaxException e) {
            throw new CodegenException("@GraphQl.DefaultValue must be a valid GraphQL SDL literal on "
                                               + endpoint.typeName().fqName() + "."
                                               + method.elementName() + " parameter " + parameter.elementName()
                                               + ": " + e.getMessage(),
                                       parameter.originatingElementValue());
        }
    }
}
