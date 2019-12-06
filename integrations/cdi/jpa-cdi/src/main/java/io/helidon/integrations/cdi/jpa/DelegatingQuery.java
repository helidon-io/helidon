/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Parameter;
import javax.persistence.Query;
import javax.persistence.TemporalType;

abstract class DelegatingQuery implements Query {

    private final Query delegate;

    DelegatingQuery(final Query delegate) {
        super();
        this.delegate = Objects.requireNonNull(delegate);
    }


    @Override
    @SuppressWarnings("rawtypes")
    public List getResultList() {
        return this.delegate.getResultList();
    }


    @Override
    public Object getSingleResult() {
        return this.delegate.getSingleResult();
    }


    @Override
    public int getMaxResults() {
        return this.delegate.getMaxResults();
    }

    @Override
    public Query setMaxResults(final int maxResults) {
        this.delegate.setMaxResults(maxResults);
        return this;
    }


    @Override
    public int getFirstResult() {
        return this.delegate.getFirstResult();
    }

    @Override
    public Query setFirstResult(final int firstResult) {
        this.delegate.setFirstResult(firstResult);
        return this;
    }


    @Override
    public Map<String, Object> getHints() {
        return this.delegate.getHints();
    }


    @Override
    public Query setHint(final String hintName, final Object value) {
        this.delegate.setHint(hintName, value);
        return this;
    }


    @Override
    public Parameter<?> getParameter(final int position) {
        return this.delegate.getParameter(position);
    }

    @Override
    public <T> Parameter<T> getParameter(final int position, final Class<T> type) {
        return this.delegate.getParameter(position, type);
    }

    @Override
    public Parameter<?> getParameter(final String name) {
        return this.delegate.getParameter(name);
    }

    @Override
    public <T> Parameter<T> getParameter(final String name, final Class<T> type) {
        return this.delegate.getParameter(name, type);
    }


    @Override
    public Set<Parameter<?>> getParameters() {
        return this.delegate.getParameters();
    }


    @Override
    public <T> T getParameterValue(final Parameter<T> parameter) {
        return this.delegate.getParameterValue(parameter);
    }

    @Override
    public Object getParameterValue(final int position) {
        return this.delegate.getParameterValue(position);
    }

    @Override
    public Object getParameterValue(final String name) {
        return this.delegate.getParameterValue(name);
    }


    @Override
    public boolean isBound(final Parameter<?> parameter) {
        return this.delegate.isBound(parameter);
    }


    @Override
    public <T> Query setParameter(final Parameter<T> parameter, final T value) {
        this.delegate.setParameter(parameter, value);
        return this;
    }

    @Override
    public Query setParameter(final Parameter<Calendar> parameter, final Calendar value, final TemporalType temporalType) {
        this.delegate.setParameter(parameter, value, temporalType);
        return this;
    }

    @Override
    public Query setParameter(final Parameter<Date> parameter, final Date value, final TemporalType temporalType) {
        this.delegate.setParameter(parameter, value, temporalType);
        return this;
    }


    @Override
    public Query setParameter(final int position, final Calendar value, final TemporalType temporalType) {
        this.delegate.setParameter(position, value, temporalType);
        return this;
    }

    @Override
    public Query setParameter(final String name, final Calendar value, final TemporalType temporalType) {
        this.delegate.setParameter(name, value, temporalType);
        return this;
    }


    @Override
    public Query setParameter(final int position, final Date value, final TemporalType temporalType) {
        this.delegate.setParameter(position, value, temporalType);
        return this;
    }

    @Override
    public Query setParameter(final String name, final Date value, final TemporalType temporalType) {
        this.delegate.setParameter(name, value, temporalType);
        return this;
    }


    @Override
    public Query setParameter(final int position, final Object value) {
        this.delegate.setParameter(position, value);
        return this;
    }

    @Override
    public Query setParameter(final String name, final Object value) {
        this.delegate.setParameter(name, value);
        return this;
    }


    @Override
    public FlushModeType getFlushMode() {
        return this.delegate.getFlushMode();
    }

    @Override
    public Query setFlushMode(final FlushModeType flushMode) {
        this.delegate.setFlushMode(flushMode);
        return this;
    }


    @Override
    public LockModeType getLockMode() {
        return this.delegate.getLockMode();
    }

    @Override
    public Query setLockMode(final LockModeType lockMode) {
        this.delegate.setLockMode(lockMode);
        return this;
    }


    @Override
    public int executeUpdate() {
        return this.delegate.executeUpdate();
    }


    @Override
    public <T> T unwrap(final Class<T> cls) {
        return this.delegate.unwrap(cls);
    }


}
