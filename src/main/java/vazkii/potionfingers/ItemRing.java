package vazkii.potionfingers;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class ItemRing extends Item implements IBauble {

    private static final String ITEM_NAME = "ring";

    protected static final String TAG_POTION_EFFECT = "effect";
    protected static final String TAG_DURABILITY = "durability";
    protected static final String TAG_MAX_DURABILITY = "maxDurability";

    protected static final int NO_DURABILITY = -1;

    private static final String[] VARIANTS = new String[]{
            "ring_disabled",
            "ring_enabled"
    };

    public static final int EFFECT_DURATION = 199;
    public static final int EFFECT_REFRESH_RATE = EFFECT_DURATION / 2;

    public ItemRing() {
        this.setTranslationKey(ITEM_NAME);
        setCreativeTab(CreativeTabs.BREWING);
        setMaxStackSize(1);
        setHasSubtypes(true);
    }

    @Nonnull
    @Override
    public Item setTranslationKey(@Nonnull String name) {
        super.setTranslationKey(name);
        setRegistryName(new ResourceLocation(PotionFingers.MOD_ID, name));
        return this;
    }

    @Nonnull
    @Override
    public String getTranslationKey(@Nonnull ItemStack stack) {
        int dmg = super.getDamage(stack);
        String[] variants = getVariants();

        if (dmg >= variants.length)
            return "item." + ITEM_NAME + ".name";
        return "item." + variants[dmg] + ".name";
    }

    @Nonnull
    public String[] getVariants() {
        return VARIANTS;
    }

    @Nonnull
    public String getModel() {
        return "ring";
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> subItems) {
        if (tab == getCreativeTab()) {
            subItems.add(new ItemStack(this));
            for (Potion p : PotionFingers.DEFAULT_EFFECTS) {
                subItems.add(getRingForPotion(p));
            }
        }
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return getPotion(stack) != null;
    }

    @Nonnull
    @Override
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        String name = getTranslationKey(stack);
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            Potion potion = getPotion(stack);
            if (potion != null) {
                return I18n.format(name, I18n.format(potion.getName()));
            }

            return I18n.format(name);
        }
        return name;
    }

    @Nonnull
    public static ItemStack getRingForPotion(@Nonnull Potion potion) {
        ResourceLocation registryName = potion.getRegistryName();
        if (registryName == null) {
            return ItemStack.EMPTY;
        }

        String id = registryName.toString();
        ItemStack stack = new ItemStack(PotionFingers.ring, 1, 1);
        getOrCreateNBTTag(stack).setString(TAG_POTION_EFFECT, id);
        return stack;
    }

    @Nonnull
    protected static NBTTagCompound getOrCreateNBTTag(@Nonnull ItemStack stack) {
        if (stack.getTagCompound() == null || !stack.hasTagCompound())
            stack.setTagCompound(new NBTTagCompound());

        return stack.getTagCompound();
    }

    @Nullable
    protected static Potion getPotion(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        String effect = getOrCreateNBTTag(stack).getString(TAG_POTION_EFFECT);
        if (effect.isEmpty()) {
            return null;
        }

        return Potion.REGISTRY.getObject(new ResourceLocation(effect));
    }

    protected static int getDurability(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return NO_DURABILITY;
        }

        NBTTagCompound tagCompound = getOrCreateNBTTag(stack);
        if (tagCompound.hasKey(TAG_DURABILITY)) {
            return tagCompound.getInteger(TAG_DURABILITY);
        }

        return NO_DURABILITY;
    }

    @SideOnly(Side.CLIENT)
    public IItemColor getItemColor() {
        return (stack, i) -> {
            if (i != 0) {
                Potion p = getPotion(stack);
                if (p != null) {
                    return p.getLiquidColor();
                }
            }

            return 0xFFFFFF;
        };
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        updatePotionStatus(player, getPotion(itemstack), itemstack, false);
    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {
        updatePotionStatus(player, getPotion(itemstack), itemstack, true);
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {
        Potion potion = getPotion(stack);
        if (potion != null && (player.ticksExisted % EFFECT_REFRESH_RATE == 0 || !player.isPotionActive(potion))) {
            // update the potion effect
            updatePotionStatus(player, potion, stack, false);

            // update the durability if it is used on this ring
            int durability = getDurability(stack);
            if (durability > NO_DURABILITY) {
                updateDurabilityStatus(player, durability, stack);
            }
        }
    }

    protected void updatePotionStatus(EntityLivingBase player, Potion potion, ItemStack stack, boolean unequipping) {
        if (potion == null || player.getEntityWorld().isRemote || !(player instanceof EntityPlayer)) {
            return;
        }

        IBaublesItemHandler inv = BaublesApi.getBaublesHandler((EntityPlayer) player);

        int level = -1;
        for (int slot : BaubleType.RING.getValidSlots()) {
            ItemStack baubleRing = inv.getStackInSlot(slot);
            Potion baublePotion = unequipping && stack == baubleRing ? null : getPotion(baubleRing);
            if (baublePotion == potion) {
                level++;
            }
        }
        if (level > 1) {
            level = 1;
        }
        PotionEffect currentEffect = player.getActivePotionEffect(potion);
        int currentLevel = currentEffect != null ? currentEffect.getAmplifier() : -1;
        if (currentLevel <= level || currentEffect != null) {
            if (unequipping && currentLevel > level && currentEffect.getDuration() <= EFFECT_DURATION) {
                player.removePotionEffect(potion);
            }
        }
        if (level != -1) {
            player.addPotionEffect(new PotionEffect(potion, EFFECT_DURATION, level, true, false));
        }
    }

    protected void updateDurabilityStatus(EntityLivingBase player, int durability, ItemStack stack) {
        if (durability < 0 || player.getEntityWorld().isRemote || !(player instanceof EntityPlayer)) {
            return;
        }

        if (durability - 1 >= 0) {
            // reduce durability by 1 if there is durability left
            getOrCreateNBTTag(stack).setInteger(TAG_DURABILITY, durability - 1);
        } else {
            // play the item breaking sound and then destroy the item
            player.getEntityWorld().playSound(null, player.getPosition(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            stack.setCount(0);
        }
    }

    @Override
    public boolean showDurabilityBar(@Nonnull ItemStack stack) {
        if (stack.hasTagCompound()) {
            //noinspection ConstantConditions
            return stack.getTagCompound().hasKey(TAG_DURABILITY);
        }
        return super.showDurabilityBar(stack);
    }

    @Override
    public double getDurabilityForDisplay(@Nonnull ItemStack stack) {
        return getDamage(stack) * 1.0D / getMaxDamage(stack);
    }

    @Override
    public int getMaxDamage(@Nonnull ItemStack stack) {
        //noinspection ConstantConditions
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TAG_MAX_DURABILITY)) {
            return stack.getTagCompound().getInteger(TAG_MAX_DURABILITY);
        }
        return super.getMaxDamage(stack);
    }

    @Override
    public int getDamage(@Nonnull ItemStack stack) {
        //noinspection ConstantConditions
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TAG_DURABILITY)) {
            return getMaxDamage(stack) - stack.getTagCompound().getInteger(TAG_DURABILITY);
        }
        return super.getDamage(stack);
    }

    @Override
    public boolean isDamaged(@Nonnull ItemStack stack) {
        //noinspection ConstantConditions
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TAG_DURABILITY)) {
            return stack.getTagCompound().getInteger(TAG_DURABILITY) >= 0;
        }
        return super.isDamaged(stack);
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.RING;
    }

    @SubscribeEvent
    public static void onRegister(ModelRegistryEvent event) {
        registerModel((ItemRing) PotionFingers.ring);
    }

    public static void registerModel(@Nonnull ItemRing item) {
        for (int i = 0; i < item.getVariants().length; i++) {
            ModelResourceLocation loc = new ModelResourceLocation(new ResourceLocation(PotionFingers.MOD_ID, item.getModel()), "inventory");
            ModelLoader.setCustomModelResourceLocation(item, i, loc);
        }
    }

    @SubscribeEvent
    public static void registerItems(@Nonnull RegistryEvent.Register<Item> event) {
        event.getRegistry().register(PotionFingers.ring);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemColorRegister(@Nonnull ColorHandlerEvent.Item event) {
        event.getItemColors().registerItemColorHandler(((ItemRing) PotionFingers.ring).getItemColor(), PotionFingers.ring);
    }
}
