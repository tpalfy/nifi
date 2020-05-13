package org.apache.nifi.idbroker.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.security.krb.KerberosAction;
import org.apache.nifi.security.krb.KerberosPasswordUser;
import org.apache.nifi.security.krb.KerberosUser;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedExceptionAction;
import java.util.StringJoiner;

import static org.apache.nifi.idbroker.service.ConfigService.IDBROKER_TOKEN_ENDPOINT;

public class TokenService extends AbstractCachingIDBrokerService<IDBrokerToken> {
    private final ConfigService configService;
    private final ComponentLog componentLog;

    private final KerberosUser kerberosUser;

    public TokenService(HttpClient httpClient, String userName, String password, ConfigService configService, ComponentLog componentLog) throws LoginException {
        super(httpClient);
        this.configService = configService;
        this.componentLog = componentLog;

        this.kerberosUser = createAndLoginKerberosUser(userName, password);
    }

    @Override
    protected String getUrl(CloudProviderHandler<?, ?> cloudProvider) {
        // https://HOST:8444/gateway/dt/knoxtoken/api/v1/token
        // (The result looks provider-agnostic)

        StringJoiner urlBuilder = new StringJoiner("/")
            .add(configService.getRootAddress(cloudProvider))
            .add(configService.getDtPath(cloudProvider))
            .add(IDBROKER_TOKEN_ENDPOINT);

        return urlBuilder.toString();
    }

    @Override
    protected HttpResponse requestResource(String url) {
        HttpResponse idBrokerTokenResponse = runKerberized(() -> executeGetRequest(url));

        return idBrokerTokenResponse;
    }

    @Override
    protected IDBrokerToken mapContent(InputStream content, CloudProviderHandler<?, ?> cloudProvider) throws IOException, JsonParseException, JsonMappingException {
        return mapContent(content, IDBrokerToken.class, PropertyNamingStrategy.SNAKE_CASE);
    }

    @Override
    protected <I, C> boolean expired(CloudProviderHandler<I, C> cloudProvider, IDBrokerToken resource) {
        return expired(resource.getExpiresIn());
    }

    protected KerberosUser createAndLoginKerberosUser(String userName, String password) throws LoginException {
        KerberosUser kerberosUser = new KerberosPasswordUser(userName, password);
        kerberosUser.login();

        return kerberosUser;
    }

    protected <A> A runKerberized(PrivilegedExceptionAction<A> privilegedAction) {
        return new KerberosAction<>(kerberosUser, privilegedAction, componentLog)
            .execute();
    }
}
