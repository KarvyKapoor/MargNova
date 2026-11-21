
package com.saferoute.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HttpConfig {

    @Value("${saferoute.connect-timeout-ms:3000}")
    private long connectTimeoutMs;

    @Value("${saferoute.read-timeout-ms:8000}")
    private long readTimeoutMs;

    @Bean
    public HttpClient sharedHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Bean
    public RestClient restClient(
            RestClient.Builder builder,
            HttpClient httpClient) {

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofMillis(readTimeoutMs));

        return builder.requestFactory(requestFactory).build();
    }

    @Bean
    public WebClient webClient(
            WebClient.Builder builder,
            HttpClient httpClient) {

        return builder
                .clientConnector(
                        new JdkClientHttpConnector(httpClient))
                .build();
    }
}
