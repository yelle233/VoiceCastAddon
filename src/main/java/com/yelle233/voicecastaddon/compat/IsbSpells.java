package com.yelle233.voicecastaddon.compat;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

public final class IsbSpells {

    private IsbSpells() {
    }

    private static IForgeRegistry<AbstractSpell> registry() {
        return SpellRegistry.REGISTRY.get();
    }

    public static List<ResourceLocation> allSpellIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        IForgeRegistry<AbstractSpell> reg = registry();
        for (AbstractSpell spell : reg) {
            ResourceLocation id = reg.getKey(spell);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static ResourceLocation idOf(AbstractSpell spell) {
        if (spell == null) {
            return null;
        }
        return registry().getKey(spell);
    }

    public static AbstractSpell spellOf(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return registry().getValue(id);
    }
}
