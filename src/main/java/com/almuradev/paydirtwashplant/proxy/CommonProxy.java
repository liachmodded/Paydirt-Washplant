package com.almuradev.paydirtwashplant.proxy;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.almuradev.paydirtwashplant.Config;
import com.almuradev.paydirtwashplant.PDWPMod;
import com.almuradev.paydirtwashplant.block.gui.GuiHandler;
import com.almuradev.paydirtwashplant.tileentity.TileEntityWashplant;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.io.File;

public class CommonProxy {

	public void fmlLifeCycleEvent(FMLConstructionEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	public void fmlLifeCycleEvent(FMLPreInitializationEvent event) {
		initConfig(event);        
		registerTileEntities();
	}

	public void fmlLifeCycleEvent(FMLInitializationEvent event) {
		registerGuiHandlers();
	}   

	private void initConfig(FMLPreInitializationEvent event) {
		Config.setup(new File(event.getModConfigurationDirectory(), "/paydirt.cfg"));
	}

	@SubscribeEvent
	public void onRegisterBlocks(RegistryEvent.Register<Block> event) {
		event.getRegistry().register(PDWPMod.BLOCK_WASHPLANT);
	}

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlock(PDWPMod.BLOCK_WASHPLANT).setRegistryName(PDWPMod.BLOCK_WASHPLANT.getRegistryName()));
    }

	@SubscribeEvent
	public void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
		GameRegistry.addShapedRecipe(new ResourceLocation(PDWPMod.MODID, "washplant"), new ResourceLocation(PDWPMod.MODID, "washplant"), new
				ItemStack(PDWPMod.BLOCK_WASHPLANT),
				"XAX",
				"XBX",
				"XCX",
				'A', Blocks.REDSTONE_BLOCK,
				'B', Blocks.IRON_BLOCK,
				'C', Blocks.OBSIDIAN,
				'X', Blocks.AIR
				);
	}

	@SubscribeEvent
	public void onRegisterSounds(RegistryEvent.Register<SoundEvent> event) {
		event.getRegistry().register(PDWPMod.SOUND_WASHING);
	}

	private void registerTileEntities() {
		GameRegistry.registerTileEntity(TileEntityWashplant.class, "washplanttile");
	}

	private void registerGuiHandlers() {
		NetworkRegistry.INSTANCE.registerGuiHandler(PDWPMod.instance, new GuiHandler());
	}
}

