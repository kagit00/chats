package com.flairbit.chats.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flairbit.chats.client.ServiceAuthClient;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import java.nio.charset.StandardCharsets;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor serviceAuthInterceptor(ServiceAuthClient authClient) {
        return template -> {
            String token = authClient.createToken("FlairBit");
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        };
    }

    @Bean
    public Decoder feignDecoder(ObjectMapper mapper) {
        return (response, type) -> {
            String body = StreamUtils.copyToString(response.body().asInputStream(), StandardCharsets.UTF_8);
            return mapper.readValue(body, mapper.constructType(type));
        };
    }
}