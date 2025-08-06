package com.leclowndu93150.better_locator_bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.waypoints.Waypoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LodestoneCompassTracker {
    // Thread-safe storage for player waypoints and tick counters
    private static final Map<UUID, Map<GlobalPos, LodestoneWaypointTransmitter>> playerWaypoints = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerTickCounters = new ConcurrentHashMap<>();
    
    // Cache for tracking which compasses have already been cleaned
    private static final Map<UUID, Set<GlobalPos>> cleanedCompasses = new ConcurrentHashMap<>();

    public void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        UUID playerId = serverPlayer.getUUID();
        int tickCounter = playerTickCounters.getOrDefault(playerId, 0);
        
        // Check every 20 ticks (1 second)
        if (++tickCounter >= 20) {
            playerTickCounters.put(playerId, 0);
            processPlayerCompasses(serverPlayer);
        } else {
            playerTickCounters.put(playerId, tickCounter);
        }
    }

    public void onPlayerJoin(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        UUID playerId = serverPlayer.getUUID();
        
        // Clear any cached cleaned compass data for this player
        cleanedCompasses.remove(playerId);
        
        // Immediately process compasses when player joins
        processPlayerCompasses(serverPlayer);
        
        // Reset tick counter for this player
        playerTickCounters.put(playerId, 0);
    }
    
    public void onPlayerLeave(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        UUID playerId = serverPlayer.getUUID();
        
        // Remove all waypoints for this player
        removeAllPlayerWaypoints(serverPlayer);
        
        // Clean up tracking data
        playerTickCounters.remove(playerId);
        cleanedCompasses.remove(playerId);
    }

    private void processPlayerCompasses(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        // Get current compass positions from inventory
        Set<GlobalPos> currentCompassPositions = new HashSet<>();
        List<ItemStack> compasses = findAllLodestoneCompasses(player);
        
        // Process each compass
        for (ItemStack compass : compasses) {
            LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()) {
                GlobalPos targetPos = tracker.target().get();
                
                // Verify lodestone exists
                if (verifyLodestone(player.getServer(), targetPos)) {
                    // Lodestone exists - track it
                    currentCompassPositions.add(targetPos);
                    
                    // Add coordinates to compass
                    addCoordinatesToCompass(compass, targetPos.pos());
                    
                    // Clear from cleaned cache since it's valid again
                    Set<GlobalPos> cleaned = cleanedCompasses.get(playerId);
                    if (cleaned != null) {
                        cleaned.remove(targetPos);
                    }
                } else {
                    // Lodestone doesn't exist - clean up the compass
                    cleanupCompass(compass, playerId, targetPos);
                }
            }
        }
        
        // Update waypoints based on current compass positions
        updatePlayerWaypoints(player, currentCompassPositions);
    }

    private boolean verifyLodestone(net.minecraft.server.MinecraftServer server, GlobalPos targetPos) {
        ServerLevel targetLevel = server.getLevel(targetPos.dimension());
        if (targetLevel == null) return false;
        
        return targetLevel.getBlockState(targetPos.pos()).is(Blocks.LODESTONE);
    }

    private void cleanupCompass(ItemStack compass, UUID playerId, GlobalPos targetPos) {
        // Check if we've already cleaned this compass for this player
        Set<GlobalPos> cleaned = cleanedCompasses.computeIfAbsent(playerId, k -> new HashSet<>());
        
        if (!cleaned.contains(targetPos)) {
            // First time cleaning this compass - remove data
            compass.remove(DataComponents.LODESTONE_TRACKER);
            removeLodestoneCoordinates(compass);
            cleaned.add(targetPos);
        }
    }

    private void updatePlayerWaypoints(ServerPlayer player, Set<GlobalPos> currentPositions) {
        UUID playerId = player.getUUID();
        Map<GlobalPos, LodestoneWaypointTransmitter> existingWaypoints = playerWaypoints.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Map<GlobalPos, LodestoneWaypointTransmitter> newWaypoints = new HashMap<>();
        
        // Add or keep waypoints for current compass positions
        for (GlobalPos pos : currentPositions) {
            LodestoneWaypointTransmitter waypoint = existingWaypoints.get(pos);
            
            if (waypoint != null) {
                // Reuse existing waypoint
                newWaypoints.put(pos, waypoint);
            } else {
                // Create new waypoint
                waypoint = createWaypoint(player, pos);
                if (waypoint != null) {
                    newWaypoints.put(pos, waypoint);
                }
            }
        }
        
        // Remove waypoints that are no longer needed
        for (Map.Entry<GlobalPos, LodestoneWaypointTransmitter> entry : existingWaypoints.entrySet()) {
            if (!newWaypoints.containsKey(entry.getKey())) {
                removeWaypoint(player.level(), entry.getValue());
            }
        }
        
        // Update the stored waypoints
        if (newWaypoints.isEmpty()) {
            playerWaypoints.remove(playerId);
        } else {
            playerWaypoints.put(playerId, newWaypoints);
        }
    }

    private LodestoneWaypointTransmitter createWaypoint(ServerPlayer player, GlobalPos targetPos) {
        try {
            // Find the compass for this position to get its icon
            ItemStack compass = findCompassForPosition(player, targetPos);
            if (compass == null) return null;
            
            Waypoint.Icon icon = createCompassIcon(compass);
            UUID waypointId = UUID.nameUUIDFromBytes((LodestoneWaypointStyles.LODESTONE_UUID_PREFIX + player.getUUID() + "_" + targetPos.toString()).getBytes());
            
            LodestoneWaypointTransmitter waypoint = new LodestoneWaypointTransmitter(
                    waypointId, targetPos.pos(), icon, 60000000, player.getUUID()
            );
            
            ServerLevel level = player.level();
            level.getWaypointManager().trackWaypoint(waypoint);
            
            return waypoint;
        } catch (Exception e) {
            // Failed to create waypoint
            return null;
        }
    }

    private void removeWaypoint(ServerLevel level, LodestoneWaypointTransmitter waypoint) {
        try {
            level.getWaypointManager().untrackWaypoint(waypoint);
        } catch (Exception ignored) {
            // Silently handle errors
        }
    }

    private void removeAllPlayerWaypoints(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Map<GlobalPos, LodestoneWaypointTransmitter> waypoints = playerWaypoints.remove(playerId);
        
        if (waypoints != null) {
            ServerLevel level = player.level();
            for (LodestoneWaypointTransmitter waypoint : waypoints.values()) {
                removeWaypoint(level, waypoint);
            }
        }
    }

    private ItemStack findCompassForPosition(ServerPlayer player, GlobalPos targetPos) {
        List<ItemStack> compasses = findAllLodestoneCompasses(player);
        
        for (ItemStack compass : compasses) {
            LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent() && tracker.target().get().equals(targetPos)) {
                return compass;
            }
        }
        
        return null;
    }

    private List<ItemStack> findAllLodestoneCompasses(ServerPlayer player) {
        List<ItemStack> compasses = new ArrayList<>();
        
        // Check main hand
        ItemStack mainHand = player.getMainHandItem();
        if (isLodestoneCompass(mainHand)) {
            compasses.add(mainHand);
        }
        
        // Check off hand
        ItemStack offHand = player.getOffhandItem();
        if (isLodestoneCompass(offHand) && !compasses.contains(offHand)) {
            compasses.add(offHand);
        }
        
        // Check inventory slots (0-35: hotbar and main inventory)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isLodestoneCompass(stack) && !compasses.contains(stack)) {
                compasses.add(stack);
            }
        }
        
        return compasses;
    }

    private boolean isLodestoneCompass(ItemStack stack) {
        return stack.is(Items.COMPASS) && stack.has(DataComponents.LODESTONE_TRACKER);
    }

    private Waypoint.Icon createCompassIcon(ItemStack compass) {
        Waypoint.Icon icon = new Waypoint.Icon();
        icon.style = LodestoneWaypointStyles.LODESTONE;

        // Check for TCC dyed compass colors
        if (compass.has(DataComponents.ITEM_MODEL)) {
            String itemModel = compass.get(DataComponents.ITEM_MODEL).toString();
            if (itemModel.contains("tcc:dyed_compass/")) {
                String colorName = itemModel.substring(itemModel.lastIndexOf("/") + 1);
                Optional<Integer> color = getTCCCompassColor(colorName);
                if (color.isPresent()) {
                    icon.color = color;
                }
            }
        }

        return icon;
    }

    private Optional<Integer> getTCCCompassColor(String colorName) {
        return switch (colorName) {
            case "white" -> Optional.of(0xF9FFFE);
            case "light_gray" -> Optional.of(0x9D9D97);
            case "gray" -> Optional.of(0x474F52);
            case "black" -> Optional.of(0x1D1D21);
            case "brown" -> Optional.of(0x835432);
            case "red" -> Optional.of(0xB02E26);
            case "orange" -> Optional.of(0xF9801D);
            case "yellow" -> Optional.of(0xFED83D);
            case "lime" -> Optional.of(0x80C71F);
            case "green" -> Optional.of(0x5E7C16);
            case "cyan" -> Optional.of(0x169C9C);
            case "light_blue" -> Optional.of(0x3AB3DA);
            case "blue" -> Optional.of(0x3C44AA);
            case "purple" -> Optional.of(0x8932B8);
            case "magenta" -> Optional.of(0xC74EBD);
            case "pink" -> Optional.of(0xF38BAA);
            default -> Optional.empty();
        };
    }

    private void addCoordinatesToCompass(ItemStack compass, BlockPos pos) {
        List<Component> loreList = new ArrayList<>();

        // Get existing lore if present
        if (compass.has(DataComponents.LORE)) {
            ItemLore existingLore = compass.get(DataComponents.LORE);
            loreList.addAll(existingLore.lines());
        }

        // Create coordinate component
        Component coordComponent = Component.literal("Lodestone: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(style -> style.withColor(0x55FFFF));

        // Remove any existing lodestone coordinate lines
        loreList.removeIf(line -> line.getString().startsWith("Lodestone: "));
        
        // Add new coordinate line
        loreList.add(coordComponent);

        // Update compass lore
        compass.set(DataComponents.LORE, new ItemLore(loreList));
    }

    private void removeLodestoneCoordinates(ItemStack compass) {
        if (compass.has(DataComponents.LORE)) {
            ItemLore existingLore = compass.get(DataComponents.LORE);
            List<Component> loreList = new ArrayList<>(existingLore.lines());
            
            // Remove any lodestone coordinate lines
            loreList.removeIf(line -> line.getString().startsWith("Lodestone: "));
            
            if (loreList.isEmpty()) {
                // Remove lore component entirely if no lore remains
                compass.remove(DataComponents.LORE);
            } else {
                compass.set(DataComponents.LORE, new ItemLore(loreList));
            }
        }
    }
}