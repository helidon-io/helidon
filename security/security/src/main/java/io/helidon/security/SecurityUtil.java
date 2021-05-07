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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.security.spi.AuditProvider;
import io.helidon.security.spi.SecurityProvider;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;

/**
 * Utility class for internal needs.
 */
final class SecurityUtil {
    private static final Logger LOGGER = Logger.getLogger(SecurityUtil.class.getName());

    private SecurityUtil() {
    }

    static Set<Class<? extends Annotation>> getAnnotations(Map<SecurityProvider, Boolean> providers) {
        Set<Class<? extends Annotation>> annotations = new HashSet<>();
        for (SecurityProvider provider : providers.keySet()) {
            annotations.addAll(provider.supportedAnnotations());
        }
        return annotations;
    }

    static Tracer getTracer(boolean tracingEnabled, Tracer builderTracer) {
        if (tracingEnabled) {
            return (builderTracer == null) ? GlobalTracer.get() : builderTracer;
        } else {
            return NoopTracerFactory.create();
        }
    }

    static AuditProvider.TracedAuditEvent wrapEvent(String tracingId, AuditProvider.AuditSource auditSource, AuditEvent event) {
        return new AuditProvider.TracedAuditEvent() {
            @Override
            public AuditProvider.AuditSource auditSource() {
                return auditSource;
            }

            @Override
            public String tracingId() {
                return tracingId;
            }

            @Override
            public String eventType() {
                return event.eventType();
            }

            @Override
            public Optional<Throwable> throwable() {
                return event.throwable();
            }

            @Override
            public List<AuditEvent.AuditParam> params() {
                return event.params();
            }

            @Override
            public String messageFormat() {
                return event.messageFormat();
            }

            @Override
            public AuditEvent.AuditSeverity severity() {
                return event.severity();
            }

            @Override
            public String toString() {
                return event.toString();
            }
        };
    }

    static String forAuditNamed(List<? extends NamedProvider<?>> collection) {
        return collection.stream().map(p -> p.getName() + ": " + p.getProvider().getClass().getName())
                .collect(Collectors.toList()).toString();
    }

    static String forAudit(Collection<?> collection) {
        return collection.stream().map(p -> p.getClass().getName()).collect(Collectors.toList()).toString();
    }

    static <T> T instantiate(String className, Class<? extends T> type, Config config) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (Exception e) {
            throw new SecurityException("Failed to get class " + className, e);
        }

        Exception configException = null;

        if (null != config) {
            try {
                return type.cast(config.as(clazz).get());
            } catch (ClassCastException e) {
                throw new SecurityException("Class " + className + " is not instance of expected type: " + type.getName());
            } catch (ConfigMappingException e) {
                LOGGER.log(Level.FINEST,
                           e,
                           () -> "Class " + className + " failed to get mapped by config. Will attempt public default "
                                   + "constructor");
                configException = e;
            }
        }

        // last chance - public default constructor
        try {
            return type.cast(clazz.getConstructor().newInstance());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not instantiate: " + className + ". Class must either have a default public"
                    + " constructor or be mappable by Config");

            configException = ((null == configException) ? e : configException);
            throw new SecurityException("Failed to load " + type
                    .getName() + " from class " + clazz + ", parsing from config failed with: "
                                                + extractExceptionDetails(configException), e);
        }
    }

    private static String extractExceptionDetails(Exception configException) {
        Throwable prev = configException;
        Throwable cause;

        StringBuilder details = new StringBuilder();
        details.append(configException.getMessage());

        while (true) {
            cause = prev.getCause();
            if ((null == cause) || (cause == prev)) {
                break;
            }
            details.append(", caused by: ").append(cause.getMessage());
            prev = cause;
        }
        return details.toString();
    }
}
