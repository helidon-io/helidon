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
package io.helidon.integrations.narayana.jta.cdi;

import java.util.Collection;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Singleton;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.UserTransaction;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

/**
 * A <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi">CDI 2.0
 * portable extension</a> that adapts the <a
 * href="https://narayana.io/">Narayana transaction engine</a> to a <a
 * href="http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#part_2">CDI
 * 2.0 SE environment</a>.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
 */
public final class NarayanaExtension implements Extension {


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link NarayanaExtension}.
   */
  public NarayanaExtension() {
    super();
  }


  /*
   * Instance methods.
   */


  /**
   * Adds a synthetic bean that creates a {@link Transaction} in
   * {@linkplain TransactionScoped transaction scope}.
   *
   * @param event the {@link AfterBeanDiscovery} event fired by the
   * CDI container; may be {@code null} in which case no action will
   * be taken
   */
  private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
    if (event != null) {

      // Weld registers a UserTransaction bean well before this
      // observer method fires.  OpenWebBeans does not.  We need to
      // handle both cases since this is not part of the CDI
      // specification.
      Collection<? extends Bean<?>> beans = beanManager.getBeans(UserTransaction.class);
      if (beans == null || beans.isEmpty()) {
        event.addBean()
          .types(UserTransaction.class)
          // OpenWebBeans does not add these qualifiers; Weld does
          // automatically:
          .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE)
          // see
          // e.g. https://docs.oracle.com/javaee/6/tutorial/doc/gmgli.html
          // which reads in part: "Predefined beans are injected with
          // **dependent scope** [emphasis mine] and the predefined
          // default qualifier @Default."  This scope restriction is
          // not specified in the CDI specification but seems
          // reasonable and widely expected.
          .scope(Dependent.class)
          .createWith(cc -> com.arjuna.ats.jta.UserTransaction.userTransaction());
      }

      event.addBean()
        .id(Transaction.class.getName())
        .types(Transaction.class)
        .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE) // OpenWebBeans does not add these
        .scope(TransactionScoped.class)
        .createWith(cc -> {
            try {
              return CDI.current().select(TransactionManager.class).get().getTransaction();
            } catch (final SystemException systemException) {
              throw new CreationException(systemException.getMessage(), systemException);
            }
          });


      beans = beanManager.getBeans(JTAEnvironmentBean.class);
      if (beans == null || beans.isEmpty()) {
        event.addBean()
          .addTransitiveTypeClosure(JTAEnvironmentBean.class)
          // OpenWebBeans does not add these qualifiers; Weld does
          // automatically:
          .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE)
          .scope(Singleton.class)
          .createWith(cc -> BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class));
      }

    }
  }

}
