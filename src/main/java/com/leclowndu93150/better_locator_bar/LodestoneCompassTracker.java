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
    private static final Map<UUID, Map<GlobalPos, LodestoneWaypointTransmitter>> playerWaypoints = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerTickCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<GlobalPos>> cleanedCompasses = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> delayedProcessing = new ConcurrentHashMap<>();

    public void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        UUID playerId = serverPlayer.getUUID();
        int tickCounter = playerTickCounters.getOrDefault(playerId, 0);
        
        if (delayedProcessing.containsKey(playerId)) {
            int delayCounter = delayedProcessing.get(playerId);
            if (++delayCounter >= 5) {
                delayedProcessing.remove(playerId);
                processPlayerCompasses(serverPlayer);
            } else {
                delayedProcessing.put(playerId, delayCounter);
            }
        }
        
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
        
        cleanedCompasses.remove(playerId);
        playerTickCounters.put(playerId, 0);
        updateAllCompassesOnJoin(serverPlayer);
        delayedProcessing.put(playerId, 0);
        processPlayerCompasses(serverPlayer);
    }
    
    private void updateAllCompassesOnJoin(ServerPlayer player) {
        boolean inventoryChanged = false;
        
        
        ItemStack mainHand = player.getMainHandItem();
        if (isLodestoneCompass(mainHand)) {
            LodestoneTracker tracker = mainHand.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()) {
                GlobalPos targetPos = tracker.target().get();
                boolean lodestoneExists = verifyLodestone(player.getServer(), targetPos);
                ItemStack updated = mainHand.copy();
                updateCompassLore(updated, targetPos, lodestoneExists, player);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, updated);
                inventoryChanged = true;
            }
        }
        
        
        ItemStack offHand = player.getOffhandItem();
        if (isLodestoneCompass(offHand)) {
            LodestoneTracker tracker = offHand.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()) {
                GlobalPos targetPos = tracker.target().get();
                boolean lodestoneExists = verifyLodestone(player.getServer(), targetPos);
                ItemStack updated = offHand.copy();
                updateCompassLore(updated, targetPos, lodestoneExists, player);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, updated);
                inventoryChanged = true;
            }
        }
        
        
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isLodestoneCompass(stack)) {
                LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                if (tracker != null && tracker.target().isPresent()) {
                    GlobalPos targetPos = tracker.target().get();
                    boolean lodestoneExists = verifyLodestone(player.getServer(), targetPos);
                    ItemStack updated = stack.copy();
                    updateCompassLore(updated, targetPos, lodestoneExists, player);
                    player.getInventory().setItem(i, updated);
                    inventoryChanged = true;
                }
            }
        }
        
        
        if (inventoryChanged) {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }
    
    public void onPlayerLeave(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        UUID playerId = serverPlayer.getUUID();
        
        removeAllPlayerWaypoints(serverPlayer);
        playerTickCounters.remove(playerId);
        cleanedCompasses.remove(playerId);
        delayedProcessing.remove(playerId);
    }

    private void processPlayerCompasses(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        Set<GlobalPos> currentCompassPositions = new HashSet<>();
        
        processCompassInSlot(player, -1, currentCompassPositions);
        processCompassInSlot(player, -2, currentCompassPositions);
        
        for (int i = 0; i < 36; i++) {
            processCompassInSlot(player, i, currentCompassPositions);
        }
        
        updatePlayerWaypoints(player, currentCompassPositions);
    }
    
    private void processCompassInSlot(ServerPlayer player, int slot, Set<GlobalPos> currentCompassPositions) {
        ItemStack compass;
        
        if (slot == -1) {
            compass = player.getMainHandItem();
        } else if (slot == -2) {
            compass = player.getOffhandItem();
        } else {
            compass = player.getInventory().getItem(slot);
        }
        
        if (!isLodestoneCompass(compass)) return;
        
        LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null && tracker.target().isPresent()) {
            GlobalPos targetPos = tracker.target().get();
            
            boolean lodestoneExists = verifyLodestone(player.getServer(), targetPos);
            
            ItemStack updatedCompass = compass.copy();
            updateCompassLore(updatedCompass, targetPos, lodestoneExists, player);
            
            if (slot == -1) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, updatedCompass);
            } else if (slot == -2) {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, updatedCompass);
            } else {
                player.getInventory().setItem(slot, updatedCompass);
            }
            
            if (lodestoneExists) {
                currentCompassPositions.add(targetPos);
            }
        }
    }

    private boolean verifyLodestone(net.minecraft.server.MinecraftServer server, GlobalPos targetPos) {
        ServerLevel targetLevel = server.getLevel(targetPos.dimension());
        if (targetLevel == null) return false;
        
        return targetLevel.getBlockState(targetPos.pos()).is(Blocks.LODESTONE);
    }


    private void updatePlayerWaypoints(ServerPlayer player, Set<GlobalPos> currentPositions) {
        UUID playerId = player.getUUID();
        Map<GlobalPos, LodestoneWaypointTransmitter> existingWaypoints = playerWaypoints.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Map<GlobalPos, LodestoneWaypointTransmitter> newWaypoints = new HashMap<>();
        
        for (GlobalPos pos : currentPositions) {
            LodestoneWaypointTransmitter waypoint = existingWaypoints.get(pos);
            
            if (waypoint != null) {
                newWaypoints.put(pos, waypoint);
            } else {
                waypoint = createWaypoint(player, pos);
                if (waypoint != null) {
                    newWaypoints.put(pos, waypoint);
                }
            }
        }
        
        for (Map.Entry<GlobalPos, LodestoneWaypointTransmitter> entry : existingWaypoints.entrySet()) {
            if (!newWaypoints.containsKey(entry.getKey())) {
                removeWaypoint(player.level(), entry.getValue());
            }
        }
        
        if (newWaypoints.isEmpty()) {
            playerWaypoints.remove(playerId);
        } else {
            playerWaypoints.put(playerId, newWaypoints);
        }
    }

    private LodestoneWaypointTransmitter createWaypoint(ServerPlayer player, GlobalPos targetPos) {
        try {
            ItemStack compass = findCompassForPosition(player, targetPos);
            if (compass == null) return null;
            
            Waypoint.Icon icon = createCompassIcon(compass, targetPos, player);
            UUID waypointId = UUID.nameUUIDFromBytes((LodestoneWaypointStyles.LODESTONE_UUID_PREFIX + player.getUUID() + "_" + targetPos.toString()).getBytes());
            
            LodestoneWaypointTransmitter waypoint = new LodestoneWaypointTransmitter(
                    waypointId, targetPos.pos(), icon, 60000000, player.getUUID()
            );
            
            ServerLevel level = player.level();
            level.getWaypointManager().trackWaypoint(waypoint);
            
            return waypoint;
        } catch (Exception e) {
            return null;
        }
    }

    private void removeWaypoint(ServerLevel level, LodestoneWaypointTransmitter waypoint) {
        try {
            level.getWaypointManager().untrackWaypoint(waypoint);
        } catch (Exception ignored) {
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
        
        
        ItemStack mainHand = player.getMainHandItem();
        if (isLodestoneCompass(mainHand)) {
            compasses.add(mainHand);
        }
        
        
        ItemStack offHand = player.getOffhandItem();
        if (isLodestoneCompass(offHand) && !compasses.contains(offHand)) {
            compasses.add(offHand);
        }

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

    private Waypoint.Icon createCompassIcon(ItemStack compass, GlobalPos targetPos, ServerPlayer player) {
        Waypoint.Icon icon = new Waypoint.Icon();
        icon.style = LodestoneWaypointStyles.LODESTONE;

        // PRIORITY 1: Check for TCC dyed compass colors first
        if (compass.has(DataComponents.ITEM_MODEL)) {
            String itemModel = compass.get(DataComponents.ITEM_MODEL).toString();
            if (itemModel.contains("tcc:dyed_compass/")) {
                String colorName = itemModel.substring(itemModel.lastIndexOf("/") + 1);
                Optional<Integer> color = getTCCCompassColor(colorName);
                if (color.isPresent()) {
                    icon.color = color;
                    return icon; // TCC color takes priority, return immediately
                }
            }
        }

        // PRIORITY 2: Use shared lodestone color if no TCC color
        try {
            LodestoneColorRegistry registry = LodestoneColorRegistry.get(player.getServer());
            Integer lodestoneColor = registry.getLodestoneColor(targetPos);
            if (lodestoneColor != null) {
                icon.color = Optional.of(lodestoneColor);
            }
        } catch (Exception e) {
            // Fallback if registry access fails - use default color
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

    private void updateCompassLore(ItemStack compass, GlobalPos targetPos, boolean lodestoneExists, ServerPlayer player) {
        List<Component> loreList = new ArrayList<>();
        BlockPos pos = targetPos.pos();

        // Get existing lore if present
        if (compass.has(DataComponents.LORE)) {
            ItemLore existingLore = compass.get(DataComponents.LORE);
            loreList.addAll(existingLore.lines());
        }

        // Remove any existing lodestone coordinate lines
        loreList.removeIf(line -> {
            String lineText = line.getString();
            return lineText.startsWith("Lodestone: ") || lineText.startsWith("● ") || 
                   lineText.matches("^-?\\d+, -?\\d+, -?\\d+$"); // Also remove plain coordinates
        });
        
        Component coordComponent;
        if (lodestoneExists) {
            // Lodestone exists - show colored circle with coordinates
            int circleColor = getCompassColor(compass, targetPos, player);
            
            coordComponent = Component.literal("")
                    .append(Component.literal("● ").withStyle(style -> style.withColor(circleColor).withItalic(false)))
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(style -> style.withColor(0x999999).withItalic(false)));
        } else {
            // Lodestone broken - show dark gray circle and coordinates
            coordComponent = Component.literal("")
                    .append(Component.literal("● ").withStyle(style -> style.withColor(0x555555).withItalic(false)))
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(style -> style.withColor(0x555555).withItalic(false)));
        }
        
        // Add the coordinate line
        loreList.add(coordComponent);

        // Update compass lore
        compass.set(DataComponents.LORE, new ItemLore(loreList));
    }
    
    private int getCompassColor(ItemStack compass, GlobalPos targetPos, ServerPlayer player) {
        // Check for TCC dyed compass colors first
        if (compass.has(DataComponents.ITEM_MODEL)) {
            String itemModel = compass.get(DataComponents.ITEM_MODEL).toString();
            if (itemModel.contains("tcc:dyed_compass/")) {
                String colorName = itemModel.substring(itemModel.lastIndexOf("/") + 1);
                Optional<Integer> color = getTCCCompassColor(colorName);
                if (color.isPresent()) {
                    return color.get();
                }
            }
        }

        // Use shared lodestone color if no TCC color
        try {
            LodestoneColorRegistry registry = LodestoneColorRegistry.get(player.getServer());
            Integer lodestoneColor = registry.getLodestoneColor(targetPos);
            if (lodestoneColor != null) {
                return lodestoneColor;
            }
        } catch (Exception e) {
            // Fallback if registry access fails
        }

        // Default color
        return 0x55FFFF;
    }
    
    /**
     * Immediately updates all compasses pointing to a broken lodestone.
     * Called when a lodestone block is broken.
     */
    public void updateCompassesForBrokenLodestone(GlobalPos brokenPos, net.minecraft.server.MinecraftServer server) {
        // Loop through all online players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inventoryChanged = false;
            
            
            ItemStack mainHand = player.getMainHandItem();
            if (isCompassPointingTo(mainHand, brokenPos)) {
                ItemStack updated = mainHand.copy();
                updateCompassLore(updated, brokenPos, false, player);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, updated);
                inventoryChanged = true;
            }
            
            
            ItemStack offHand = player.getOffhandItem();
            if (isCompassPointingTo(offHand, brokenPos)) {
                ItemStack updated = offHand.copy();
                updateCompassLore(updated, brokenPos, false, player);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, updated);
                inventoryChanged = true;
            }
            
            
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (isCompassPointingTo(stack, brokenPos)) {
                    ItemStack updated = stack.copy();
                    updateCompassLore(updated, brokenPos, false, player);
                    player.getInventory().setItem(i, updated);
                    inventoryChanged = true;
                }
            }
            
            
            if (inventoryChanged) {
                player.inventoryMenu.sendAllDataToRemote();
            }
        }
    }
    
    private boolean isCompassPointingTo(ItemStack stack, GlobalPos targetPos) {
        if (!isLodestoneCompass(stack)) return false;
        
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null && tracker.target().isPresent()) {
            return tracker.target().get().equals(targetPos);
        }
        return false;
    }
    
    /**
     * Refreshes waypoints for all players when a lodestone is placed or broken.
     * This ensures color changes are immediately visible to all players.
     */
    public void refreshWaypointsForLodestone(GlobalPos lodestonePos, net.minecraft.server.MinecraftServer server) {
        // Find all players that might be tracking this lodestone and refresh their waypoints
        for (UUID playerId : playerWaypoints.keySet()) {
            Map<GlobalPos, LodestoneWaypointTransmitter> waypoints = playerWaypoints.get(playerId);
            if (waypoints != null && waypoints.containsKey(lodestonePos)) {
                // Find the player and refresh their waypoint immediately
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // Remove the old waypoint
                    LodestoneWaypointTransmitter oldWaypoint = waypoints.get(lodestonePos);
                    if (oldWaypoint != null) {
                        removeWaypoint(player.level(), oldWaypoint);
                    }
                    
                    // Create a new waypoint with updated color
                    LodestoneWaypointTransmitter newWaypoint = createWaypoint(player, lodestonePos);
                    if (newWaypoint != null) {
                        waypoints.put(lodestonePos, newWaypoint);
                    }
                }
            }
        }
    }
    
}