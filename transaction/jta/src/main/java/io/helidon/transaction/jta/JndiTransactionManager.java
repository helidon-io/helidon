package io.helidon.transaction.jta;

import io.helidon.service.registry.Service;

@Service.Named("java:comp/TransactionManager")
@Service.Singleton
public class JndiTransactionManager {
}
