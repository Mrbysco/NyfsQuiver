package com.nyfaria.nyfsquiver.items;

import com.nyfaria.nyfsquiver.init.TagInit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nonnull;

import java.io.File;
import java.util.*;
 
public class QuiverInventory implements IItemHandlerModifiable {

    private final boolean remote;
    private final ArrayList<ItemStack> stacks = new ArrayList<>();
    private final int inventoryIndex;
    public int rows;
    public int columns;

    public QuiverInventory(boolean remote, int inventoryIndex, int rows, int columns){
        this.remote = remote;
        this.inventoryIndex = inventoryIndex;
        this.rows = rows;
        this.columns = columns;
        for(int a = 0; a < this.rows * this.columns; a++)
            this.stacks.add(ItemStack.EMPTY);
    }


    public QuiverInventory(boolean remote, int inventoryIndex){
        this.remote = remote;
        this.inventoryIndex = inventoryIndex;
    }

    @Override
    public int getSlots(){
        return this.rows * this.columns;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot){
        return this.stacks.get(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
        ItemStack current = this.stacks.get(slot);
        if(!stack.isEmpty() && this.isItemValid(slot, stack) && canStack(current, stack)){
            int amount = Math.min(stack.getCount(), 64 - current.getCount());
            if(!simulate){
                ItemStack newStack = stack.copy();
                newStack.setCount(current.getCount() + amount);
                this.stacks.set(slot, newStack);

                if(!this.remote && stack.getItem() instanceof QuiverItem && stack.getOrCreateTag().contains("invIndex")){
                    int index = stack.getOrCreateTag().getInt("invIndex");
                }
            }
            ItemStack result = stack.copy();
            result.shrink(amount);
            return result;
        }
        return stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate){
        ItemStack stack = this.stacks.get(slot);
        int count = Math.min(amount, stack.getCount());
        ItemStack result = stack.copy();
        if(!simulate){
            stack.shrink(count);
            if(!this.remote && result.getItem() instanceof QuiverItem && result.getOrCreateTag().contains("invIndex")){
                int index = result.getOrCreateTag().getInt("invIndex");
                boolean contains = false;
                for(ItemStack stack1 : this.stacks){
                    if(stack1.getItem() instanceof QuiverItem && stack1.getOrCreateTag().contains("invIndex")
                        && stack1.getOrCreateTag().getInt("invIndex") == index){
                        contains = true;
                        break;
                    }
                }
            }
        }
        result.setCount(count);
        return result;
    }

    @Override
    public int getSlotLimit(int slot){
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack){
    	if(!stack.is(TagInit.QUIVER_ITEMS)) {
    		return false;
    	}

        return true;
    }

    private static boolean canStack(ItemStack stack1, ItemStack stack2){
        return stack1.isEmpty() || stack2.isEmpty() || (stack1.getItem() == stack2.getItem() && stack1.getDamageValue() == stack2.getDamageValue() && ItemStack.tagMatches(stack1, stack2));
    }

    public void save(File file){
        CompoundTag compound = new CompoundTag();
        compound.putInt("rows", this.rows);
        compound.putInt("columns", this.columns);
        compound.putInt("stacks", this.stacks.size());
        for(int slot = 0; slot < this.stacks.size(); slot++)
            compound.put("stack" + slot, this.stacks.get(slot).save(new CompoundTag()));
        try{
            NbtIo.write(compound, file);
        }catch(Exception e){e.printStackTrace();}
    }

    public void load(File file){
        CompoundTag compound;
        try{
            compound = NbtIo.read(file);
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        this.rows = compound.contains("rows") ? compound.getInt("rows") : compound.getInt("slots") / 9; // Do this for compatibility with older versions
        this.columns = compound.contains("columns") ? compound.getInt("columns") : compound.getInt("slots") / 9; // Do this for compatibility with older versions
        this.stacks.clear();
        int size = compound.contains("stacks") ? compound.getInt("stacks") : this.rows * this.columns; // Do this for compatibility with older versions
        for(int slot = 0; slot < size; slot++)
            this.stacks.add(ItemStack.of(compound.getCompound("stack" + slot)));
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack){
        ItemStack oldStack = this.stacks.get(slot);
        this.stacks.set(slot, ItemStack.EMPTY);

        if(!this.remote && oldStack.getItem() instanceof QuiverItem && oldStack.getOrCreateTag().contains("invIndex")){
            int index = oldStack.getOrCreateTag().getInt("invIndex");
            boolean contains = false;
            for(ItemStack stack1 : this.stacks){
                if(stack1.getItem() instanceof QuiverItem && stack1.getOrCreateTag().contains("invIndex")
                    && stack1.getOrCreateTag().getInt("invIndex") == index){
                    contains = true;
                    break;
                }
            }
        }

        this.stacks.set(slot, stack);

        if(!this.remote && stack.getItem() instanceof QuiverItem && stack.getOrCreateTag().contains("invIndex")){
            int index = stack.getOrCreateTag().getInt("invIndex");
        }
    }

    public void adjustSize(int rows, int columns){
        if(this.rows == rows && this.columns == columns)
            return;
        this.rows = rows;
        this.columns = columns;
        while(this.stacks.size() < this.rows * this.columns)
            this.stacks.add(ItemStack.EMPTY);
    }

    public List<ItemStack> getStacks(){
        return this.stacks;
    }

    public void setStacks(List<ItemStack> stacks){
        for(int i = 0; i < this.rows * this.columns; i++){
            if(i < stacks.size()) {
                this.stacks.set(i,stacks.get(i));
            }
            else {
                this.stacks.add(ItemStack.EMPTY);
            }
        }
    }
    public static void openQuiverInventory(ItemStack stack, Player player, int bagSlot){
        QuiverType type = ((QuiverItem)stack.getItem()).type;
        CompoundTag compound = stack.getOrCreateTag();
        if(!compound.contains("invIndex") || QuiverStorageManager.getInventory(compound.getInt("invIndex")) == null){
            compound.putInt("invIndex", QuiverStorageManager.createInventoryIndex(type));
            compound.putInt("slotIndex", 0);
            stack.setTag(compound);
        }else{
            QuiverInventory inventory = QuiverStorageManager.getInventory(compound.getInt("invIndex"));
            int rows = inventory.rows;
            int columns = inventory.columns;
            if(rows != type.getRows())
                inventory.adjustSize(type.getRows(),type.getColumns());
            if(columns != type.getColumns())
                inventory.adjustSize(type.getRows(),type.getColumns());
        }
        int inventoryIndex = compound.getInt("invIndex");
        QuiverInventory inventory = QuiverStorageManager.getInventory(inventoryIndex);
        if(bagSlot != -6) {
            NetworkHooks.openGui((ServerPlayer) player, new QuiverItem.ContainerProvider(stack.getDisplayName(), bagSlot, inventoryIndex, inventory), a -> {
                a.writeInt(bagSlot);
                a.writeInt(inventoryIndex);
                a.writeInt(inventory.rows);
                a.writeInt(inventory.columns);
            });
        }
    }
}
