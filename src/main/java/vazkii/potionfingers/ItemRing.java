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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
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

    private static final String TAG_POTION_EFFECT = "effect";

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
        int dmg = stack.getItemDamage();
        String[] variants = getVariants();

        if (dmg >= variants.length)
            return "item." + ITEM_NAME;
        return "item." + variants[dmg];
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
        String name = super.getItemStackDisplayName(stack);
        Potion potion = getPotion(stack);
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
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
    public static Potion getPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        String effect = getOrCreateNBTTag(stack).getString(TAG_POTION_EFFECT);
        if (effect.isEmpty()) {
            return null;
        }

        return Potion.REGISTRY.getObject(new ResourceLocation(effect));
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
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        Potion potion = getPotion(itemstack);
        if (potion != null && (player.ticksExisted % EFFECT_REFRESH_RATE == 0 || !player.isPotionActive(potion))) {
            updatePotionStatus(player, potion, itemstack, false);
        }
    }

    public void updatePotionStatus(EntityLivingBase player, Potion potion, ItemStack ring, boolean unequipping) {
        if (potion == null || player.world.isRemote || !(player instanceof EntityPlayer)) {
            return;
        }

        IBaublesItemHandler inv = BaublesApi.getBaublesHandler((EntityPlayer) player);

        int level = -1;
        for (int slot : BaubleType.RING.getValidSlots()) {
            ItemStack baubleRing = inv.getStackInSlot(slot);
            Potion baublePotion = unequipping && ring == baubleRing ? null : getPotion(baubleRing);
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
