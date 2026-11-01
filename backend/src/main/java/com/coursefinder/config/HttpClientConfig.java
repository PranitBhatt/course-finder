package com.coursefinder.config;

import com.coursefinder.config.properties.CourseraProperties;
import com.coursefinder.config.properties.YouTubeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * One {@link RestClient} per platform, each with its own base URL and timeouts.
 */
@Configuration
public class HttpClientConfig {

    public static final String COURSERA_CLIENT = "courseraRestClient";
    public static final String YOUTUBE_CLIENT = "youTubeRestClient";

    @Bean
    @Qualifier(COURSERA_CLIENT)
    public RestClient courseraRestClient(RestClient.Builder builder, CourseraProperties properties) {
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .build();
    }

    @Bean
    @Qualifier(YOUTUBE_CLIENT)
    public RestClient youTubeRestClient(RestClient.Builder builder, YouTubeProperties properties) {
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
