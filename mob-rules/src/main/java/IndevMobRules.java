import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class IndevMobRules extends JavaPlugin implements Listener {
    private static final int WIDTH = 256;
    private static final int HEIGHT = 64;
    private static final int LENGTH = 256;
    private static final int HOSTILE_CAP = WIDTH * LENGTH * HEIGHT * 20 / 64 / 64 / 64 / 2;
    private static final int PASSIVE_CAP = WIDTH * LENGTH / 4000;
    private static final double MIN_PLAYER_DISTANCE_SQUARED = 32.0D * 32.0D;

    private final Random random = new Random();
    private boolean pluginSpawn;
    private int taskId = -1;

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        taskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                tickIndevSpawners();
            }
        }, 1L, 1L);
        System.out.println("[IndevMobRules] Original 2010-02-23 finite-level mob rules enabled.");
    }

    public void onDisable() {
        if (taskId != -1) getServer().getScheduler().cancelTask(taskId);
    }

    @EventHandler(priority = Priority.Highest, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isIndevWorld(event.getLocation().getWorld()) || pluginSpawn) return;
        CreatureType type = event.getCreatureType();
        if (event.getSpawnReason() == SpawnReason.NATURAL
                || type == CreatureType.PIG_ZOMBIE || type == CreatureType.MONSTER) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = Priority.Highest, ignoreCancelled = false)
    public void onBlockForm(BlockFormEvent event) {
        if (!isIndevWorld(event.getBlock().getWorld())) return;
        Material formed = event.getNewState().getType();
        if (formed == Material.ICE || formed == Material.SNOW) event.setCancelled(true);
    }

    private void tickIndevSpawners() {
        List<World> worlds = getServer().getWorlds();
        for (int i = 0; i < worlds.size(); i++) {
            World world = worlds.get(i);
            if (!isIndevWorld(world) || world.getPlayers().isEmpty()) continue;
            spawnCategory(world, true);
            spawnCategory(world, false);
        }
    }

    private boolean isIndevWorld(World world) {
        return world != null && world.getName().toLowerCase().startsWith("indev");
    }

    private void spawnCategory(World world, boolean hostile) {
        int count = countCategory(world, hostile);
        int cap = hostile ? HOSTILE_CAP : PASSIVE_CAP;
        for (int pass = 0; pass < 4 && count < cap; pass++) {
            CreatureType type = hostile ? chooseHostile() : choosePassive();
            if (type == null) continue;

            int x = random.nextInt(WIDTH) - WIDTH / 2;
            int y = (int)(Math.min(random.nextFloat(), random.nextFloat()) * HEIGHT);
            int z = random.nextInt(LENGTH) - LENGTH / 2;

            for (int cluster = 0; cluster < 2 && count < cap; cluster++) {
                int cx = x;
                int cy = y;
                int cz = z;
                for (int attempt = 0; attempt < 3 && count < cap; attempt++) {
                    cx += random.nextInt(6) - random.nextInt(6);
                    // The original nextInt(1)-nextInt(1) keeps a group on one Y level.
                    cz += random.nextInt(6) - random.nextInt(6);
                    if (!insideFiniteLevel(cx, cy, cz)) continue;
                    Location location = new Location(world, cx + 0.5D, cy + 0.5D, cz + 0.5D);
                    if (nearAnyPlayer(location, world.getPlayers())) continue;
                    if (!validSpawn(location, hostile)) continue;
                    if (spawn(world, location, type)) count++;
                }
            }
        }
    }

    private int countCategory(World world, boolean hostile) {
        int count = 0;
        List<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (hostile ? entity instanceof Monster : entity instanceof Animals) count++;
        }
        return count;
    }

    private CreatureType chooseHostile() {
        switch (random.nextInt(5)) {
            case 0: return CreatureType.SKELETON;
            case 1: return CreatureType.CREEPER;
            case 2: return CreatureType.SPIDER;
            case 3: return CreatureType.ZOMBIE;
            default: return null;
        }
    }

    private CreatureType choosePassive() {
        return random.nextBoolean() ? CreatureType.PIG : CreatureType.SHEEP;
    }

    private boolean insideFiniteLevel(int x, int y, int z) {
        return x >= -WIDTH / 2 && x < WIDTH / 2 && z >= -LENGTH / 2 && z < LENGTH / 2
                && y >= 1 && y < HEIGHT - 2;
    }

    private boolean nearAnyPlayer(Location location, List<Player> players) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getLocation().distanceSquared(location) < MIN_PLAYER_DISTANCE_SQUARED) return true;
        }
        return false;
    }

    private boolean validSpawn(Location location, boolean hostile) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        if (feet.getType() != Material.AIR || head.getType() != Material.AIR || !isSolidFloor(floor.getType())) {
            return false;
        }
        if (hostile) return feet.getLightLevel() <= random.nextInt(8);
        return floor.getType() == Material.GRASS && feet.getLightLevel() > 8;
    }

    private boolean isSolidFloor(Material material) {
        return material != null && material != Material.AIR && material != Material.WATER
                && material != Material.STATIONARY_WATER && material != Material.LAVA
                && material != Material.STATIONARY_LAVA;
    }

    private boolean spawn(World world, Location location, CreatureType type) {
        pluginSpawn = true;
        try {
            return world.spawnCreature(location, type) != null;
        } catch (Throwable failure) {
            return false;
        } finally {
            pluginSpawn = false;
        }
    }
}
