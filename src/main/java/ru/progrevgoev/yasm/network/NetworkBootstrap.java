package ru.progrevgoev.yasm.network;

import net.neoforged.bus.api.IEventBus;

/** Register CustomPacketPayload types and handlers here. Keep all gameplay validation server-side. */
public final class NetworkBootstrap {
    private NetworkBootstrap() {}

    public static void register(IEventBus modEventBus) {
        // TODO: register payload codecs and handlers using RegisterPayloadHandlersEvent.
    }
}
