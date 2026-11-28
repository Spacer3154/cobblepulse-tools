package fr.cobblepulse.tools;

import fr.cobblepulse.tools.block.MovementPlateBlock;
import fr.cobblepulse.tools.block.StickPlateBlock;
import fr.cobblepulse.tools.wand.PokemonEditorWand;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Blocs, objets et onglet creatif. */
public final class ModContent {
    public static Block MOVEMENT_PLATE;
    public static Block STICK_PLATE;
    public static Item MOVEMENT_PLATE_ITEM;
    public static Item STICK_PLATE_ITEM;
    public static Item EDITOR_WAND;
    public static Block ELEVATOR;
    public static Item ELEVATOR_ITEM;

    private ModContent() {}

    private static BlockBehaviour.Properties plate(MapColor color) {
        // comme Pixelmon : pioche requise, ne se casse pas d'un coup
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.0f, 3.0f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    public static void register() {
        MOVEMENT_PLATE = Registry.register(BuiltInRegistries.BLOCK, CobblePulseTools.id("movement_plate"),
                new MovementPlateBlock(plate(MapColor.COLOR_LIGHT_GREEN)));
        // ralentit comme le sable des ames
        STICK_PLATE = Registry.register(BuiltInRegistries.BLOCK, CobblePulseTools.id("stick_plate"),
                new StickPlateBlock(plate(MapColor.COLOR_BLUE).speedFactor(0.4f)));
        MOVEMENT_PLATE_ITEM = Registry.register(BuiltInRegistries.ITEM, CobblePulseTools.id("movement_plate"),
                new BlockItem(MOVEMENT_PLATE, new Item.Properties()));
        STICK_PLATE_ITEM = Registry.register(BuiltInRegistries.ITEM, CobblePulseTools.id("stick_plate"),
                new BlockItem(STICK_PLATE, new Item.Properties()));
        ELEVATOR = Registry.register(BuiltInRegistries.BLOCK, CobblePulseTools.id("elevator"),
                new fr.cobblepulse.tools.block.ElevatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                        .requiresCorrectToolForDrops().sound(SoundType.METAL)));
        ELEVATOR_ITEM = Registry.register(BuiltInRegistries.ITEM, CobblePulseTools.id("elevator"), new BlockItem(ELEVATOR, new Item.Properties()));
        EDITOR_WAND = Registry.register(BuiltInRegistries.ITEM, CobblePulseTools.id("pokemon_editor_wand"),
                new PokemonEditorWand(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
        CreativeModeTab tab = FabricItemGroup.builder()
                .icon(() -> new ItemStack(MOVEMENT_PLATE_ITEM))
                .title(Component.translatable("itemGroup.cobblepulse_tools"))
                .displayItems((params, out) -> {
                    out.accept(MOVEMENT_PLATE_ITEM);
                    out.accept(STICK_PLATE_ITEM);
                    out.accept(ELEVATOR_ITEM);
                    out.accept(EDITOR_WAND);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CobblePulseTools.id("tools"), tab);
    }
}
