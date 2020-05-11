package org.apache.nifi.idbroker.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.apache.nifi.processor.exception.ProcessException;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.StringJoiner;

import static org.apache.nifi.idbroker.service.ConfigService.IDBROKER_TOKEN_ENDPOINT;

public class TokenService extends AbstractCachingIDBrokerService<IDBrokerToken> {
    private final String userName;
    private final String password;
    private final ConfigService configService;

    public TokenService(HttpClient httpClient, String userName, String password, ConfigService configService) {
        super(httpClient);
        this.userName = userName;
        this.password = password;
        this.configService = configService;
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

    protected <A> A runKerberized(PrivilegedAction<A> privilegedAction) {
        try {
            Subject subject = new Subject();

            CallbackHandler callbackHandler = callbacks -> {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(userName);
                    }
                    if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(password.toCharArray());
                    }
                }
            };

            Configuration loginConfig = new Configuration() {
                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                    return new AppConfigurationEntry[]{
                        new AppConfigurationEntry(
                            "com.sun.security.auth.module.Krb5LoginModule",
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            new HashMap<>()
                        )
                    };
                }
            };
            LoginContext loginContext = new LoginContext("", subject, callbackHandler, loginConfig);
            loginContext.login();

            Subject serviceSubject = loginContext.getSubject();

            return Subject.doAs(serviceSubject, privilegedAction);
        } catch (LoginException e) {
            throw new ProcessException("Kerberos authentication error for user '" + userName + "'", e);
        }
    }
}
