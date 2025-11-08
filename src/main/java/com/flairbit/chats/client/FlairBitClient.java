package com.flairbit.chats.client;

import com.flairbit.chats.dto.ProfileChatDto;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@FeignClient(name = "flairbit", url = "${flairbit.base-url:http://flairbit:8081}")
@CircuitBreaker(name = "flairbit")
public interface FlairBitClient {

    @GetMapping("/internal/chat-service/users/{email}/profile/{intent}")
    ProfileChatDto getProfileByUserEmail(@PathVariable String email, @PathVariable String intent);
}