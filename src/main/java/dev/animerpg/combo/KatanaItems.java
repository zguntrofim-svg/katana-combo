package dev.animerpg.combo;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.text.Text;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
public final class KatanaItems {
    public static final Item KUROTSUKI=Registry.register(Registries.ITEM,Identifier.of("katana_combo","kurotsuki"),
        new SwordItem(ToolMaterials.NETHERITE,new Item.Settings().fireproof().rarity(Rarity.EPIC)
            .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE,3,-2.4f))));
    public static void init(){
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries->entries.add(KUROTSUKI));
        Registry.register(Registries.ITEM_GROUP,Identifier.of("katana_combo","katanas"),FabricItemGroup.builder()
            .icon(()->new ItemStack(KUROTSUKI)).displayName(Text.translatable("itemGroup.katana_combo.katanas"))
            .entries((context,entries)->{entries.add(KUROTSUKI);entries.add(RaizenBoss.AMAKIRI);for(Item item:StormItems.ARMOR)entries.add(item);}).build());
    }
    private KatanaItems(){}
}
