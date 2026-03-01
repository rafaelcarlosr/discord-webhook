package dev.discord.gateway.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.discord.gateway.service.EventForwardingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discord/interactions")
public class InteractionController {

    private static final int PING = 1;
    private static final String PONG_RESPONSE = """
            {"type":1}""";
    /** Type 5 = DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE — tells Discord we will follow up later. */
    private static final String DEFERRED_RESPONSE = """
            {"type":5}""";

    private final ObjectMapper objectMapper;
    private final EventForwardingService eventForwardingService;

    public InteractionController(ObjectMapper objectMapper,
                                 EventForwardingService eventForwardingService) {
        this.objectMapper = objectMapper;
        this.eventForwardingService = eventForwardingService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleInteraction(@RequestBody String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        int type = node.path("type").asInt();

        if (type == PING) {
            return ResponseEntity.ok(PONG_RESPONSE);
        }

        eventForwardingService.forward(payload);
        return ResponseEntity.ok(DEFERRED_RESPONSE);
    }
}
