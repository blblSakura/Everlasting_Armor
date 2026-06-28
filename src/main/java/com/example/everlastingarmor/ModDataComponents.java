package com.example.everlastingarmor;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, EverlastingArmor.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BROKEN =
            DATA_COMPONENTS.register("broken",
                    () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build());

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}