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
package io.helidon.integrations.cdi.jpa;

import java.util.Calendar;
import java.util.Date;

import javax.persistence.FlushModeType;
import javax.persistence.Parameter;
import javax.persistence.ParameterMode;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TemporalType;

abstract class DelegatingStoredProcedureQuery extends DelegatingQuery implements StoredProcedureQuery {

    private final StoredProcedureQuery delegate;

    DelegatingStoredProcedureQuery(final StoredProcedureQuery delegate) {
        super(delegate);
        this.delegate = delegate;
    }


    @Override
    public Object getOutputParameterValue(final int position) {
        return this.delegate.getOutputParameterValue(position);
    }

    @Override
    public Object getOutputParameterValue(final String parameterName) {
        return this.delegate.getOutputParameterValue(parameterName);
    }


    @Override
    public boolean hasMoreResults() {
        return this.delegate.hasMoreResults();
    }


    @Override
    public int getUpdateCount() {
        return this.delegate.getUpdateCount();
    }


    @Override
    @SuppressWarnings("rawtypes")
    public DelegatingStoredProcedureQuery registerStoredProcedureParameter(final int position,
                                                                           final Class type,
                                                                           final ParameterMode mode) {
        this.delegate.registerStoredProcedureParameter(position, type, mode);
        return this;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public DelegatingStoredProcedureQuery registerStoredProcedureParameter(final String parameterName,
                                                                           final Class type,
                                                                           final ParameterMode mode) {
        this.delegate.registerStoredProcedureParameter(parameterName, type, mode);
        return this;
    }


    @Override
    public DelegatingStoredProcedureQuery setFlushMode(FlushModeType flushMode) {
        return (DelegatingStoredProcedureQuery) super.setFlushMode(flushMode);
    }


    @Override
    public DelegatingStoredProcedureQuery setHint(final String hintName,
                                                  final Object value) {
        return (DelegatingStoredProcedureQuery) super.setHint(hintName, value);
    }


    @Override
    public <T> DelegatingStoredProcedureQuery setParameter(final Parameter<T> parameter,
                                                           final T value) {
        return (DelegatingStoredProcedureQuery) super.setParameter(parameter, value);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(final Parameter<Calendar> parameter,
                                                       final Calendar value,
                                                       final TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(parameter, value, temporalType);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(final Parameter<Date> parameter,
                                                       final Date value,
                                                       final TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(parameter, value, temporalType);
    }


    @Override
    public DelegatingStoredProcedureQuery setParameter(final int position,
                                                       final Object value) {
        return (DelegatingStoredProcedureQuery) super.setParameter(position, value);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(final int position,
                                                       final Calendar value,
                                                       final TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(position, value, temporalType);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(final int position,
                                                       final Date value,
                                                       final TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(position, value, temporalType);
    }


    @Override
    public DelegatingStoredProcedureQuery setParameter(final String name,
                                                       final Object value) {
        return (DelegatingStoredProcedureQuery) super.setParameter(name, value);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(final String name,
                                                       final Calendar value,
                                                       final TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(name, value, temporalType);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(final String name,
                                                       final Date value,
                                                       final TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(name, value, temporalType);
    }


    @Override
    public boolean execute() {
        return this.delegate.execute();
    }


}
