/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.List;
import java.util.Optional;

import io.helidon.security.spi.AuditProvider;

/**
 * An audit event to store using an Audit provider.
 * You should provide your own implementation class.
 *
 * @see SecurityContext#audit(AuditEvent)
 * @see AuditProvider
 */
public interface AuditEvent {
    /**
     * Reserved event type prefix for security component.
     */
    String SECURITY_TYPE_PREFIX = "security";
    /**
     * Reserved event type prefix for authentication events.
     * You may trigger such audit events (e.g. when writing an authentication provider, or from app), yet
     * they MUST be related to authentication.
     */
    String AUTHN_TYPE_PREFIX = "authn";
    /**
     * Reserved event type prefix for authorization events.
     * You may trigger such audit events (e.g. when writing an authorization provider, or from app), yet
     * they MUST be related to authorization.
     */
    String AUTHZ_TYPE_PREFIX = "authz";
    /**
     * Reserved event type prefix for outbound security (such as identity propagation) events.
     * You may trigger such audit events (e.g. when writing an identity propagation provider, or from app), yet
     * they MUST be related to identity propagation.
     */
    String OUTBOUND_TYPE_PREFIX = "outbound";
    /**
     * Reserved event type prefix for audit events.
     * You may trigger such events ONLY when writing audit providers.
     */
    String AUDIT_TYPE_PREFIX = "audit";

    /**
     * Gets the type of this {@code AuditEvent}.
     *
     * @return the type of this {@code AuditEvent} represented as String.
     */
    String eventType();

    /**
     * Gets an {@code Throwable} object from which additional audit information
     * can be obtained.
     *
     * @return an {@code Throwable} with additional information if available.
     */
    Optional<Throwable> throwable();

    /**
     * Parameters of this audit event, used in {@link String#format(String, Object...)}
     * when creating the audit message.
     *
     * @return parameters of this audit message
     */
    List<AuditParam> params();

    /**
     * Gets the message format of this {@code AuditEvent} to be used with
     * {@link String#format(String, Object...)}.
     *
     * @return English message format (this is a fallback if internationalization is not configured).
     */
    String messageFormat();

    /**
     * Gets the severity of this {@code AuditEvent}.
     *
     * @return severity
     */
    AuditSeverity severity();

    /**
     * Severity of {@code AuditEvent}.
     */
    enum AuditSeverity {
        /**
         * General information.
         */
        INFO,
        /**
         * Security event success.
         */
        SUCCESS,
        /**
         * Security warning.
         */
        WARN,
        /**
         * Security event error - we tried to process security, but failed with exception (equivalent of http 500).
         */
        ERROR,
        /**
         * Security event failure - we tried to process security, but the result was negative (e.g. authorization denied,
         * authentication failed).
         */
        FAILURE,
        /**
         * Audit framework failure - we cannot correctly audit.
         */
        AUDIT_FAILURE
    }

    /**
     * Named parameters of audit event.
     * If sensitive, the audit provider should either encrypt them or
     * obfuscate them.
     */
    final class AuditParam {
        private final String name;
        private final Object parameter;
        private final boolean sensitive;

        private AuditParam(String name, Object parameter, boolean sensitive) {
            this.name = name;
            this.parameter = parameter;
            this.sensitive = sensitive;
        }

        /**
         * New parameter of any type.
         *
         * @param name      parameter name
         * @param parameter parameter value
         * @return Plain audit parameter
         */
        public static AuditParam plain(String name, Object parameter) {
            return new AuditParam(name, parameter, false);
        }

        /**
         * New parameter of any type that is sensitive.
         *
         * @param name      parameter name
         * @param parameter parameter value
         * @return Sensitive audit parameter
         */
        public static AuditParam sensitive(String name, Object parameter) {
            return new AuditParam(name, parameter, true);
        }

        /**
         * Name of this parameter.
         * @return name
         */
        public String name() {
            return name;
        }

        /**
         * Value of this parameter.
         * @return value or empty if not defined (null).
         */
        public Optional<Object> value() {
            return Optional.ofNullable(parameter);
        }

        /**
         * Whether this is sensitive information (such as passwords).
         * Handle sensitive information carefully - e.g. do not log it.
         * @return {@code true} if this is a sensitive value
         */
        public boolean isSensitive() {
            return sensitive;
        }

        @Override
        public String toString() {
            return "AuditParam{"
                    + "name='" + name + '\''
                    + ", parameter=" + (sensitive ? "<sensitive>" : parameter)
                    + ", sensitive=" + sensitive
                    + '}';
        }
    }
}
