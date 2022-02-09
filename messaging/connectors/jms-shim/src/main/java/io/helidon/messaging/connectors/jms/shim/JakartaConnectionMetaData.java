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
 */
package io.helidon.messaging.connectors.jms.shim;

import java.util.Enumeration;

import jakarta.jms.ConnectionMetaData;
import jakarta.jms.JMSException;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaConnectionMetaData implements ConnectionMetaData {
    private final javax.jms.ConnectionMetaData delegate;

    JakartaConnectionMetaData(javax.jms.ConnectionMetaData delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getJMSVersion() throws JMSException {
        return call(delegate::getJMSVersion);
    }

    @Override
    public int getJMSMajorVersion() throws JMSException {
        return call(delegate::getJMSMajorVersion);
    }

    @Override
    public int getJMSMinorVersion() throws JMSException {
        return call(delegate::getJMSMinorVersion);
    }

    @Override
    public String getJMSProviderName() throws JMSException {
        return call(delegate::getJMSProviderName);
    }

    @Override
    public String getProviderVersion() throws JMSException {
        return call(delegate::getProviderVersion);
    }

    @Override
    public int getProviderMajorVersion() throws JMSException {
        return call(delegate::getProviderMajorVersion);
    }

    @Override
    public int getProviderMinorVersion() throws JMSException {
        return call(delegate::getProviderMinorVersion);
    }

    @Override
    public Enumeration getJMSXPropertyNames() throws JMSException {
        return call(delegate::getJMSXPropertyNames);
    }
}
