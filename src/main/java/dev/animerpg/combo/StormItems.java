package dev.animerpg.combo;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.*;

/** Player-sized equipment; the existing Amakiri item remains the weapon and combo entry point. */
public final class StormItems {
    public static final Item HELMET=armor("storm_shogun_helmet",ArmorItem.Type.HELMET);
    public static final Item CHESTPLATE=armor("storm_shogun_chestplate",ArmorItem.Type.CHESTPLATE);
    public static final Item LEGGINGS=armor("storm_shogun_leggings",ArmorItem.Type.LEGGINGS);
    public static final Item BOOTS=armor("storm_shogun_boots",ArmorItem.Type.BOOTS);
    public static final Item[] ARMOR={HELMET,CHESTPLATE,LEGGINGS,BOOTS};
    private static Item armor(String name,ArmorItem.Type type){
        return Registry.register(Registries.ITEM,Identifier.of("katana_combo",name),new ArmorItem(ArmorMaterials.NETHERITE,type,
            new Item.Settings().fireproof().rarity(Rarity.EPIC).maxDamage(type.getMaxDamage(37))));
    }
    public static void init(){ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries->{for(Item item:ARMOR)entries.add(item);});}
    private StormItems(){}
}
