package com.pdp.persistence.provider;

import java.util.Set;

/**
 * 持久化提供方扩展契约。
 */
public interface PersistenceProvider {

    String providerKey();

    DatabaseProduct databaseProduct();

    DatabaseCapabilityProfile certifiedBaseline();

    Set<DatabaseSwitchCapability> switchCapabilities();

    DatabaseDialect dialect();

    default boolean supports(DatabaseDeploymentProfile deployment) {
        return deployment.status() == DatabaseDeploymentProfile.DeploymentStatus.CERTIFIED
                && deployment.capabilityProfile().databaseProduct().equals(databaseProduct())
                && deployment.capabilityProfile().databaseVersion()
                        .compareTo(certifiedBaseline().databaseVersion()) >= 0;
    }
}
