package org.apache.nifi.idbroker.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.apache.nifi.processor.exception.ProcessException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public abstract class AbstractCachingIDBrokerService<R> {
    private static final long CACHE_RENEW_TIME_THRESHOLD_MS = 15 * 60 * 1000;

    private final HttpClient httpClient;

    private final HashMap<CloudProviderHandler<?, ?>, R> cache = new HashMap<>();

    protected AbstractCachingIDBrokerService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    protected abstract String getUrl(CloudProviderHandler<?, ?> cloudProvider);

    protected abstract HttpResponse getResource(String url);

    protected abstract R mapContent(InputStream content, CloudProviderHandler<?, ?> cloudProvider) throws IOException, JsonParseException, JsonMappingException;

    protected abstract <I, C> boolean expired(CloudProviderHandler<I, C> cloudProvider, R resource);

    public R getCachedResource(CloudProviderHandler<?, ?> cloudProvider) {
        R resource = cache.computeIfAbsent(cloudProvider, _cloudProvider -> getResource(_cloudProvider));

        if (expired(cloudProvider, resource)) {
            cache.remove(cloudProvider);
            resource = cache.computeIfAbsent(cloudProvider, _cloudProvider -> getResource(_cloudProvider));
        }

        return resource;
    }

    protected R getResource(CloudProviderHandler<?, ?> cloudProvider) {
        String url = getUrl(cloudProvider);
        HttpResponse response = getResource(url);

        try (InputStream content = response.getEntity().getContent()) {
            try {
                R mappedContent = mapContent(content, cloudProvider);

                return mappedContent;
            } catch (JsonParseException | JsonMappingException e) {
                HttpResponse errorHttpResponse = getResource(url);

                String errorResponse = IOUtils.toString(errorHttpResponse.getEntity().getContent(), StandardCharsets.UTF_8);

                throw new ProcessException("Didn't get valid response from IDBroker via '" + url + "', response was:\n" + errorResponse, e);
            }
        } catch (IOException e) {
            throw new RetryableCommunicationException("Couldn't get response from IDBroker via '" + url + "'", e);
        }
    }

    protected <T> T mapContent(InputStream content, Class<T> type, PropertyNamingStrategy propertyNamingStrategy) throws IOException, JsonParseException, JsonMappingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(propertyNamingStrategy);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        T contentAsObject = objectMapper.readValue(content, type);

        return contentAsObject;
    }

    protected HttpResponse executeGetRequest(String url, Header... headers) {
        try {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeaders(headers);

            HttpResponse response = httpClient.execute(httpGet);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                String errorResponse = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                throw new RetryableCommunicationException("Request to '" + url + "' returned status code '" + statusCode + "', response was:\n" + errorResponse);
            } else if (statusCode != HttpServletResponse.SC_OK) {
                String errorResponse = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                throw new ProcessException("Request to '" + url + "' returned status code '" + statusCode + "', response was:\n" + errorResponse);
            }

            return response;
        } catch (IOException e) {
            throw new RetryableCommunicationException("Got exception while sending request to url '" + url + "'", e);
        }
    }

    protected boolean expired(long expirationTimestamp) {
        boolean expired = expirationTimestamp - System.currentTimeMillis() < CACHE_RENEW_TIME_THRESHOLD_MS;

        return expired;
    }
}
