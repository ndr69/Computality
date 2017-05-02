/**
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2016. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.media.IMediaProvider;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.api.permissions.ITurtlePermissionProvider;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.api.redstone.IBundledRedstoneProvider;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.core.filesystem.ComboMount;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.JarMount;
import dan200.computercraft.shared.common.DefaultBundledRedstoneProvider;
import dan200.computercraft.shared.computer.blocks.BlockCommandComputer;
import dan200.computercraft.shared.computer.blocks.BlockComputer;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.core.ClientComputerRegistry;
import dan200.computercraft.shared.computer.core.ServerComputerRegistry;
import dan200.computercraft.shared.media.items.ItemDiskExpanded;
import dan200.computercraft.shared.media.items.ItemDiskLegacy;
import dan200.computercraft.shared.media.items.ItemPrintout;
import dan200.computercraft.shared.media.items.ItemTreasureDisk;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.network.PacketHandler;
import dan200.computercraft.shared.peripheral.common.BlockCable;
import dan200.computercraft.shared.peripheral.common.BlockPeripheral;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.modem.BlockAdvancedModem;
import dan200.computercraft.shared.peripheral.modem.WirelessNetwork;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.pocket.peripherals.PocketModem;
import dan200.computercraft.shared.proxy.ICCTurtleProxy;
import dan200.computercraft.shared.proxy.IComputerCraftProxy;
import dan200.computercraft.shared.turtle.blocks.BlockTurtle;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.upgrades.*;
import dan200.computercraft.shared.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

///////////////
// UNIVERSAL //
///////////////

@Mod(modid = "computercraft", name = "ComputerCraft", version = "${version}", guiFactory = "dan200.computercraft.client.gui.GuiConfig$Factory")
public class ComputerCraft {
    // GUI IDs
    public static final int diskDriveGUIID = 100;
    public static final int computerGUIID = 101;
    public static final int printerGUIID = 102;
    public static final int turtleGUIID = 103;
    // ComputerCraftEdu uses ID 104
    public static final int printoutGUIID = 105;
    public static final int pocketComputerGUIID = 106;
    public static final int terminalWidth_computer = 51;
    public static final int terminalHeight_computer = 19;
    public static final int terminalWidth_turtle = 39;
    public static final int terminalHeight_turtle = 13;
    public static final int terminalWidth_pocketComputer = 26;
    public static final int terminalHeight_pocketComputer = 20;

    public static class Config {
        // Configuration options
        public static boolean http_enable = true;
        public static String http_whitelist = "*";
        public static boolean disable_lua51_features = false;
        public static String default_computer_settings = "";
        public static boolean enableCommandBlock = false;
        public static boolean turtlesNeedFuel = true;
        public static int turtleFuelLimit = 20000;
        public static int advancedTurtleFuelLimit = 100000;
        public static boolean turtlesObeyBlockProtection = true;
        public static boolean turtlesCanPush = true;
        public static int modem_range = 64;
        public static int modem_highAltitudeRange = 384;
        public static int modem_rangeDuringStorm = 64;
        public static int modem_highAltitudeRangeDuringStorm = 384;
        public static int computerSpaceLimit = 1000 * 1000;
        public static int floppySpaceLimit = 125 * 1000;
        public static int maximumFilesOpen  = 128;
        public static Configuration config;
    }
    public static Iterable<IPocketUpgrade> getVanillaPocketUpgrades() {
        List<IPocketUpgrade> upgrades = new ArrayList<>();
        for(IPocketUpgrade upgrade : pocketUpgrades.values()) {
            if(upgrade instanceof PocketModem) {
                upgrades.add( upgrade );
            }
        }

        return upgrades;
    }

    public static class PocketUpgrades
    {
        public static PocketModem wirelessModem;
        public static PocketModem advancedModem;
    }

    // Registries
    public static ClientComputerRegistry clientComputerRegistry = new ClientComputerRegistry();
    public static ServerComputerRegistry serverComputerRegistry = new ServerComputerRegistry();
    // Networking
    public static FMLEventChannel networkEventChannel;
    // Creative
    public static CreativeTabMain mainCreativeTab;
    // Implementation
    @Mod.Instance(value = "computercraft")
    public static ComputerCraft instance;
    @SidedProxy(clientSide = "dan200.computercraft.client.proxy.ComputerCraftProxyClient", serverSide = "dan200.computercraft.server.proxy.ComputerCraftProxyServer")
    public static IComputerCraftProxy proxy;
    @SidedProxy(clientSide = "dan200.computercraft.client.proxy.CCTurtleProxyClient", serverSide = "dan200.computercraft.server.proxy.CCTurtleProxyServer")
    public static ICCTurtleProxy turtleProxy;
    // API users
    private static List<IPeripheralProvider> peripheralProviders = new ArrayList<IPeripheralProvider>();
    private static List<IBundledRedstoneProvider> bundledRedstoneProviders = new ArrayList<IBundledRedstoneProvider>();
    private static List<IMediaProvider> mediaProviders = new ArrayList<IMediaProvider>();
    private static List<ITurtlePermissionProvider> permissionProviders = new ArrayList<ITurtlePermissionProvider>();
    private static final Map<String, IPocketUpgrade> pocketUpgrades = new HashMap<String, IPocketUpgrade>();

    public ComputerCraft() {
    }

    public static String getVersion() {
        return "${version}";
    }

    public static boolean isClient() {
        return proxy.isClient();
    }

    public static boolean getGlobalCursorBlink() {
        return proxy.getGlobalCursorBlink();
    }

    public static long getRenderFrame() {
        return proxy.getRenderFrame();
    }

    public static void deleteDisplayLists(int list, int range) {
        proxy.deleteDisplayLists(list, range);
    }

    public static Object getFixedWidthFontRenderer() {
        return proxy.getFixedWidthFontRenderer();
    }

    public static void playRecord(SoundEvent record, String recordInfo, World world, BlockPos pos) {
        proxy.playRecord(record, recordInfo, world, pos);
    }

    public static String getRecordInfo(ItemStack recordStack) {
        return proxy.getRecordInfo(recordStack);
    }

    public static void openDiskDriveGUI(EntityPlayer player, TileDiskDrive drive) {
        BlockPos pos = drive.getPos();
        player.openGui(ComputerCraft.instance, ComputerCraft.diskDriveGUIID, player.getEntityWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openComputerGUI(EntityPlayer player, TileComputer computer) {
        BlockPos pos = computer.getPos();
        player.openGui(ComputerCraft.instance, ComputerCraft.computerGUIID, player.getEntityWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openPrinterGUI(EntityPlayer player, TilePrinter printer) {
        BlockPos pos = printer.getPos();
        player.openGui(ComputerCraft.instance, ComputerCraft.printerGUIID, player.getEntityWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openTurtleGUI(EntityPlayer player, TileTurtle turtle) {
        BlockPos pos = turtle.getPos();
        player.openGui(instance, ComputerCraft.turtleGUIID, player.getEntityWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openPrintoutGUI(EntityPlayer player, EnumHand hand) {
        player.openGui(ComputerCraft.instance, ComputerCraft.printoutGUIID, player.getEntityWorld(), hand.ordinal(), 0, 0);
    }

    public static void openPocketComputerGUI(EntityPlayer player, EnumHand hand) {
        player.openGui(ComputerCraft.instance, ComputerCraft.pocketComputerGUIID, player.getEntityWorld(), hand.ordinal(), 0, 0);
    }

    public static File getBaseDir() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getFile(".");
    }

    public static File getResourcePackDir() {
        return new File(getBaseDir(), "resourcepacks");
    }

    public static File getWorldDir(World world) {
        return proxy.getWorldDir(world);
    }

    private static FMLProxyPacket encode(ComputerCraftPacket packet) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        packet.toBytes(buffer);
        return new FMLProxyPacket(buffer, "CC");
    }

    public static void sendToPlayer(EntityPlayer player, ComputerCraftPacket packet) {
        networkEventChannel.sendTo(encode(packet), (EntityPlayerMP) player);
    }

    public static void sendToAllPlayers(ComputerCraftPacket packet) {
        networkEventChannel.sendToAll(encode(packet));
    }

    public static void sendToServer(ComputerCraftPacket packet) {
        networkEventChannel.sendToServer(encode(packet));
    }

    public static void handlePacket(ComputerCraftPacket packet, EntityPlayer player) {
        proxy.handlePacket(packet, player);
    }

    public static boolean canPlayerUseCommands(EntityPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList().canSendCommands(player.getGameProfile());
    }

    public static boolean isPlayerOpped(EntityPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList().getOppedPlayers().getPermissionLevel(player.getGameProfile()) > 0;
    }

    public static void registerPermissionProvider(ITurtlePermissionProvider provider) {
        if (provider != null && !permissionProviders.contains(provider)) {
            permissionProviders.add(provider);
        }
    }

    public static boolean isBlockEnterable(World world, BlockPos pos, EntityPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && !world.isRemote) {
            if (server.isBlockProtected(world, pos, player)) {
                return false;
            }
        }

        for (ITurtlePermissionProvider provider : permissionProviders) {
            if (!provider.isBlockEnterable(world, pos)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBlockEditable(World world, BlockPos pos, EntityPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && !world.isRemote) {
            if (server.isBlockProtected(world, pos, player)) {
                return false;
            }
        }

        for (ITurtlePermissionProvider provider : permissionProviders) {
            if (!provider.isBlockEditable(world, pos)) {
                return false;
            }
        }
        return true;
    }

    public static void registerPocketUpgrade( IPocketUpgrade upgrade )
    {
        String id = upgrade.getUpgradeID().toString();
        IPocketUpgrade existing = pocketUpgrades.get( id );
        if( existing != null )
        {
            throw new RuntimeException( "Error registering '" + upgrade.getUnlocalisedAdjective() + " pocket computer'. UpgradeID '" + id + "' is already registered by '" + existing.getUnlocalisedAdjective() + " pocket computer'" );
        }

        pocketUpgrades.put( id, upgrade );
    }

    public static void registerPeripheralProvider( IPeripheralProvider provider )
    {
        if( provider != null && !peripheralProviders.contains( provider ) )
        {
            peripheralProviders.add( provider );
        }
    }

    public static void registerBundledRedstoneProvider(IBundledRedstoneProvider provider) {
        if (provider != null && !bundledRedstoneProviders.contains(provider)) {
            bundledRedstoneProviders.add(provider);
        }
    }

    public static void registerMediaProvider(IMediaProvider provider) {
        if (provider != null && !mediaProviders.contains(provider)) {
            mediaProviders.add(provider);
        }
    }

    public static IPeripheral getPeripheralAt(World world, BlockPos pos, EnumFacing side) {
        // Try the handlers in order:
        for (IPeripheralProvider peripheralProvider : peripheralProviders) {
            try {
                IPeripheralProvider handler = peripheralProvider;
                IPeripheral peripheral = handler.getPeripheral(world, pos, side);
                if (peripheral != null) {
                    return peripheral;
                }
            } catch (Exception e) {
                // mod misbehaved, ignore it
            }
        }
        return null;
    }

    public static int getDefaultBundledRedstoneOutput(World world, BlockPos pos, EnumFacing side) {
        if (WorldUtil.isBlockInWorld(world, pos)) {
            return DefaultBundledRedstoneProvider.getDefaultBundledRedstoneOutput(world, pos, side);
        }
        return -1;
    }

    public static int getBundledRedstoneOutput(World world, BlockPos pos, EnumFacing side) {
        int y = pos.getY();
        if (y < 0 || y >= world.getHeight()) {
            return -1;
        }

        // Try the handlers in order:
        int combinedSignal = -1;
        for (IBundledRedstoneProvider bundledRedstoneProvider : bundledRedstoneProviders) {
            try {
                IBundledRedstoneProvider handler = bundledRedstoneProvider;
                int signal = handler.getBundledRedstoneOutput(world, pos, side);
                if (signal >= 0) {
                    if (combinedSignal < 0) {
                        combinedSignal = (signal & 0xffff);
                    } else {
                        combinedSignal = combinedSignal | (signal & 0xffff);
                    }
                }
            } catch (Exception e) {
                // mod misbehaved, ignore it
            }
        }
        return combinedSignal;
    }

    public static IMedia getMedia(ItemStack stack) {
        if (stack != null) {
            // Try the handlers in order:
            for (IMediaProvider mediaProvider : mediaProviders) {
                try {
                    IMediaProvider handler = mediaProvider;
                    IMedia media = handler.getMedia(stack);
                    if (media != null) {
                        return media;
                    }
                } catch (Exception e) {
                    // mod misbehaved, ignore it
                }
            }
            return null;
        }
        return null;
    }

    public static int createUniqueNumberedSaveDir( World world, String parentSubPath )
    {
        return IDAssigner.getNextIDFromDirectory(new File(getWorldDir(world), parentSubPath));
    }

    public static IWritableMount createSaveDirMount(World world, String subPath, long capacity) {
        try {
            return new FileMount(new File(getWorldDir(world), subPath), capacity);
        } catch (Exception e) {
            return null;
        }
    }

    public static IMount createResourceMount(Class modClass, String domain, String subPath) {
        // Start building list of mounts
        List<IMount> mounts = new ArrayList<IMount>();
        subPath = "assets/" + domain + "/" + subPath;

        // Mount from debug dir
        File codeDir = getDebugCodeDir(modClass);
        if (codeDir != null) {
            File subResource = new File(codeDir, subPath);
            if (subResource.exists()) {
                IMount resourcePackMount = new FileMount(subResource, 0);
                mounts.add(resourcePackMount);
            }
        }

        // Mount from mod jar
        File modJar = getContainingJar(modClass);
        if (modJar != null) {
            try {
                IMount jarMount = new JarMount(modJar, subPath);
                mounts.add(jarMount);
            } catch (IOException e) {
                // Ignore
            }
        }

        // Mount from resource packs
        File resourcePackDir = getResourcePackDir();
        if (resourcePackDir.exists() && resourcePackDir.isDirectory()) {
            String[] resourcePacks = resourcePackDir.list();
            if (resourcePacks != null) {
                for (String resourcePack1 : resourcePacks) {
                    try {
                        File resourcePack = new File(resourcePackDir, resourcePack1);
                        if (!resourcePack.isDirectory()) {
                            // Mount a resource pack from a jar
                            IMount resourcePackMount = new JarMount(resourcePack, subPath);
                            mounts.add(resourcePackMount);
                        } else {
                            // Mount a resource pack from a folder
                            File subResource = new File(resourcePack, subPath);
                            if (subResource.exists()) {
                                IMount resourcePackMount = new FileMount(subResource, 0);
                                mounts.add(resourcePackMount);
                            }
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }

        // Return the combination of all the mounts found
        if (mounts.size() >= 2) {
            IMount[] mountArray = new IMount[mounts.size()];
            mounts.toArray(mountArray);
            return new ComboMount(mountArray);
        } else if (mounts.size() == 1) {
            return mounts.get(0);
        } else {
            return null;
        }
    }

    private static File getContainingJar(Class modClass) {
        String path = modClass.getProtectionDomain().getCodeSource().getLocation().getPath();
        int bangIndex = path.indexOf("!");
        if (bangIndex >= 0) {
            path = path.substring(0, bangIndex);
        }

        URL url;
        try {
            url = new URL(path);
        } catch (MalformedURLException e1) {
            return null;
        }

        File file;
        try {
            file = new File(url.toURI());
        } catch (URISyntaxException e) {
            file = new File(url.getPath());
        }
        return file;
    }

    private static File getDebugCodeDir(Class modClass) {
        String path = modClass.getProtectionDomain().getCodeSource().getLocation().getPath();
        int bangIndex = path.indexOf("!");
        if (bangIndex >= 0) {
            return null;
        }
        return new File(new File(path).getParentFile(), "../..");
    }

    public static void registerTurtleUpgrade(ITurtleUpgrade upgrade) {
        turtleProxy.registerTurtleUpgrade(upgrade);
    }

    public static ITurtleUpgrade getTurtleUpgrade(String id) {
        return turtleProxy.getTurtleUpgrade(id);
    }

    public static ITurtleUpgrade getTurtleUpgrade(int legacyID) {
        return turtleProxy.getTurtleUpgrade(legacyID);
    }

    public static ITurtleUpgrade getTurtleUpgrade(ItemStack item) {
        return turtleProxy.getTurtleUpgrade(item);
    }

    public static void addAllUpgradedTurtles(List<ItemStack> list) {
        turtleProxy.addAllUpgradedTurtles(list);
    }

    public static void setEntityDropConsumer(Entity entity, IEntityDropConsumer consumer) {
        turtleProxy.setEntityDropConsumer(entity, consumer);
    }

    public static void clearEntityDropConsumer(Entity entity) {
        turtleProxy.clearEntityDropConsumer(entity);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Load config
        Config.config = new Configuration(event.getSuggestedConfigurationFile());
        Config.config.load();

        // Setup general

        Property prop = Config.config.get(Configuration.CATEGORY_GENERAL, "http_enable", Config.http_enable);
        prop.setComment("Enable the \"http\" API on Computers (see \"http_whitelist\" for more fine grained control than this)");
        Config.http_enable = prop.getBoolean(Config.http_enable);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "http_whitelist", Config.http_whitelist);
        prop.setComment("A semicolon limited list of wildcards for domains that can be accessed through the \"http\" API on Computers. Set this to \"*\" to access to the entire internet. Example: \"*.pastebin.com;*.github.com;*.computercraft.info\" will restrict access to just those 3 domains.");
        Config.http_whitelist = prop.getString();

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "disable_lua51_features", Config.disable_lua51_features);
        prop.setComment("Set this to true to disable Lua 5.1 functions that will be removed in a future update. Useful for ensuring forward compatibility of your programs now.");
        Config.disable_lua51_features = prop.getBoolean(Config.disable_lua51_features);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "default_computer_settings", Config.default_computer_settings);
        prop.setComment("A comma seperated list of default system settings to set on new computers. Example: \"shell.autocomplete=false,lua.autocomplete=false,edit.autocomplete=false\" will disable all autocompletion");
        Config.default_computer_settings = prop.getString();

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "enableCommandBlock", Config.enableCommandBlock);
        prop.setComment("Enable Command Block peripheral support");
        Config.enableCommandBlock = prop.getBoolean(Config.enableCommandBlock);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "modem_range", Config.modem_range);
        prop.setComment("The range of Wireless Modems at low altitude in clear weather, in meters");
        Config.modem_range = Math.min(prop.getInt(), 100000);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "modem_highAltitudeRange", Config.modem_highAltitudeRange);
        prop.setComment("The range of Wireless Modems at maximum altitude in clear weather, in meters");
        Config.modem_highAltitudeRange = Math.min(prop.getInt(), 100000);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "modem_rangeDuringStorm", Config.modem_rangeDuringStorm);
        prop.setComment("The range of Wireless Modems at low altitude in stormy weather, in meters");
        Config.modem_rangeDuringStorm = Math.min(prop.getInt(), 100000);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "modem_highAltitudeRangeDuringStorm", Config.modem_highAltitudeRangeDuringStorm);
        prop.setComment("The range of Wireless Modems at maximum altitude in stormy weather, in meters");
        Config.modem_highAltitudeRangeDuringStorm = Math.min(prop.getInt(), 100000);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "computerSpaceLimit", Config.computerSpaceLimit);
        prop.setComment("The disk space limit for computers and turtles, in bytes");
        Config.computerSpaceLimit = prop.getInt();

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "floppySpaceLimit", Config.floppySpaceLimit);
        prop.setComment("The disk space limit for floppy disks, in bytes");
        Config.floppySpaceLimit = prop.getInt();

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "maximumFilesOpen", Config.maximumFilesOpen);
        prop.setComment("How many files a computer can have open at the same time");
        Config.maximumFilesOpen = prop.getInt();

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "turtlesNeedFuel", Config.turtlesNeedFuel);
        prop.setComment("Set whether Turtles require fuel to move");
        Config.turtlesNeedFuel = prop.getBoolean(Config.turtlesNeedFuel);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "turtleFuelLimit", Config.turtleFuelLimit);
        prop.setComment("The fuel limit for Turtles");
        Config.turtleFuelLimit = prop.getInt(Config.turtleFuelLimit);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "advancedTurtleFuelLimit", Config.advancedTurtleFuelLimit);
        prop.setComment("The fuel limit for Advanced Turtles");
        Config.advancedTurtleFuelLimit = prop.getInt(Config.advancedTurtleFuelLimit);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "turtlesObeyBlockProtection", Config.turtlesObeyBlockProtection);
        prop.setComment("If set to true, Turtles will be unable to build, dig, or enter protected areas (such as near the server spawn point)");
        Config.turtlesObeyBlockProtection = prop.getBoolean(Config.turtlesObeyBlockProtection);

        prop = Config.config.get(Configuration.CATEGORY_GENERAL, "turtlesCanPush", Config.turtlesCanPush);
        prop.setComment("If set to true, Turtles will push entities out of the way instead of stopping if there is space to do so");
        Config.turtlesCanPush = prop.getBoolean(Config.turtlesCanPush);

        Config.config.save();

        // Setup network
        networkEventChannel = NetworkRegistry.INSTANCE.newEventDrivenChannel("CC");
        networkEventChannel.register(new PacketHandler());

        proxy.preInit();
        turtleProxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        turtleProxy.init();
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
    }

    @Mod.EventHandler
    public void onServerStart(FMLServerStartedEvent event) {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
            ComputerCraft.serverComputerRegistry.reset();
            WirelessNetwork.resetNetworks();
        }
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
            ComputerCraft.serverComputerRegistry.reset();
            WirelessNetwork.resetNetworks();
        }
    }

    // Blocks and Items
    public static class Blocks {
        public static BlockComputer computer;
        public static BlockPeripheral peripheral;
        public static BlockCable cable;
        public static BlockTurtle turtle;
        public static BlockTurtle turtleExpanded;
        public static BlockTurtle turtleAdvanced;
        public static BlockCommandComputer commandComputer;
        public static BlockAdvancedModem advancedModem;
    }

    public static class Items {
        public static ItemDiskLegacy disk;
        public static ItemDiskExpanded diskExpanded;
        public static ItemPrintout printout;
        public static ItemTreasureDisk treasureDisk;
        public static ItemPocketComputer pocketComputer;
    }

    public static class Upgrades {
        public static TurtleModem wirelessModem;
        public static TurtleCraftingTable craftingTable;
        public static TurtleSword diamondSword;
        public static TurtleShovel diamondShovel;
        public static TurtleTool diamondPickaxe;
        public static TurtleAxe diamondAxe;
        public static TurtleHoe diamondHoe;
        public static TurtleModem advancedModem;
    }
}
