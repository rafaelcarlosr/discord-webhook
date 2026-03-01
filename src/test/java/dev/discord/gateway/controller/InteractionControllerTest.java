package dev.discord.gateway.controller;

import dev.discord.gateway.service.EventForwardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InteractionControllerTest {

    @Mock
    private EventForwardingService eventForwardingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InteractionController controller =
                new InteractionController(new ObjectMapper(), eventForwardingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

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
