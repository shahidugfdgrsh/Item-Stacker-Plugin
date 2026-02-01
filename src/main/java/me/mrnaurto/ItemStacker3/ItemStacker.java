package me.mrnaruto.CustomTools;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemStacker extends JavaPlugin implements Listener {

    // Use ConcurrentHashMap for thread-safe performance
    private final Set<Item> itemsToRemove = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // Cache of materials that should NEVER be stacked (for instant lookup)
    private final Set<Material> blockedMaterials = new HashSet<>();
    
    @Override
    public void onEnable() {
        // Initialize blocked materials cache (one-time setup)
        initializeBlockedMaterials();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Hardcoded enable message
        getLogger().info("===================================");
        getLogger().info("ItemStacker by MrNaruto enabled");
        getLogger().info("===================================");
        
        // Start optimized stacking task
        startStackingTask();
    }

    @Override
    public void onDisable() {
        // Clear memory
        itemsToRemove.clear();
        blockedMaterials.clear();
        getLogger().info("ItemStacker by MrNaruto disabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is OP
        if (player.isOp()) {
            // Send hardcoded message to OP players
            player.sendMessage("§6===========");
            player.sendMessage("§eItem Stacker v1.0");
            player.sendMessage("§eAuthor MrNaruto");
            player.sendMessage("§6===========");
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item newItem = event.getEntity();
        ItemStack newItemStack = newItem.getItemStack();
        
        // FAST CHECK: If item is in blocked list, don't process
        if (isBlockedItem(newItemStack)) {
            return;
        }
        
        // OPTIMIZATION: Only check if item isn't already at max stack
        if (newItemStack.getAmount() >= newItemStack.getMaxStackSize()) {
            return;
        }
        
        // Get nearby entities once (cached)
        List<org.bukkit.entity.Entity> nearbyEntities = newItem.getNearbyEntities(1.5, 1.5, 1.5); // Reduced radius for performance
        
        // OPTIMIZATION: Process only nearby items
        for (org.bukkit.entity.Entity entity : nearbyEntities) {
            if (!(entity instanceof Item) || entity == newItem || itemsToRemove.contains(entity)) {
                continue;
            }
            
            Item existingItem = (Item) entity;
            ItemStack existingItemStack = existingItem.getItemStack();
            
            // FAST CHECK: Skip if existing item is blocked
            if (isBlockedItem(existingItemStack)) {
                continue;
            }
            
            // Check if items can be stacked
            if (canStackFast(newItemStack, existingItemStack)) {
                stackItemsOptimized(existingItem, newItem);
                event.setCancelled(true); // Cancel the new item spawn since we're stacking it
                return; // Only stack with one item per event to reduce processing
            }
        }
    }

    /**
     * Initialize all blocked materials (called once on startup)
     */
    private void initializeBlockedMaterials() {
        // Add all shulker boxes
        blockedMaterials.addAll(Arrays.asList(
            Material.SHULKER_BOX,
            Material.BLACK_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX
        ));
        
        // Add spawners
        blockedMaterials.add(Material.SPAWNER);
        
        // Add non-stackable items
        blockedMaterials.addAll(Arrays.asList(
            Material.ELYTRA,
            Material.TOTEM_OF_UNDYING,
            Material.DRAGON_HEAD,
            Material.PLAYER_HEAD,
            Material.CREEPER_HEAD,
            Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            
            // Other non-stackable items
            Material.SHIELD,
            Material.TRIDENT,
            Material.CROSSBOW,
            Material.BOW,
            Material.FISHING_ROD,
            Material.ENCHANTED_BOOK,
            Material.WRITTEN_BOOK,
            Material.FILLED_MAP,
            Material.BUNDLE,
            Material.GOAT_HORN
        ));
    }

    /**
     * FAST CHECK: Check if an item is in the blocked list
     */
    private boolean isBlockedItem(ItemStack itemStack) {
        if (itemStack == null) return true;
        
        // Check if material is in blocked cache
        if (blockedMaterials.contains(itemStack.getType())) {
            return true;
        }
        
        // Check if item can't stack (max stack size = 1)
        if (itemStack.getMaxStackSize() == 1) {
            return true;
        }
        
        return false;
    }

    /**
     * OPTIMIZED: Faster stack checking with early exits
     */
    private boolean canStackFast(ItemStack stack1, ItemStack stack2) {
        // Early null checks
        if (stack1 == null || stack2 == null) return false;
        
        // Fast material check (cheapest operation)
        if (stack1.getType() != stack2.getType()) return false;
        
        // Skip durability check for non-durable items
        if (stack1.getType().getMaxDurability() > 0) {
            if (stack1.getDurability() != stack2.getDurability()) return false;
        }
        
        // Check if both have item meta (expensive, so check last)
        boolean hasMeta1 = stack1.hasItemMeta();
        boolean hasMeta2 = stack2.hasItemMeta();
        
        if (hasMeta1 != hasMeta2) return false;
        if (hasMeta1 && !stack1.getItemMeta().equals(stack2.getItemMeta())) return false;
        
        return true;
    }

    /**
     * OPTIMIZED: Stack items with minimal operations
     */
    private void stackItemsOptimized(Item existingItem, Item newItem) {
        ItemStack existingStack = existingItem.getItemStack();
        ItemStack newStack = newItem.getItemStack();
        
        int existingAmount = existingStack.getAmount();
        int newAmount = newStack.getAmount();
        int maxStackSize = existingStack.getMaxStackSize();
        
        // OPTIMIZATION: Check if we can combine fully without modifying item
        if (existingAmount + newAmount <= maxStackSize) {
            // Update existing item once
            existingStack.setAmount(existingAmount + newAmount);
            existingItem.setItemStack(existingStack);
            
            // Mark new item for removal
            itemsToRemove.add(newItem);
            newItem.remove();
        } else {
            // Partial combination - calculate once
            int canAdd = maxStackSize - existingAmount;
            
            // Update existing item
            existingStack.setAmount(maxStackSize);
            existingItem.setItemStack(existingStack);
            
            // Update new item if there's remainder
            if (canAdd > 0) {
                newStack.setAmount(newAmount - canAdd);
                newItem.setItemStack(newStack);
            }
        }
    }

    /**
     * OPTIMIZED: Stacking task with performance improvements
     */
    private void startStackingTask() {
        new BukkitRunnable() {
            private int currentWorldIndex = 0;
            private final List<World> worlds = new ArrayList<>();
            
            @Override
            public void run() {
                // OPTIMIZATION: Cycle through worlds instead of checking all every tick
                if (worlds.isEmpty() || Bukkit.getWorlds().size() != worlds.size()) {
                    worlds.clear();
                    worlds.addAll(Bukkit.getWorlds());
                }
                
                if (worlds.isEmpty()) return;
                
                // Get current world to process
                World world = worlds.get(currentWorldIndex);
                currentWorldIndex = (currentWorldIndex + 1) % worlds.size();
                
                // OPTIMIZATION: Only get loaded chunks
                Chunk[] loadedChunks = world.getLoadedChunks();
                if (loadedChunks.length == 0) return;
                
                // OPTIMIZATION: Process only 3 random chunks per tick to spread load
                Random random = new Random();
                int chunksToProcess = Math.min(3, loadedChunks.length);
                
                for (int i = 0; i < chunksToProcess; i++) {
                    Chunk chunk = loadedChunks[random.nextInt(loadedChunks.length)];
                    processChunkItems(chunk);
                }
                
                // Clean up marked items
                cleanupMarkedItems();
            }
            
            /**
             * Process items in a specific chunk (reduces search space)
             */
            private void processChunkItems(Chunk chunk) {
                // Get all items in chunk
                Item[] chunkItems = Arrays.stream(chunk.getEntities())
                    .filter(entity -> entity instanceof Item)
                    .map(entity -> (Item) entity)
                    .toArray(Item[]::new);
                
                if (chunkItems.length <= 1) return; // No stacking needed
                
                // OPTIMIZATION: Use spatial partitioning - group items by type first
                Map<Material, List<Item>> itemsByType = new HashMap<>();
                
                for (Item item : chunkItems) {
                    if (itemsToRemove.contains(item)) continue;
                    
                    ItemStack stack = item.getItemStack();
                    if (stack.getAmount() >= stack.getMaxStackSize()) continue;
                    if (isBlockedItem(stack)) continue;
                    
                    itemsByType.computeIfAbsent(stack.getType(), k -> new ArrayList<>()).add(item);
                }
                
                // Process each material type separately (reduces comparisons)
                for (List<Item> itemsOfType : itemsByType.values()) {
                    if (itemsOfType.size() <= 1) continue;
                    
                    // Sort by amount to fill partial stacks first
                    itemsOfType.sort(Comparator.comparingInt(item -> item.getItemStack().getAmount()));
                    
                    // Try to stack items
                    for (int i = 0; i < itemsOfType.size(); i++) {
                        Item item1 = itemsOfType.get(i);
                        if (itemsToRemove.contains(item1)) continue;
                        
                        ItemStack stack1 = item1.getItemStack();
                        if (stack1.getAmount() >= stack1.getMaxStackSize()) continue;
                        
                        for (int j = i + 1; j < itemsOfType.size(); j++) {
                            Item item2 = itemsOfType.get(j);
                            if (itemsToRemove.contains(item2)) continue;
                            
                            ItemStack stack2 = item2.getItemStack();
                            if (stack2.getAmount() >= stack2.getMaxStackSize()) continue;
                            
                            if (canStackFast(stack1, stack2)) {
                                stackItemsOptimized(item1, item2);
                                break; // Only stack with one item per tick
                            }
                        }
                    }
                }
            }
            
            /**
             * Clean up marked items
             */
            private void cleanupMarkedItems() {
                // OPTIMIZATION: Only process a few items per tick
                Iterator<Item> iterator = itemsToRemove.iterator();
                int processed = 0;
                int maxProcessPerTick = 50; // Limit cleanup to prevent lag spikes
                
                while (iterator.hasNext() && processed < maxProcessPerTick) {
                    Item item = iterator.next();
                    if (item.isDead() || !item.isValid()) {
                        iterator.remove();
                        processed++;
                    }
                }
            }
            
        }.runTaskTimer(this, 40L, 20L); // Start after 2 seconds, run every 1 second (reduced frequency)
    }
}