package dev.discord.wsgw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level Discord Gateway payload. All opcodes share this envelope.
 *
 * @param op   opcode
 * @param data event data (varies by opcode; deserialized as a raw Object / Map)
 * @param seq  sequence number, only present for DISPATCH (op 0)
 * @param type event name, only present for DISPATCH (op 0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayPayload(
        @JsonProperty("op") int op,
        @JsonProperty("d") Object data,
        @JsonProperty("s") Integer seq,
        @JsonProperty("t") String type) {}
