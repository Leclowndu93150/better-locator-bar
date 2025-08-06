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
import net.minecraft.world.waypoints.WaypointStyleAssets;

import java.util.*;

public class LodestoneCompassTracker {
    private static final Map<UUID, Map<GlobalPos, LodestoneWaypointTransmitter>> playerWaypoints = new HashMap<>();
    private static final Map<UUID, Integer> playerTickCounters = new HashMap<>();

    public void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        UUID playerId = serverPlayer.getUUID();
        int tickCounter = playerTickCounters.getOrDefault(playerId, 0);
        
        if (++tickCounter >= 20) {
            playerTickCounters.put(playerId, 0);
            handlePlayerCompass(serverPlayer);
        } else {
            playerTickCounters.put(playerId, tickCounter);
        }
    }

    public void onPlayerJoin(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        // Immediately process compasses when player joins
        handlePlayerCompass(serverPlayer);
        
        // Reset tick counter for this player
        playerTickCounters.put(serverPlayer.getUUID(), 0);
    }

    private void handlePlayerCompass(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Map<GlobalPos, LodestoneWaypointTransmitter> currentWaypoints = new HashMap<>();

        List<ItemStack> compasses = findAllLodestoneCompasses(player);

        for (ItemStack compass : compasses) {
            LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()) {
                GlobalPos targetPos = tracker.target().get();
                
                // Check if lodestone exists at the position
                ServerLevel targetLevel = player.getServer().getLevel(targetPos.dimension());
                if (targetLevel != null) {
                    boolean lodestoneExists = targetLevel.getBlockState(targetPos.pos()).is(Blocks.LODESTONE);
                    
                    if (lodestoneExists) {
                        // Lodestone exists - add/update waypoint and lore
                        addCoordinatesToCompass(compass, targetPos.pos());

                        Map<GlobalPos, LodestoneWaypointTransmitter> existingWaypoints = playerWaypoints.getOrDefault(playerId, new HashMap<>());
                        LodestoneWaypointTransmitter existingWaypoint = existingWaypoints.get(targetPos);

                        if (existingWaypoint != null) {
                            currentWaypoints.put(targetPos, existingWaypoint);
                        } else {
                            Waypoint.Icon icon = createCompassIcon(compass);
                            UUID waypointId = UUID.nameUUIDFromBytes((LodestoneWaypointStyles.LODESTONE_UUID_PREFIX + playerId + "_" + targetPos.toString()).getBytes());
                            
                            LodestoneWaypointTransmitter waypoint = new LodestoneWaypointTransmitter(
                                    waypointId, targetPos.pos(), icon, 60000000, playerId
                            );

                            try {
                                ServerLevel level = player.level();
                                level.getWaypointManager().trackWaypoint(waypoint);
                                currentWaypoints.put(targetPos, waypoint);
                            } catch (Exception e) {
                                // Silently handle waypoint tracking errors to prevent server crashes
                            }
                        }
                    } else {
                        // Lodestone doesn't exist - remove lore
                        removeLodestoneCoordinates(compass);
                    }
                }
            }
        }

        Map<GlobalPos, LodestoneWaypointTransmitter> previousWaypoints = playerWaypoints.getOrDefault(playerId, new HashMap<>());
        for (Map.Entry<GlobalPos, LodestoneWaypointTransmitter> entry : previousWaypoints.entrySet()) {
            if (!currentWaypoints.containsKey(entry.getKey()) && entry.getValue() != null) {
                try {
                    ServerLevel level = (ServerLevel) player.level();
                    level.getWaypointManager().untrackWaypoint(entry.getValue());
                } catch (Exception e) {
                    // Silently handle waypoint untracking errors to prevent server crashes
                }
            }
        }

        if (currentWaypoints.isEmpty()) {
            playerWaypoints.remove(playerId);
        } else {
            playerWaypoints.put(playerId, currentWaypoints);
        }
    }

    private List<ItemStack> findAllLodestoneCompasses(ServerPlayer player) {
        List<ItemStack> compasses = new ArrayList<>();

        ItemStack mainHand = player.getMainHandItem();
        if (isLodestoneCompass(mainHand)) {
            compasses.add(mainHand);
        }

        ItemStack offHand = player.getOffhandItem();
        if (isLodestoneCompass(offHand)) {
            compasses.add(offHand);
        }

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isLodestoneCompass(stack)) {
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

        if (compass.has(DataComponents.LORE)) {
            ItemLore existingLore = compass.get(DataComponents.LORE);
            loreList.addAll(existingLore.lines());
        }

        Component coordComponent = Component.literal("Lodestone: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(style -> style.withColor(0x55FFFF));

        loreList.removeIf(line -> line.getString().startsWith("Lodestone: "));
        loreList.add(coordComponent);

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