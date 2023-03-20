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

import jakarta.persistence.FlushModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import jakarta.persistence.TemporalType;

class DelegatingStoredProcedureQuery extends DelegatingQuery implements StoredProcedureQuery {

    private final StoredProcedureQuery delegate;

    DelegatingStoredProcedureQuery(StoredProcedureQuery delegate) {
        super(delegate);
        this.delegate = delegate;
    }


    @Override
    public Object getOutputParameterValue(int position) {
        return this.delegate.getOutputParameterValue(position);
    }

    @Override
    public Object getOutputParameterValue(String parameterName) {
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
    public DelegatingStoredProcedureQuery registerStoredProcedureParameter(int position,
                                                                           Class type,
                                                                           ParameterMode mode) {
        this.delegate.registerStoredProcedureParameter(position, type, mode);
        return this;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public DelegatingStoredProcedureQuery registerStoredProcedureParameter(String parameterName,
                                                                           Class type,
                                                                           ParameterMode mode) {
        this.delegate.registerStoredProcedureParameter(parameterName, type, mode);
        return this;
    }


    @Override
    public DelegatingStoredProcedureQuery setFlushMode(FlushModeType flushMode) {
        return (DelegatingStoredProcedureQuery) super.setFlushMode(flushMode);
    }


    @Override
    public DelegatingStoredProcedureQuery setHint(String hintName,
                                                  Object value) {
        return (DelegatingStoredProcedureQuery) super.setHint(hintName, value);
    }


    @Override
    public <T> DelegatingStoredProcedureQuery setParameter(Parameter<T> parameter,
                                                           T value) {
        return (DelegatingStoredProcedureQuery) super.setParameter(parameter, value);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(Parameter<Calendar> parameter,
                                                       Calendar value,
                                                       TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(parameter, value, temporalType);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(Parameter<Date> parameter,
                                                       Date value,
                                                       TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(parameter, value, temporalType);
    }


    @Override
    public DelegatingStoredProcedureQuery setParameter(int position,
                                                       Object value) {
        return (DelegatingStoredProcedureQuery) super.setParameter(position, value);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(int position,
                                                       Calendar value,
                                                       TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(position, value, temporalType);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(int position,
                                                       Date value,
                                                       TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(position, value, temporalType);
    }


    @Override
    public DelegatingStoredProcedureQuery setParameter(String name,
                                                       Object value) {
        return (DelegatingStoredProcedureQuery) super.setParameter(name, value);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(String name,
                                                       Calendar value,
                                                       TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(name, value, temporalType);
    }

    @Override
    public DelegatingStoredProcedureQuery setParameter(String name,
                                                       Date value,
                                                       TemporalType temporalType) {
        return (DelegatingStoredProcedureQuery) super.setParameter(name, value, temporalType);
    }


    @Override
    public boolean execute() {
        return this.delegate.execute();
    }


}
