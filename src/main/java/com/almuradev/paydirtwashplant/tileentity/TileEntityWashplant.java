/*
 * This file is part of Paydirt-Washplant.
 *
 * Copyright (c) AlmuraDev <https://github.com/AlmuraDev/>
 *
 * All Rights Reserved.
 */
package com.almuradev.paydirtwashplant.tileentity;

import com.almuradev.paydirtwashplant.Config;
import com.almuradev.paydirtwashplant.PDWPMod;
import com.almuradev.paydirtwashplant.util.DirectionHelper;
import com.almuradev.paydirtwashplant.util.ItemStackHelper;
import com.almuradev.paydirtwashplant.util.Voltage;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.Random;

import javax.annotation.Nullable;

public class TileEntityWashplant extends TileEntity implements IFluidHandler, ISidedInventory, ITickable {

    private static final Random RANDOM = new Random();
    private static final int[] NO_SLOTS = new int[0];
    private static final int[] LEFT_SLOTS = new int[]{0};
    private static final int[] RIGHT_SLOTS = new int[]{1};
    private final FluidTank tank;
    private final Sink sink;
    private final ItemStack[] slots = new ItemStack[4];
    private boolean washing;
    private int washTime = 0;
    private EnumFacing facing;

    public TileEntityWashplant() {
        this.tank = new WashPlantTank(Config.WATER_BUFFER);
        this.sink = new Sink(this, Config.EU_BUFFER, Voltage.getVoltage(Config.VOLTAGE));
        this.facing = EnumFacing.NORTH;
        Arrays.fill(this.slots, ItemStack.EMPTY);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.sink.readFromNBT(tag);
        this.tank.readFromNBT(tag);
        this.washTime = tag.getInteger("wash Time");
        this.washing = tag.getBoolean("Washing");

        NBTTagList items = tag.getTagList("Items", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < items.tagCount(); ++i) {
            NBTTagCompound item = items.getCompoundTagAt(i);

            int slot = item.getInteger("Slot");
            if (slot >= 0 && slot < getSizeInventory()) {
                this.slots[slot] = new ItemStack(item);
            }
        }

        super.readFromNBT(tag);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        this.sink.writeToNBT(tag);
        this.tank.writeToNBT(tag);

        tag.setInteger("Wash Time", this.washTime);
        tag.setBoolean("Washing", this.washing);
        tag.setByte("Facing", (byte) this.facing.ordinal());

        NBTTagList nbttaglist = new NBTTagList();

        for (int i = 0; i < this.slots.length; ++i) {
            if (!this.slots[i].isEmpty()) {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setByte("Slot", (byte) i);
                this.slots[i].writeToNBT(nbttagcompound1);
                nbttaglist.appendTag(nbttagcompound1);
            }
        }

        tag.setTag("Items", nbttaglist);

        return super.writeToNBT(tag);
    }

    @Override
    public void invalidate() {
        this.sink.invalidate();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.sink.onChunkUnload();
    }

    @Override
    public void markDirty() {
        if (this.world != null && !this.world.isRemote) {
            this.world.markChunkDirty(this.pos, this);
        }
        super.markDirty();
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return false;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    private void toggleWashing(boolean b) {
        if (this.washing != b) {
            this.washing = b;
        }
    }

    /**
     * Accessor for block rendering.
     *
     * @return True if the block is active
     */
    public boolean isActive() {
        return this.washing;
    }

    /**
     * Accessor for stored facing.
     *
     * @return The facing of the tile
     */
    public EnumFacing getFacing() {
        return this.facing;
    }

    /**
     * Setter for stored facing.
     *
     * @param facing The facing of the tile
     */
    public boolean setFacing(EnumFacing facing) {
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            return false;
        }
        this.facing = facing;
        return true;
    }

    public boolean isWashing() {
        return this.washTime > 0;
    }

    private boolean canWash() {
        return this.tank.getFluidAmount() >= Config.WATER_PER_OPERATION && this.sink.canUseEnergy(Config.EU_PER_OPERATION) && !this.slots[0]
            .isEmpty();
    }

    public void washItem() {
        this.tank.drain(Config.WATER_PER_OPERATION, true);
        this.sink.useEnergy(Config.EU_PER_OPERATION);

        if (success(Block.getBlockFromItem(this.slots[0].getItem()))) {
            dumpTo(Config.minedItem(), 1);
        }

        this.slots[0].shrink(1);
    }

    private boolean success(Block block) {
        final double r = RANDOM.nextDouble();
        final double b;

        if (block == Blocks.COBBLESTONE) {
            b = Config.COBBLESTONE_PERCENTAGE;
        } else if (block == Blocks.GRAVEL) {
            b = Config.GRAVEL_PERCENTAGE;
        } else if (block == Blocks.DIRT) {
            b = Config.DIRT_PERCENTAGE;
        } else {
            b = 0D;
        }

        return r < b;
    }

    public boolean acceptsEnergyFrom(EnumFacing direction) {
        return direction == EnumFacing.DOWN || direction == this.facing.getOpposite();
    }

    // Fluid

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return this.tank.fill(resource, doFill);
    }

    @Override
    @Nullable
    public FluidStack drain(FluidStack fluidStack, boolean b) {
        return null;
    }

    @Override
    @Nullable
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return this.tank.getTankProperties();
    }

    @Override
    public int[] getSlotsForFace(EnumFacing direction) {
        if (direction.equals(DirectionHelper.getRelativeSide(this.facing, "left"))) {
            return LEFT_SLOTS;

        }
        if (direction.equals(DirectionHelper.getRelativeSide(this.facing, "right"))) {
            return RIGHT_SLOTS;
        }

        return NO_SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, EnumFacing side) {
        return side.equals(DirectionHelper.getRelativeSide(this.facing, "left")) && isItemValidForSlot(slot, item);

    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, EnumFacing side) {
        return side.equals(DirectionHelper.getRelativeSide(this.facing, "right"));
    }

    @Override
    public int getSizeInventory() {
        return this.slots.length;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return this.slots[i];
    }

    @Override
    public ItemStack decrStackSize(int slot, int i) {
        if (this.slots[slot] != ItemStack.EMPTY) {
            ItemStack itemstack;

            if (this.slots[slot].getCount() <= i) {
                itemstack = this.slots[slot];
                this.slots[slot] = ItemStack.EMPTY;
                this.markDirty();
                return itemstack;
            } else {
                itemstack = this.slots[slot].splitStack(i);

                if (this.slots[slot].isEmpty()) {
                    this.slots[slot] = ItemStack.EMPTY;
                }

                this.markDirty();
                return itemstack;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        final ItemStack result = this.slots[index];
        this.slots[index] = ItemStack.EMPTY;
        return result;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack itemstack) {
        this.slots[slot] = itemstack;

        if (!itemstack.isEmpty() && itemstack.getCount() > this.getInventoryStackLimit()) {
            itemstack.setCount(this.getInventoryStackLimit());
        }

        this.markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack itemstack) {
        if (slot == 0) {
            Block block = Block.getBlockFromItem(itemstack.getItem());
            return block != Blocks.AIR && (block.equals(Blocks.COBBLESTONE) || block.equals(Blocks.GRAVEL) || block.equals(Blocks.DIRT));
        }
        return false;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        Arrays.fill(this.slots, ItemStack.EMPTY);
    }

    public void dropInventory() {
        for (ItemStack itemstack : this.slots) {
            if (!itemstack.isEmpty()) {
                this.world.spawnEntity(new EntityItem(this.world, this.pos.getX(), this.pos.getY(), this.pos.getZ(), itemstack));
            }
        }
    }

    public int getFluidLevel() {
        return this.tank.getFluidAmount();
    }

    public int getPowerLevel() {
        return (int) this.sink.getEnergyStored();
    }

    public int getWashTime() {
        return this.washTime;
    }

    public void setFluidLevel(int fluidLevel) {
        this.tank.setFluid(new FluidStack(FluidRegistry.WATER, fluidLevel));
    }

    public void setPowerLevel(int powerLevel) {
        this.sink.setEnergyStored(powerLevel);
    }

    public void setWashTime(int washtime) {
        this.washTime = washtime;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound tag = new NBTTagCompound();
        return new SPacketUpdateTileEntity(this.pos, 1, tag);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private void processFluid() {
        if (this.slots[2].isEmpty()) {
            return;
        }
        final ItemStack input = this.slots[2].copy();
        input.setCount(1);

        @Nullable final IFluidHandlerItem itemFluidHandler = input.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (itemFluidHandler == null) {
            return;
        }

        final int currentAmount = getFluidLevel();
        @Nullable final FluidStack contained =
            itemFluidHandler.drain(new FluidStack(FluidRegistry.WATER, Config.WATER_BUFFER - currentAmount), false);

        if (contained == null || contained.getFluid() != FluidRegistry.WATER || this.tank.fill(contained, false) <= 0) {
            return;
        }

        final ItemStack actualLeftOver = ItemStackHelper.getActualLeftOver(input, contained);
        if (!canDumpTo(actualLeftOver, 3)) {
            return;
        }

        this.tank.fill(contained, true);
        itemFluidHandler.drain(contained, true);
        this.slots[2].shrink(1);
        dumpTo(itemFluidHandler.getContainer(), 3);
    }

    private boolean canDumpTo(final ItemStack stack, final int slot) {
        return ItemStackHelper.canMerge(stack, this.slots[slot]);
    }

    private void dumpTo(final ItemStack stack, final int slot) {
        this.slots[slot] = ItemStackHelper.merge(this.slots[slot], stack);
    }

    public int washTimeScaled(int scale) {
        return (getWashTime() * scale) / Config.WASH_TIME;
    }

    public int fluidScaled(int scale) {
        return (getFluidLevel() * scale) / this.tank.getCapacity();
    }

    public int powerScaled(int scale) {
        return (int) ((getPowerLevel() * scale) / this.sink.getCapacity());
    }

    @Override
    public void update() {
        if (this.world.isRemote) {
            return;
        }

        this.sink.update();

        processFluid();

        boolean updateInventory = false;
        if (canWash()) {
            toggleWashing(true);

            this.washTime++;

            if (this.washTime % 20 == 0) {
                this.world.playSound(null, this.pos, PDWPMod.getInstance().getWashingSoundEvent(), SoundCategory.BLOCKS, 1, 1);
            }

            if (this.washTime == Config.WASH_TIME) {
                if (canDumpTo(Config.minedItem(), 1)) {
                    this.washItem();
                    updateInventory = true;
                }
                this.washTime = 0;
            }
        } else {
            this.washTime = 0;
            toggleWashing(false);
        }
        if (updateInventory) {
            this.markDirty();
        }
    }

    @Override
    public String getName() {
        return "Washplant";
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, facing);
    }
}