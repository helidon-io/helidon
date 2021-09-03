/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.abac.policy.el;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.StandardELContext;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.abac.policy.spi.PolicyExecutor;

/**
 * {@link PolicyExecutor} for Java EE Expression Language (EL).
 *
 * See tutorial for details of the EL: <a href="https://docs.oracle.com/javaee/7/tutorial/jsf-el005.htm#BNAIK">https://docs
 * .oracle.com/javaee/7/tutorial/jsf-el005.htm#BNAIK</a>
 */
public final class JavaxElPolicyExecutor implements PolicyExecutor {
    private static final Logger LOGGER = Logger.getLogger(JavaxElPolicyExecutor.class.getName());
    private static final AttributeResolver ATTRIBUTE_RESOLVER = new AttributeResolver();

    private final ExpressionFactory ef;
    private final List<CustomFunction> customMethods = new LinkedList<>();

    private JavaxElPolicyExecutor(Builder builder) {
        this.ef = builder.expressionFactory;
        this.customMethods.addAll(builder.customMethods);
    }

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance based on configuration.
     *
     * @param config configuration located on the key provided by {@link JavaxElPolicyExecutorService#configKey()}
     * @return a new configured instance
     */
    public static JavaxElPolicyExecutor create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new instance configured with defaults.
     *
     * @return a new policy executor
     */
    public static JavaxElPolicyExecutor create() {
        return builder().build();
    }

    @Override
    public void executePolicy(String policyStatement, Errors.Collector collector, ProviderRequest request) {
        StandardELContext context = new StandardELContext(ef);
        context.addELResolver(ATTRIBUTE_RESOLVER);

        FunctionMapper functions = context.getFunctionMapper();
        VariableMapper variables = context.getVariableMapper();

        customMethods.forEach(customFunction -> functions.mapFunction(customFunction.prefix,
                                                                      customFunction.localName,
                                                                      customFunction.method));

        Subject userSubject = request.subject().orElse(SecurityContext.ANONYMOUS);

        variable(variables, "user", userSubject, Subject.class);
        variable(variables, "subject", userSubject, Subject.class);
        variable(variables, "service", request.service().orElse(SecurityContext.ANONYMOUS), Subject.class);
        variable(variables, "env", request.env(), SecurityEnvironment.class);
        variable(variables, "object", request.getObject().orElse(null), Object.class);
        variable(variables, "request", request, ProviderRequest.class);

        try {
            ValueExpression expression = ef.createValueExpression(context, policyStatement, boolean.class);
            boolean value = (boolean) expression.getValue(context);
            if (!value) {
                collector.fatal(this, "Policy statement \"" + policyStatement + "\" evaluated to false");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, e, () -> "Statement " + policyStatement + " evaluation failed");
            throw new SecurityException("Policy statement \"" + policyStatement + "\" evaluated to an exception", e);
        }
    }

    private <T> void variable(VariableMapper variables, String varName, T object, Class<T> objectClass) {
        ValueExpression variable = ef.createValueExpression(object, objectClass);
        variables.setVariable(varName, variable);
    }

    /**
     * A fluent API builder for {@link JavaxElPolicyExecutor}.
     */
    public static final class Builder implements io.helidon.common.Builder<JavaxElPolicyExecutor> {
        private final List<CustomFunction> customMethods = new LinkedList<>();
        private ExpressionFactory expressionFactory;

        private Builder() {
            // configure built-in methods
            try {
                // roles
                customMethods.add(new CustomFunction("",
                                                     "inRole",
                                                     BuiltInMethods.class.getMethod("inRole", Subject.class, String.class)));
                customMethods.add(new CustomFunction("", "inRoles", BuiltInMethods.class.getMethod("inRoles", Subject.class,
                                                                                                   String[].class)));

                // scopes
                customMethods.add(new CustomFunction("",
                                                     "inScope",
                                                     BuiltInMethods.class.getMethod("inScope", Subject.class, String.class)));
                customMethods.add(new CustomFunction("",
                                                     "inScopes",
                                                     BuiltInMethods.class.getMethod("inScopes", Subject.class, String[].class)));
            } catch (NoSuchMethodException e) {
                throw new SecurityException("Failed to configure expression language built-in methods", e);
            }
        }

        @Override
        public JavaxElPolicyExecutor build() {
            if (null == expressionFactory) {
                try {
                    expressionFactory = ExpressionFactory.newInstance();
                } catch (ELException e) {
                    throw new SecurityException("Failed to configure ABAC Policy support for Jakarta Expression Language,"
                                                        + " no implementation found through service loader.", e);
                }
            }
            return new JavaxElPolicyExecutor(this);
        }

        /**
         * Configure a specific expression factory to use with this executor.
         * If this method is not called, the executor uses default expression factory (if one is on the classpath).
         *
         * @param factory factory to use
         * @return updated builder instance
         */
        public Builder expressionFactory(ExpressionFactory factory) {
            this.expressionFactory = factory;
            return this;
        }

        /**
         * Add a custom method to be available in expressions without a prefix.
         * This method must be a static method.
         *
         * @param localName name to call this function in an expression
         * @param method    method to invoke (may have parameters - these can be passed from the script)
         * @return updated builder instance
         * @see #addMethod(String, String, Method)
         */
        public Builder addMethod(String localName, Method method) {
            return addMethod("", localName, method);
        }

        /**
         * Add a custom method to be available in expressions.
         * This method must be a static method.
         *
         * <p>
         * <b>Example</b><br>
         * prefix: fun<br>
         * localName: inRole<br>
         * method: public static boolean isSubjectInRole(Subject subject, String roleName)<br>
         * expression: "${fun:inRole(user, "some_role"}"<br>
         *
         * @param prefix    prefix to use before the name in an expression
         * @param localName name to call this function in an expression
         * @param method    method to invoke (may have parameters - these can be passed from the script)
         * @return updated builder instance
         */
        public Builder addMethod(String prefix, String localName, Method method) {
            customMethods.add(new CustomFunction(prefix, localName, method));
            return this;
        }

        /**
         * Updated builder from configuration.
         *
         * @param config configuration to update from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            // currently no configurable options exist
            return this;
        }
    }

    private static final class CustomFunction {
        private final String prefix;
        private final String localName;
        private final Method method;

        private CustomFunction(String prefix, String localName, Method method) {
            this.prefix = prefix;
            this.localName = localName;
            this.method = method;
        }
    }
}
