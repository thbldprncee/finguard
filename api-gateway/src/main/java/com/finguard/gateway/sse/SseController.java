package com.finguard.gateway.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sse")
public class SseController {

    private final Sinks.Many<Map<String, Object>> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    public void publishEvent(Map<String, Object> event) {
        sink.tryEmitNext(event);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamEvents() {
        return sink.asFlux()
                .map(event -> ServerSentEvent.<Map<String, Object>>builder()
                        .event((String) event.getOrDefault("eventType", "update"))
                        .data(event)
                        .build())
                .doOnSubscribe(sub -> log.info("SSE client connected"))
                .doOnCancel(() -> log.info("SSE client disconnected"));
    }
}
