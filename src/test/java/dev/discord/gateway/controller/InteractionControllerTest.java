package dev.discord.gateway.controller;

import dev.discord.gateway.service.EventForwardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InteractionController.class)
class InteractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventForwardingService eventForwardingService;

    @Test
    void pingReturnsPong() throws Exception {
        mockMvc.perform(post("/api/discord/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"type\":1}"));

        verifyNoInteractions(eventForwardingService);
    }

    @Test
    void applicationCommandForwardsToRedisAndReturnsDeferredResponse() throws Exception {
        String payload = "{\"type\":2,\"id\":\"cmd-123\"}";

        mockMvc.perform(post("/api/discord/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"type\":5}"));

        verify(eventForwardingService).forward(payload);
    }

    @Test
    void messageComponentForwardsToRedisAndReturnsDeferredResponse() throws Exception {
        String payload = "{\"type\":3,\"id\":\"comp-456\"}";

        mockMvc.perform(post("/api/discord/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"type\":5}"));

        verify(eventForwardingService).forward(payload);
    }
}
