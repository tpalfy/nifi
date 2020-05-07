package org.apache.nifi.idbroker.domain;

import com.amazonaws.auth.AWSCredentials;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;

public class CloudProviders {
    public static CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> AWS = new CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials>() {
        @Override
        public String getFsName() {
            return "s3a";
        }

        @Override
        public Class<IDBrokerAWSCredentials> getIDBrokerCloudCredentialsType() {
            return IDBrokerAWSCredentials.class;
        }

        @Override
        public AWSCredentials map(IDBrokerAWSCredentials idBrokerCloudCredentials) {
            final String accessKeyId = idBrokerCloudCredentials.getCredentials().getAccessKeyId();
            final String secretAccessKey = idBrokerCloudCredentials.getCredentials().getSecretAccessKey();

            AWSCredentials awsCredentials = new AWSCredentials() {
                @Override
                public String getAWSAccessKeyId() {
                    return accessKeyId;
                }

                @Override
                public String getAWSSecretKey() {
                    return secretAccessKey;
                }
            };

            return awsCredentials;
        }

        @Override
        public long getExpirationTimestamp(IDBrokerAWSCredentials idBrokerAWSCredentials) {
            return idBrokerAWSCredentials.getCredentials().getExpiration();
        }
    };
}
