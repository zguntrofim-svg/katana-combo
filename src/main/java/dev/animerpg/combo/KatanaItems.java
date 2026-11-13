package dev.animerpg.combo;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
public final class KatanaItems {
    public static final Item KUROTSUKI=Registry.register(Registries.ITEM,Identifier.of("katana_combo","kurotsuki"),
        new SwordItem(ToolMaterials.NETHERITE,new Item.Settings().fireproof().rarity(Rarity.EPIC)
            .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE,3,-2.4f))));
    public static void init(){ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries->entries.add(KUROTSUKI));}
    private KatanaItems(){}
}
