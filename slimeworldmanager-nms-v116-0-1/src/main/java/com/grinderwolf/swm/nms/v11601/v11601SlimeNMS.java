package com.grinderwolf.swm.nms.v11601;

import com.flowpowered.nbt.CompoundTag;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.api.world.properties.SlimeProperties;
import com.grinderwolf.swm.nms.CraftSlimeWorld;
import com.grinderwolf.swm.nms.SlimeNMS;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.server.v1_16_R1.*;
import lombok.Getter;
import net.minecraft.server.v1_16_R1.GameRules.GameRuleKey;
import net.minecraft.server.v1_16_R1.GameRules.GameRuleValue;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.FileUtils;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R1.util.CraftMagicNumbers;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Getter
public class v11601SlimeNMS implements SlimeNMS {

    private static final Logger LOGGER = LogManager.getLogger("SWM");
    private static final File UNIVERSE_DIR;
    public static Convertable CONVERTABLE;
    private static boolean isPaperMC = false;

    static {
        Path path;

        try{
            path = Files.createTempDirectory("swm-" + UUID.randomUUID().toString().substring(0, 5) + "-");
        }catch(IOException ex) {
            LOGGER.log(Level.FATAL, "Failed to create temp directory", ex);
            path = null;
            System.exit(1);
        }

        UNIVERSE_DIR = path.toFile();
        CONVERTABLE = Convertable.a(path);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            try {
                FileUtils.deleteDirectory(UNIVERSE_DIR);
            } catch (IOException ex) {
                LOGGER.log(Level.FATAL, "Failed to delete temp directory", ex);
            }

        }));
    }

    private final byte worldVersion = 0x06;

    private boolean loadingDefaultWorlds = true; // If true, the addWorld method will not be skipped

    private CustomWorldServer defaultWorld;
    private CustomWorldServer defaultNetherWorld;
    private CustomWorldServer defaultEndWorld;

    public v11601SlimeNMS(boolean isPaper) {
        isPaperMC = isPaper;
        try {
            CraftCLSMBridge.initialize(this);
        } catch (NoClassDefFoundError ex) {
            LOGGER.error("Failed to find ClassModifier classes. Are you sure you installed it correctly?");
            System.exit(1); // No ClassModifier, no party
        }
    }

    @Override
    public void setDefaultWorlds(SlimeWorld normalWorld, SlimeWorld netherWorld, SlimeWorld endWorld) {
        if (normalWorld != null) {
            defaultWorld = createDefaultWorld(normalWorld, WorldDimension.OVERWORLD, net.minecraft.server.v1_16_R1.World.OVERWORLD, ResourceKey.a(IRegistry.ad, new MinecraftKey(normalWorld.getName().toLowerCase(Locale.ENGLISH))));
        }

        if (netherWorld != null) {
            defaultNetherWorld = createDefaultWorld(netherWorld, WorldDimension.THE_NETHER, net.minecraft.server.v1_16_R1.World.THE_NETHER, ResourceKey.a(IRegistry.ad, new MinecraftKey(netherWorld.getName().toLowerCase(Locale.ENGLISH))));
        }

        if (endWorld != null) {
            defaultEndWorld = createDefaultWorld(endWorld, WorldDimension.THE_END, net.minecraft.server.v1_16_R1.World.THE_END, ResourceKey.a(IRegistry.ad, new MinecraftKey(endWorld.getName().toLowerCase(Locale.ENGLISH))));
        }

        loadingDefaultWorlds = false;
    }

    private CustomWorldServer createDefaultWorld(SlimeWorld world, ResourceKey<WorldDimension> dimensionKey, ResourceKey<net.minecraft.server.v1_16_R1.World> worldKey, ResourceKey<net.minecraft.server.v1_16_R1.DimensionManager> dmKey) {
        WorldDataServer worldDataServer = createWorldData(world);

        RegistryMaterials<WorldDimension> registryMaterials = worldDataServer.getGeneratorSettings().e();
        WorldDimension worldDimension = registryMaterials.a(dimensionKey);
        DimensionManager dimensionManager = worldDimension.b();
        ChunkGenerator chunkGenerator = worldDimension.c();

        World.Environment environment = getEnvironment(world);

        if (dimensionKey == WorldDimension.OVERWORLD && environment != World.Environment.NORMAL) {
            LOGGER.warn("The environment for the default world should always be 'NORMAL'.");
        }

        try {
            return new CustomWorldServer((CraftSlimeWorld) world, worldDataServer, worldKey, dimensionKey, dmKey, dimensionManager, chunkGenerator, environment);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO do something better with this?
        }
    }

    @Override
    public void generateWorld(SlimeWorld world) {
        String worldName = world.getName();

        if (Bukkit.getWorld(worldName) != null) {
            throw new IllegalArgumentException("World " + worldName + " already exists! Maybe it's an outdated SlimeWorld object?");
        }

        WorldDataServer worldDataServer = createWorldData(world);
        World.Environment environment = getEnvironment(world);
        ResourceKey<WorldDimension> dimension;

        switch(environment) {
            case NORMAL:
                dimension = WorldDimension.OVERWORLD;
                break;
            case NETHER:
                dimension = WorldDimension.THE_NETHER;
                break;
            case THE_END:
                dimension = WorldDimension.THE_END;
                break;
            default:
                throw new IllegalArgumentException("Unknown dimension supplied");
        }

        RegistryMaterials<WorldDimension> materials = worldDataServer.getGeneratorSettings().e();
        WorldDimension worldDimension = materials.a(WorldDimension.OVERWORLD);
        DimensionManager dimensionManager = worldDimension.b();
        ChunkGenerator chunkGenerator = worldDimension.c();

        ResourceKey<net.minecraft.server.v1_16_R1.World> worldKey = ResourceKey.a(IRegistry.ae,
            new MinecraftKey(worldName.toLowerCase(Locale.ENGLISH)));
        ResourceKey<net.minecraft.server.v1_16_R1.DimensionManager> dmKey = ResourceKey.a(IRegistry.ad,
            new MinecraftKey(worldName.toLowerCase(Locale.ENGLISH)));

        CustomWorldServer server;

        try {
            server = new CustomWorldServer((CraftSlimeWorld) world, worldDataServer,
                worldKey, dimension, dmKey, dimensionManager, chunkGenerator, environment);
        } catch (IOException ex) {
            throw new RuntimeException(ex); // TODO do something better with this?
        }

        EnderDragonBattle dragonBattle = server.getDragonBattle();
        boolean runBattle = world.getPropertyMap().getValue(SlimeProperties.DRAGON_BATTLE);

        if(dragonBattle != null && !runBattle) {
            dragonBattle.bossBattle.setVisible(false);

            try {
                Field battleField = WorldServer.class.getDeclaredField("dragonBattle");
                battleField.setAccessible(true);
                battleField.set(server, null);
            } catch(NoSuchFieldException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        LOGGER.info("Loading world " + worldName);
        long startTime = System.currentTimeMillis();

        server.setReady(true);

        MinecraftServer mcServer = MinecraftServer.getServer();
        mcServer.initWorld(server, worldDataServer, mcServer.getSaveData(), worldDataServer.getGeneratorSettings());

        mcServer.server.addWorld(server.getWorld());
        mcServer.worldServer.put(worldKey, server);

        WorldLoadListener worldloadlistener = server.getChunkProvider().playerChunkMap.worldLoadListener;
        WorldServer worldserver = server;

        Bukkit.getPluginManager().callEvent(new WorldInitEvent(server.getWorld()));

        if(isPaperMC) {
            if(worldserver.getWorld().getKeepSpawnInMemory()) {
                LOGGER.info("Preparing start region for dimension {}", worldserver.getDimensionKey().a());
                BlockPosition blockposition = worldserver.getSpawn();
                worldloadlistener.a(new ChunkCoordIntPair(blockposition));
                ChunkProviderServer chunkproviderserver = worldserver.getChunkProvider();
                chunkproviderserver.getLightEngine().a(500);
                server.getWorld().getChunkAtAsync(blockposition.getX(), blockposition.getZ());
                WorldServer worldserver1 = worldserver;
                ForcedChunk forcedchunk = (ForcedChunk) worldserver.getWorldPersistentData().b(ForcedChunk::new, "chunks");
                if(forcedchunk != null) {
                    LongIterator longiterator = forcedchunk.a().iterator();

                    while(longiterator.hasNext()) {
                        long i = longiterator.nextLong();
                        ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(i);
                        worldserver1.getChunkProvider().a(chunkcoordintpair, true);
                    }
                }

                worldloadlistener.b();
                chunkproviderserver.getLightEngine().a(5);
                worldserver.setSpawnFlags(
                    world.getPropertyMap().getValue(SlimeProperties.ALLOW_MONSTERS),
                    world.getPropertyMap().getValue(SlimeProperties.ALLOW_ANIMALS)
                );
            }
        }else{
            mcServer.loadSpawn(server.getChunkProvider().playerChunkMap.worldLoadListener, server);
        }

        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(server.getWorld()));

        LOGGER.info("World " + worldName + " loaded in " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    private World.Environment getEnvironment(SlimeWorld world) {
        return World.Environment.valueOf(world.getPropertyMap().getValue(SlimeProperties.ENVIRONMENT).toUpperCase());
    }

    private WorldDataServer createWorldData(SlimeWorld world) {
        String worldName = world.getName();
        CompoundTag extraData = world.getExtraData();
        WorldDataServer worldDataServer;
        NBTTagCompound extraTag = (NBTTagCompound) Converter.convertTag(extraData);
        MinecraftServer mcServer = MinecraftServer.getServer();
        DedicatedServerProperties serverProps = ((DedicatedServer) mcServer).getDedicatedServerProperties();

        if (extraTag.hasKeyOfType("LevelData", CraftMagicNumbers.NBT.TAG_COMPOUND)) {
            NBTTagCompound levelData = extraTag.getCompound("LevelData");
            int dataVersion = levelData.hasKeyOfType("DataVersion", 99) ? levelData.getInt("DataVersion") : -1;
            Dynamic<NBTBase> dynamic = mcServer.getDataFixer().update(DataFixTypes.LEVEL.a(),
                    new Dynamic<>(DynamicOpsNBT.a, levelData), dataVersion, SharedConstants.getGameVersion()
                            .getWorldVersion());

            Lifecycle lifecycle = Lifecycle.stable();
            LevelVersion levelVersion = LevelVersion.a(dynamic);
            WorldSettings worldSettings = WorldSettings.a(dynamic, mcServer.datapackconfiguration);

            worldDataServer = WorldDataServer.a(dynamic, mcServer.getDataFixer(), dataVersion, null,
                    worldSettings, levelVersion, serverProps.generatorSettings, lifecycle);
        } else {
            WorldSettings worldSettings = new WorldSettings(worldName, serverProps.gamemode, false,
                serverProps.difficulty, false, new GameRules(), mcServer.datapackconfiguration);

            // Game rules
            Optional<CompoundTag> gameRules = extraData.getAsCompoundTag("gamerules");

            gameRules.ifPresent(compoundTag -> {
                NBTTagCompound compound = (NBTTagCompound) Converter.convertTag(compoundTag);
                Map<String, GameRuleKey<?>> gameRuleKeys = CraftWorld.getGameRulesNMS();
                GameRules rules = worldSettings.getGameRules();

                compound.getKeys().forEach(gameRule -> {
                    if(gameRuleKeys.containsKey(gameRule)) {
                        GameRuleValue<?> gameRuleValue = rules.get(gameRuleKeys.get(gameRule));
                        String theValue = compound.getString(gameRule);
                        gameRuleValue.setValue(theValue);
                        gameRuleValue.onChange(mcServer);
                    }
                });
            });

            worldDataServer = new WorldDataServer(worldSettings, serverProps.generatorSettings, Lifecycle.stable());
        }

        worldDataServer.checkName(worldName);
        worldDataServer.a(mcServer.getServerModName(), mcServer.getModded().isPresent());
        worldDataServer.c(true);

        return worldDataServer;
    }

    @Override
    public SlimeWorld getSlimeWorld(World world) {
        CraftWorld craftWorld = (CraftWorld) world;

        if (!(craftWorld.getHandle() instanceof CustomWorldServer)) {
            return null;
        }

        CustomWorldServer worldServer = (CustomWorldServer) craftWorld.getHandle();
        return worldServer.getSlimeWorld();
    }

    @Override
    public CompoundTag convertChunk(CompoundTag tag) {
        NBTTagCompound nmsTag = (NBTTagCompound) Converter.convertTag(tag);
        int version = nmsTag.getInt("DataVersion");

        NBTTagCompound newNmsTag = GameProfileSerializer.a(DataConverterRegistry.a(), DataFixTypes.CHUNK, nmsTag, version);

        return (CompoundTag) Converter.convertTag("", newNmsTag);
    }
}