/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import jakarta.persistence.TemporalType;

class DelegatingQuery implements Query {

    private final Query delegate;

    DelegatingQuery(Query delegate) {
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
    public Query setMaxResults(int maxResults) {
        this.delegate.setMaxResults(maxResults);
        return this;
    }


    @Override
    public int getFirstResult() {
        return this.delegate.getFirstResult();
    }

    @Override
    public Query setFirstResult(int firstResult) {
        this.delegate.setFirstResult(firstResult);
        return this;
    }


    @Override
    public Map<String, Object> getHints() {
        return this.delegate.getHints();
    }


    @Override
    public Query setHint(String hintName, Object value) {
        this.delegate.setHint(hintName, value);
        return this;
    }


    @Override
    public Parameter<?> getParameter(int position) {
        return this.delegate.getParameter(position);
    }

    @Override
    public <T> Parameter<T> getParameter(int position, Class<T> type) {
        return this.delegate.getParameter(position, type);
    }

    @Override
    public Parameter<?> getParameter(String name) {
        return this.delegate.getParameter(name);
    }

    @Override
    public <T> Parameter<T> getParameter(String name, Class<T> type) {
        return this.delegate.getParameter(name, type);
    }


    @Override
    public Set<Parameter<?>> getParameters() {
        return this.delegate.getParameters();
    }


    @Override
    public <T> T getParameterValue(Parameter<T> parameter) {
        return this.delegate.getParameterValue(parameter);
    }

    @Override
    public Object getParameterValue(int position) {
        return this.delegate.getParameterValue(position);
    }

    @Override
    public Object getParameterValue(String name) {
        return this.delegate.getParameterValue(name);
    }


    @Override
    public boolean isBound(Parameter<?> parameter) {
        return this.delegate.isBound(parameter);
    }


    @Override
    public <T> Query setParameter(Parameter<T> parameter, T value) {
        this.delegate.setParameter(parameter, value);
        return this;
    }

    @Override
    public Query setParameter(Parameter<Calendar> parameter, Calendar value, TemporalType temporalType) {
        this.delegate.setParameter(parameter, value, temporalType);
        return this;
    }

    @Override
    public Query setParameter(Parameter<Date> parameter, Date value, TemporalType temporalType) {
        this.delegate.setParameter(parameter, value, temporalType);
        return this;
    }


    @Override
    public Query setParameter(int position, Calendar value, TemporalType temporalType) {
        this.delegate.setParameter(position, value, temporalType);
        return this;
    }

    @Override
    public Query setParameter(String name, Calendar value, TemporalType temporalType) {
        this.delegate.setParameter(name, value, temporalType);
        return this;
    }


    @Override
    public Query setParameter(int position, Date value, TemporalType temporalType) {
        this.delegate.setParameter(position, value, temporalType);
        return this;
    }

    @Override
    public Query setParameter(String name, Date value, TemporalType temporalType) {
        this.delegate.setParameter(name, value, temporalType);
        return this;
    }


    @Override
    public Query setParameter(int position, Object value) {
        this.delegate.setParameter(position, value);
        return this;
    }

    @Override
    public Query setParameter(String name, Object value) {
        this.delegate.setParameter(name, value);
        return this;
    }


    @Override
    public FlushModeType getFlushMode() {
        return this.delegate.getFlushMode();
    }

    @Override
    public Query setFlushMode(FlushModeType flushMode) {
        this.delegate.setFlushMode(flushMode);
        return this;
    }


    @Override
    public LockModeType getLockMode() {
        return this.delegate.getLockMode();
    }

    @Override
    public Query setLockMode(LockModeType lockMode) {
        this.delegate.setLockMode(lockMode);
        return this;
    }


    @Override
    public int executeUpdate() {
        return this.delegate.executeUpdate();
    }


    @Override
    public <T> T unwrap(Class<T> cls) {
        if (cls != null && cls.isInstance(this)) {
            return cls.cast(this);
        }
        return this.delegate.unwrap(cls);
    }


}
