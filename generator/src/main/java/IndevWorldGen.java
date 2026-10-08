import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class IndevWorldGen extends JavaPlugin {
    private static final int MAGIC = 0x494E4456;
    private static final int FORMAT_VERSION = 2;
    private static final int FINITE_BORDER_WIDTH = 2048;
    private final Map<String, LevelData> loaded = Collections.synchronizedMap(new HashMap<String, LevelData>());

    public void onEnable() {
        getDataFolder().mkdirs();
        System.out.println("[IndevWorldGen] Original 2010-02-23 Indev generator adapter enabled.");
    }

    public void onDisable() {
        loaded.clear();
    }

    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        Preset preset = Preset.parse(id);
        System.out.println("[IndevWorldGen] " + worldName + " uses " + preset.type + "/" + preset.theme
                + " at " + preset.width + "x" + preset.height + "x" + preset.length + ".");
        return new IndevChunkGenerator(this, worldName, preset);
    }

    private LevelData level(World world, String worldName, Preset preset) {
        String key = worldName.toLowerCase(Locale.ENGLISH) + ":" + world.getSeed() + ":" + preset.key();
        LevelData data = loaded.get(key);
        if (data != null) return data;
        synchronized (loaded) {
            data = loaded.get(key);
            if (data != null) return data;
            try {
                data = generateOrLoad(world, worldName, preset);
                loaded.put(key, data);
                return data;
            } catch (Exception ex) {
                throw new RuntimeException("Could not prepare original Indev terrain for " + worldName, ex);
            }
        }
    }

    private LevelData generateOrLoad(World world, String worldName, Preset preset) throws Exception {
        File jar = new File(getDataFolder(), "in-20100223.jar");
        File bridge = new File(getDataFolder(), "IndevGeneratorBridge.jar");
        if (!jar.isFile() || !bridge.isFile()) {
            throw new IOException("Missing in-20100223.jar or IndevGeneratorBridge.jar in " + getDataFolder());
        }
        File cache = new File(getDataFolder(), "cache");
        cache.mkdirs();
        File output = new File(cache, safe(worldName) + "-" + world.getSeed() + "-" + preset.key() + ".bin");
        if (!output.isFile()) {
            File temporary = new File(output.getPath() + ".tmp");
            ProcessBuilder builder = new ProcessBuilder(
                    javaExecutable(), "-cp", bridge.getAbsolutePath(), "IndevGeneratorBridge",
                    jar.getAbsolutePath(), temporary.getAbsolutePath(),
                    String.valueOf(preset.width), String.valueOf(preset.length), String.valueOf(preset.height),
                    String.valueOf(preset.typeId()), String.valueOf(preset.themeId()),
                    String.valueOf(world.getSeed()), worldName);
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(cache, safe(worldName) + "-bridge.log"));
            Process process = builder.start();
            int result = process.waitFor();
            if (result != 0 || !temporary.isFile()) {
                throw new IOException("Indev bridge exited " + result + "; see cache bridge log");
            }
            if (!temporary.renameTo(output)) {
                throw new IOException("Could not commit generated cache " + output);
            }
        }
        LevelData result = LevelData.read(output);
        if (result.width != preset.width || result.height != preset.height || result.length != preset.length) {
            throw new IOException("Cached Indev dimensions do not match preset");
        }
        System.out.println("[IndevWorldGen] Loaded faithful Indev terrain for " + worldName + " from " + output.getName());
        return result;
    }

    private static String javaExecutable() {
        String suffix = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("win") ? "java.exe" : "java";
        return new File(new File(System.getProperty("java.home"), "bin"), suffix).getPath();
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static final class IndevChunkGenerator extends ChunkGenerator {
        private final IndevWorldGen plugin;
        private final String worldName;
        private final Preset preset;

        IndevChunkGenerator(IndevWorldGen plugin, String worldName, Preset preset) {
            this.plugin = plugin;
            this.worldName = worldName;
            this.preset = preset;
        }

        public byte[] generate(World world, Random random, int chunkX, int chunkZ) {
            LevelData level = plugin.level(world, worldName, preset);
            int targetHeight = world.getMaxHeight();
            byte[] chunk = new byte[16 * targetHeight * 16];
            for (int localX = 0; localX < 16; localX++) {
                int sourceX = chunkX * 16 + localX + level.width / 2;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int sourceZ = chunkZ * 16 + localZ + level.length / 2;
                    if (sourceX < 0 || sourceX >= level.width || sourceZ < 0 || sourceZ >= level.length) {
                        if (!preset.type.equals("floating") && withinFiniteBorder(sourceX, sourceZ, level)) {
                            int column = (localX * 16 + localZ) * targetHeight;
                            int groundTop = Math.min(Math.max(0, level.groundLevel), targetHeight);
                            for (int y = 0; y < groundTop; y++) {
                                chunk[column + y] = (byte) level.surroundingGroundBlock;
                            }
                            int fluidTop = Math.min(Math.max(0, level.waterLevel), targetHeight);
                            for (int y = groundTop; y < fluidTop; y++) {
                                chunk[column + y] = (byte) level.fluidBlock;
                            }
                        }
                        continue;
                    }
                    int maxY = Math.min(level.height, targetHeight);
                    for (int y = 0; y < maxY; y++) {
                        int source = sourceX + (y * level.length + sourceZ) * level.width;
                        chunk[(localX * 16 + localZ) * targetHeight + y] = level.blocks[source];
                    }
                }
            }
            return chunk;
        }

        private boolean withinFiniteBorder(int sourceX, int sourceZ, LevelData level) {
            int outsideX = sourceX < 0 ? -sourceX : (sourceX >= level.width ? sourceX - level.width + 1 : 0);
            int outsideZ = sourceZ < 0 ? -sourceZ : (sourceZ >= level.length ? sourceZ - level.length + 1 : 0);
            return Math.max(outsideX, outsideZ) <= FINITE_BORDER_WIDTH;
        }

        public Location getFixedSpawnLocation(World world, Random random) {
            LevelData level = plugin.level(world, worldName, preset);
            return new Location(world, level.spawnX - level.width / 2 + 0.5,
                    level.spawnY + 0.5, level.spawnZ - level.length / 2 + 0.5);
        }

        public boolean canSpawn(World world, int x, int z) {
            LevelData level = plugin.level(world, worldName, preset);
            int sx = x + level.width / 2, sz = z + level.length / 2;
            return sx >= 0 && sx < level.width && sz >= 0 && sz < level.length;
        }

        public List<BlockPopulator> getDefaultPopulators(World world) {
            return Collections.<BlockPopulator>singletonList(new MetadataAndLightingPopulator(plugin, worldName, preset));
        }
    }

    private static final class MetadataAndLightingPopulator extends BlockPopulator {
        private final IndevWorldGen plugin;
        private final String worldName;
        private final Preset preset;

        MetadataAndLightingPopulator(IndevWorldGen plugin, String worldName, Preset preset) {
            this.plugin = plugin;
            this.worldName = worldName;
            this.preset = preset;
        }

        public void populate(World world, Random random, Chunk chunk) {
            LevelData level = plugin.level(world, worldName, preset);
            int baseX = chunk.getX() * 16;
            int baseZ = chunk.getZ() * 16;
            for (int localX = 0; localX < 16; localX++) {
                int sourceX = baseX + localX + level.width / 2;
                if (sourceX < 0 || sourceX >= level.width) continue;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int sourceZ = baseZ + localZ + level.length / 2;
                    if (sourceZ < 0 || sourceZ >= level.length) continue;
                    for (int y = 0; y < level.height && y < world.getMaxHeight(); y++) {
                        int source = sourceX + (y * level.length + sourceZ) * level.width;
                        byte data = level.metadata[source];
                        if (data != 0) {
                            Block block = world.getBlockAt(baseX + localX, y, baseZ + localZ);
                            block.setData(data, false);
                        }
                    }
                }
            }
            ((CraftChunk)chunk).getHandle().initLighting();
            world.refreshChunk(chunk.getX(), chunk.getZ());
        }
    }

    private static final class Preset {
        final String type;
        final String theme;
        final int width;
        final int height;
        final int length;

        Preset(String type, String theme, int width, int height, int length) {
            this.type = type;
            this.theme = theme;
            this.width = width;
            this.height = height;
            this.length = length;
        }

        static Preset parse(String id) {
            String[] parts = id == null ? new String[0] : id.toLowerCase(Locale.ENGLISH).split("[,;:]", -1);
            String type = parts.length > 0 && parts[0].length() > 0 ? parts[0] : "inland";
            String theme = parts.length > 1 && parts[1].length() > 0 ? parts[1] : "normal";
            if (type.equals("classic")) type = "inland";
            if (theme.equals("forest")) theme = "woods";
            if (!type.equals("inland") && !type.equals("island") && !type.equals("floating") && !type.equals("flat")) {
                throw new IllegalArgumentException("Unknown Indev type: " + type);
            }
            if (!theme.equals("normal") && !theme.equals("hell") && !theme.equals("paradise") && !theme.equals("woods")) {
                throw new IllegalArgumentException("Unknown Indev theme: " + theme);
            }
            int width = number(parts, 2, 256);
            int height = number(parts, 3, 64);
            int length = number(parts, 4, 256);
            if (width < 64 || width > 1024 || length < 64 || length > 1024 || height < 64 || height > 128) {
                throw new IllegalArgumentException("Indev dimensions out of range");
            }
            return new Preset(type, theme, width, height, length);
        }

        static int number(String[] parts, int index, int fallback) {
            return parts.length > index && parts[index].length() > 0 ? Integer.parseInt(parts[index]) : fallback;
        }

        int typeId() { return type.equals("island") ? 1 : type.equals("floating") ? 2 : type.equals("flat") ? 3 : 0; }
        int themeId() { return theme.equals("hell") ? 1 : theme.equals("paradise") ? 2 : theme.equals("woods") ? 3 : 0; }
        String key() { return type + "-" + theme + "-" + width + "x" + height + "x" + length; }
    }

    private static final class LevelData {
        int width, height, length;
        int spawnX, spawnY, spawnZ;
        int waterLevel;
        int groundLevel;
        int fluidBlock;
        int surroundingGroundBlock;
        byte[] blocks;
        byte[] metadata;

        static LevelData read(File file) throws IOException {
            DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            try {
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) throw new IOException("Invalid Indev cache");
                LevelData value = new LevelData();
                value.width = input.readInt();
                value.height = input.readInt();
                value.length = input.readInt();
                value.spawnX = input.readInt();
                value.spawnY = input.readInt();
                value.spawnZ = input.readInt();
                value.waterLevel = input.readInt();
                value.groundLevel = input.readInt();
                input.readInt(); // cloud height
                input.readInt(); // sky color
                input.readInt(); // fog color
                input.readInt(); // cloud color
                value.fluidBlock = input.readInt();
                input.readInt(); // sky brightness
                value.surroundingGroundBlock = input.readInt();
                int count = input.readInt();
                if (count != value.width * value.height * value.length) throw new IOException("Invalid Indev block count");
                value.blocks = new byte[count];
                input.readFully(value.blocks);
                int metadataCount = input.readInt();
                if (metadataCount != count) throw new IOException("Invalid Indev metadata count");
                value.metadata = new byte[metadataCount];
                input.readFully(value.metadata);
                return value;
            } finally {
                input.close();
            }
        }
    }
}
