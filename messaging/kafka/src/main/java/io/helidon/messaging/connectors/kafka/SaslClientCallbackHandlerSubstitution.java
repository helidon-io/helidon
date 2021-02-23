/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.connectors.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.spi.LoginModule;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.SaslExtensions;
import org.apache.kafka.common.security.auth.SaslExtensionsCallback;
import org.apache.kafka.common.security.authenticator.LoginManager;
import org.apache.kafka.common.security.scram.ScramExtensionsCallback;
import org.apache.kafka.common.security.scram.internals.ScramMechanism;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TargetClass(org.apache.kafka.common.security.authenticator.SaslClientCallbackHandler.class)
@SuppressWarnings("checkstyle:RedundantModifier")
final class SaslClientCallbackHandlerSubstitution implements AuthenticateCallbackHandler {

    @Alias
    private String mechanism;

    @Inject
    private Logger logger;

    @Inject
    private Subject subject;

    @Substitute
    public SaslClientCallbackHandlerSubstitution() {
        logger = LoggerFactory.getLogger(LoginManager.class);
    }

    @Override
    @Substitute
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {
        this.mechanism = saslMechanism;
        this.subject = null;

        int entrySize = jaasConfigEntries.size();
        if (entrySize == 0) {
            logger.warn("Missing JAAS config entry, missing or malformed sasl.jaas.config property.");
            return;
        } else if (entrySize > 1) {
            logger.warn("Multiple JAAS config entries, Kafka client's sasl.jaas.config can have only one JAAS config entry.");
            return;
        }

        AppConfigurationEntry jaasConfigEntry = jaasConfigEntries.get(0);
        String jaasLoginModuleName = jaasConfigEntry.getLoginModuleName();
        subject = new Subject();

        try {
            Class.forName(jaasLoginModuleName)
                    .asSubclass(LoginModule.class)
                    .getDeclaredConstructor()
                    .newInstance()
                    .initialize(subject, this, new HashMap<>(), jaasConfigEntry.getOptions());
        } catch (ReflectiveOperationException e) {
            throw new KafkaException("Can't instantiate JAAS login module" + jaasLoginModuleName, e);
        }
    }

    @Override
    @Substitute
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        // Subject.getSubject doesn't return proper subject in native image
        // Remove substitution when https://github.com/oracle/graal/issues/2745 is fixed
        // Subject subject = Subject.getSubject(AccessController.getContext());

        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nc = (NameCallback) callback;
                if (subject != null && !subject.getPublicCredentials(String.class).isEmpty()) {
                    nc.setName(subject.getPublicCredentials(String.class).iterator().next());
                } else {
                    nc.setName(nc.getDefaultName());
                }
            } else if (callback instanceof PasswordCallback) {
                if (subject != null && !subject.getPrivateCredentials(String.class).isEmpty()) {
                    char[] password = subject.getPrivateCredentials(String.class).iterator().next().toCharArray();
                    ((PasswordCallback) callback).setPassword(password);
                } else {
                    String errorMessage = "Could not login: the client is being asked for a password, but the Kafka"
                            + " client code does not currently support obtaining a password from the user.";
                    throw new UnsupportedCallbackException(callback, errorMessage);
                }
            } else if (callback instanceof RealmCallback) {
                RealmCallback rc = (RealmCallback) callback;
                rc.setText(rc.getDefaultText());
            } else if (callback instanceof AuthorizeCallback) {
                AuthorizeCallback ac = (AuthorizeCallback) callback;
                String authId = ac.getAuthenticationID();
                String authzId = ac.getAuthorizationID();
                ac.setAuthorized(authId.equals(authzId));
                if (ac.isAuthorized()) {
                    ac.setAuthorizedID(authzId);
                }
            } else if (callback instanceof ScramExtensionsCallback) {
                if (ScramMechanism.isScram(mechanism) && subject != null && !subject.getPublicCredentials(Map.class).isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> extensions =
                            (Map<String, String>) subject.getPublicCredentials(Map.class).iterator().next();
                    ((ScramExtensionsCallback) callback).extensions(extensions);
                }
            } else if (callback instanceof SaslExtensionsCallback) {
                if (!SaslConfigs.GSSAPI_MECHANISM.equals(mechanism)
                        && subject != null && !subject.getPublicCredentials(SaslExtensions.class).isEmpty()) {
                    SaslExtensions extensions = subject.getPublicCredentials(SaslExtensions.class).iterator().next();
                    ((SaslExtensionsCallback) callback).extensions(extensions);
                }
            } else {
                throw new UnsupportedCallbackException(callback, "Unrecognized SASL ClientCallback");
            }
        }
    }

    @Override
    @Substitute
    public void close() {
    }
}
