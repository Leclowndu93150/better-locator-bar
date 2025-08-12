package com.leclowndu93150.better_locator_bar;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LodestoneColorRegistry {
    private static final String DATA_FILE = "lodestone_colors.json";
    private static LodestoneColorRegistry INSTANCE;
    
    // Map of lodestone position to assigned color
    private final Map<GlobalPos, Integer> lodestoneColors = new ConcurrentHashMap<>();
    
    // Available colors for lodestones (vibrant colors that work well as waypoint indicators)
    private static final List<Integer> AVAILABLE_COLORS = Arrays.asList(
        0xFF4444, // Red
        0x44FF44, // Green
        0x4444FF, // Blue
        0xFFFF44, // Yellow
        0xFF44FF, // Magenta
        0x44FFFF, // Cyan
        0xFF8844, // Orange
        0x8844FF, // Purple
        0x44FF88, // Light Green
        0xFF4488, // Pink
        0x88FF44, // Lime
        0x4488FF, // Light Blue
        0xFF8888, // Light Red
        0x88FF88, // Pale Green
        0x8888FF, // Pale Blue
        0xFFAA44, // Gold
        0xAA44FF, // Violet
        0x44AAFF, // Sky Blue
        0xFFAA88, // Peach
        0xAAFFAA  // Mint
    );
    
    private final Random colorRandom = new Random();
    
    private final Gson gson = new Gson();
    private Path dataFile;
    
    private LodestoneColorRegistry() {}
    
    public static LodestoneColorRegistry get(MinecraftServer server) {
        if (INSTANCE == null) {
            INSTANCE = new LodestoneColorRegistry();
            INSTANCE.dataFile = server.getServerDirectory().resolve(DATA_FILE);
            INSTANCE.load();
        }
        return INSTANCE;
    }
    
    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        
        try {
            String json = Files.readString(dataFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            if (root.has("lodestones")) {
                JsonObject lodestones = root.getAsJsonObject("lodestones");
                
                for (String key : lodestones.keySet()) {
                    try {
                        String[] parts = key.split("\\|");
                        if (parts.length == 4) {
                            String dimension = parts[0];
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            int z = Integer.parseInt(parts[3]);
                            int color = lodestones.get(key).getAsInt();
                            
                            net.minecraft.resources.ResourceLocation dimensionLoc = net.minecraft.resources.ResourceLocation.parse(dimension);
                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> levelKey = 
                                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionLoc);
                            GlobalPos pos = GlobalPos.of(levelKey, new net.minecraft.core.BlockPos(x, y, z));
                            
                            lodestoneColors.put(pos, color);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse lodestone entry: " + key);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load lodestone colors: " + e.getMessage());
        }
    }
    
    private void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject lodestones = new JsonObject();
            
            for (Map.Entry<GlobalPos, Integer> entry : lodestoneColors.entrySet()) {
                GlobalPos pos = entry.getKey();
                String key = pos.dimension().location().toString() + "|" + 
                           pos.pos().getX() + "|" + pos.pos().getY() + "|" + pos.pos().getZ();
                lodestones.addProperty(key, entry.getValue());
            }
            
            root.add("lodestones", lodestones);
            
            Files.writeString(dataFile, gson.toJson(root));
        } catch (Exception e) {
            System.err.println("Failed to save lodestone colors: " + e.getMessage());
        }
    }
    
    /**
     * Assigns a color to a lodestone when it's placed.
     * If the lodestone already has a color, returns the existing one.
     */
    public int assignColorToLodestone(GlobalPos pos) {
        Integer existingColor = lodestoneColors.get(pos);
        if (existingColor != null) {
            return existingColor;
        }
        
        // Choose a random color that's not heavily used nearby
        int chosenColor = selectBestColor(pos);
        lodestoneColors.put(pos, chosenColor);
        save();
        
        return chosenColor;
    }
    
    /**
     * Gets the color assigned to a lodestone, or null if none assigned.
     */
    public Integer getLodestoneColor(GlobalPos pos) {
        return lodestoneColors.get(pos);
    }
    
    /**
     * Removes the color assignment when a lodestone is broken.
     */
    public void removeLodestone(GlobalPos pos) {
        if (lodestoneColors.remove(pos) != null) {
            save();
        }
    }
    
    /**
     * Selects the best color for a new lodestone by trying to avoid colors
     * that are already used by nearby lodestones in the same dimension.
     */
    private int selectBestColor(GlobalPos newPos) {
        // Count usage of each color in the same dimension within 1000 blocks
        Map<Integer, Integer> colorUsage = new HashMap<>();
        
        for (Map.Entry<GlobalPos, Integer> entry : lodestoneColors.entrySet()) {
            GlobalPos existingPos = entry.getKey();
            
            // Only consider lodestones in the same dimension
            if (existingPos.dimension().equals(newPos.dimension())) {
                double distance = newPos.pos().distSqr(existingPos.pos());
                
                // Weight colors based on distance (closer = more weight against using that color)
                if (distance < 1000000) { // Within 1000 blocks
                    int weight = (int) Math.max(1, 10 - (distance / 100000)); // Closer = higher weight
                    colorUsage.merge(entry.getValue(), weight, Integer::sum);
                }
            }
        }
        
        // Find the least used color
        int bestColor = AVAILABLE_COLORS.get(colorRandom.nextInt(AVAILABLE_COLORS.size()));
        int lowestUsage = Integer.MAX_VALUE;
        
        for (int color : AVAILABLE_COLORS) {
            int usage = colorUsage.getOrDefault(color, 0);
            if (usage < lowestUsage) {
                lowestUsage = usage;
                bestColor = color;
            }
        }
        
        return bestColor;
    }
    
    /**
     * Gets all registered lodestone positions (for debugging/admin purposes).
     */
    public Set<GlobalPos> getAllLodestones() {
        return new HashSet<>(lodestoneColors.keySet());
    }
    
    /**
     * Gets the total number of registered lodestones.
     */
    public int getLodestoneCount() {
        return lodestoneColors.size();
    }
}