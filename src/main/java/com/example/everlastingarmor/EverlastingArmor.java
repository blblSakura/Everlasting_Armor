package com.example.everlastingarmor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EverlastingArmor.MODID)
public class EverlastingArmor {
    public static final String MODID = "everlastingarmor";

    public EverlastingArmor(IEventBus modEventBus) {
        ModDataComponents.register(modEventBus);
    }
}