package org.apache.nifi.idbroker.domain;

public interface CloudProviderHandler<IDBROKER_CLOUD_CREDENTIALS, NATIVE_CLOUD_CREDENTIALS> {
    String getFsName();

    Class<IDBROKER_CLOUD_CREDENTIALS> getIDBrokerCloudCredentialsType();

    long getExpirationTimestamp(IDBROKER_CLOUD_CREDENTIALS credentials);

    NATIVE_CLOUD_CREDENTIALS map(IDBROKER_CLOUD_CREDENTIALS idBrokerCloudCredentials);
}
