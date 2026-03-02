package dev.discord.wsgw.model;

public enum Opcode {
    DISPATCH(0),
    HEARTBEAT(1),
    IDENTIFY(2),
    PRESENCE_UPDATE(3),
    VOICE_STATE_UPDATE(4),
    // op 5 is unused
    RESUME(6),
    RECONNECT(7),
    REQUEST_GUILD_MEMBERS(8),
    INVALID_SESSION(9),
    HELLO(10),
    HEARTBEAT_ACK(11);

    public final int value;

    Opcode(int value) {
        this.value = value;
    }

    public static Opcode of(int value) {
        for (Opcode op : values()) {
            if (op.value == value) return op;
        }
        throw new IllegalArgumentException("Unknown opcode: " + value);
    }
}
