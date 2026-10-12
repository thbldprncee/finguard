package com.finguard.gateway.sse;

import com.finguard.gateway.config.GatewayProperties;
import com.finguard.gateway.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sse")
public class SseController {

    /** Fields a USER may see in live events about their own transactions. */
    private static final List<String> USER_FIELDS = List.of(
            "transactionId", "eventType", "decision", "amount", "currency", "occurredAt");

    public record TopicEvent(String topic, Map<String, Object> payload) {}

    /**
     * directBestEffort: no replay buffer, a slow subscriber only drops its own events, and the
     * sink is not terminated when the last subscriber leaves. multicast().onBackpressureBuffer()
     * auto-cancels then, after which every new client got an empty, completed stream.
     */
    private final Sinks.Many<TopicEvent> sink = Sinks.many().multicast().directBestEffort();
    private final GatewayProperties properties;

    public SseController(GatewayProperties properties) {
        this.properties = properties;
    }

    public void publishEvent(String topic, Map<String, Object> payload) {
        sink.tryEmitNext(new TopicEvent(topic, payload));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamEvents(@AuthenticationPrincipal AuthenticatedUser user) {
        Flux<ServerSentEvent<Map<String, Object>>> events = sink.asFlux()
                .filter(event -> isVisibleTo(event, user))
                .map(event -> ServerSentEvent.<Map<String, Object>>builder()
                        .event(event.topic())
                        .data(user.isStaff() ? event.payload() : redact(event.payload()))
                        .build());

        Flux<ServerSentEvent<Map<String, Object>>> heartbeat = Flux.interval(properties.sseHeartbeat())
                .map(i -> ServerSentEvent.<Map<String, Object>>builder().comment("keep-alive").build());

        // Sent immediately so the response is committed and the client sees the connection as open.
        var connected = ServerSentEvent.<Map<String, Object>>builder().comment("connected").build();

        return Flux.merge(Flux.just(connected), events, heartbeat)
                .doOnSubscribe(sub -> log.info("SSE client connected: user={}", user.userId()))
                .doOnCancel(() -> log.info("SSE client disconnected: user={}", user.userId()));
    }

    /** Staff see every event; a USER only events about their own transactions. */
    static boolean isVisibleTo(TopicEvent event, AuthenticatedUser user) {
        return user.isStaff() || user.userId().toString().equals(String.valueOf(event.payload().get("userId")));
    }

    static Map<String, Object> redact(Map<String, Object> payload) {
        Map<String, Object> visible = new LinkedHashMap<>();
        USER_FIELDS.forEach(field -> {
            if (payload.containsKey(field)) {
                visible.put(field, payload.get(field));
            }
        });
        return visible;
    }
}
