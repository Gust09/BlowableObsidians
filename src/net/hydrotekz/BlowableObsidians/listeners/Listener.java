package net.hydrotekz.BlowableObsidians.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import net.hydrotekz.BlowableObsidians.BlowablePlugin;

public class Listener implements org.bukkit.event.Listener {

	BlowablePlugin plugin;

	public Listener(BlowablePlugin blowablePlugin){
		plugin = blowablePlugin;
	}

	private HashMap<String, Double> healthMap = new HashMap<String, Double>();

	@EventHandler (priority = EventPriority.MONITOR)
	public void onEntityExplode(EntityExplodeEvent e) {
		if (!e.isCancelled() && e.getEntity() != null){
			if (plugin.getConfig().getBoolean("Only TNT") && e.getEntityType() != EntityType.PRIMED_TNT) return;
			Location source = e.getLocation();
			double dmgRadius = plugin.getConfig().getDouble("Damage Radius");
			if (e.getYield() > 1) dmgRadius+=e.getYield()/10;
			int radius = (int)Math.ceil(dmgRadius);

			for (Block b : new ArrayList<Block>(e.blockList())){
				if (plugin.Handler.makeBlowable(b.getType())){
					e.blockList().remove(b);
				}
			}

			for (int x = -radius; x <= radius; x++) {
				for (int y = -radius; y <= radius; y++) {
					for (int z = -radius; z <= radius; z++) {
						Location loc = new Location(source.getWorld(), x + source.getX(), y + source.getY(), z + source.getZ());

						if (source.distance(loc) <= dmgRadius) {
							Block block = loc.getBlock();
							World world = loc.getWorld();

							// Don't damage blocks naturally generated
							if(block.getType() == Material.BEDROCK){
								switch (world.getEnvironment()){
									case NORMAL:
										if(loc.getBlockY() < 6) return;
										break;
									case NETHER:
										if(loc.getBlockY() == 0 || loc.getBlockY() == 127) return;
										break;
								}
							}

							if (plugin.Handler.makeBlowable(block.getType())){
								// Get distance and damage
								double distance = loc.distance(source);
								double damage = 1;

								// Yield
								if (e.getYield() > 0.5) damage+=1;
								else if (e.getYield() > 8) damage+=2;
								else if (e.getYield() > 16) damage+=3;
								else if (e.getYield() > 22) damage+=4;
								else if (e.getYield() > 28) damage+=5;

								// Check if source is liquid
								if (source.getBlock().isLiquid()){
									damage = damage * plugin.getConfig().getDouble("Liquid Multiplier");
								}

								// Damage the block
								if (damage > 0){
									if (distance > 1) damage = damage / (distance * 0.7);

									String id = plugin.Handler.getID(block);
									if (healthMap.containsKey(id)) healthMap.put(id, healthMap.get(id)-damage);
									else healthMap.put(id, plugin.Handler.getHealth(block)-damage);
									if (healthMap.get(id) <= 0){
										e.blockList().add(block);
										healthMap.remove(id);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	@EventHandler (priority = EventPriority.MONITOR)
	public void onBlockBreak(BlockBreakEvent e){
		if (!e.isCancelled()){
			String id = plugin.Handler.getID(e.getBlock());
			healthMap.remove(id);
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler (priority = EventPriority.MONITOR)
	public void onPlayerInteract(PlayerInteractEvent e){
		if (!e.isCancelled()){
			Player p = e.getPlayer();
			if (e.getAction().toString().equalsIgnoreCase(plugin.getConfig().getString("Check.Type"))){
				String required = plugin.getConfig().getString("Check.Item");
				if (required.equals("*") || (p.getItemInHand() != null && p.getItemInHand().getType().toString().equalsIgnoreCase(required))){
					Block b = e.getClickedBlock();
					Location loc = b.getLocation();
					World world = b.getLocation().getWorld();

					// Don't check blocks naturally generated
					if(b.getType() == Material.BEDROCK){
						switch (world.getEnvironment()){
							case NORMAL:
								if(loc.getBlockY() < 6) return;
								break;
							case NETHER:
								if(loc.getBlockY() == 0 || loc.getBlockY() == 127) return;
								break;
						}
					}

					String id = plugin.Handler.getID(b);
					if (healthMap.containsKey(id)){
						int percent = (int)(((healthMap.get(id) * 100) / plugin.Handler.getHealth(b)));
						String msg = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Message.Block health").replaceFirst("<percent>", String.valueOf(percent)));
						p.sendMessage(msg);
					} else if (plugin.Handler.makeBlowable(b.getType()) && plugin.getConfig().getBoolean("Always Send Health")){
						String msg = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Message.Block health").replaceFirst("<percent>", "100"));
						p.sendMessage(msg);
					}
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler (priority = EventPriority.MONITOR)
	public void onItemSpawn(ItemSpawnEvent e){
		if (!e.isCancelled() && (plugin.getConfig().getBoolean("Better Stacking") || 
				plugin.getConfig().getBoolean("Void Stacking"))){
			List<Entity> entities = e.getEntity().getNearbyEntities(2, 2, 2);
			if (entities == null || entities.isEmpty()) return;
			int fallingBlocks = 0;
			for(Entity entity : entities){
				if(entity.getType() == EntityType.FALLING_BLOCK){
					Location loc = entity.getLocation();
					boolean voidStack = false;
					if (plugin.getConfig().getBoolean("Void Stacking") && loc.getBlockY() < 0){
						loc.setY(0);
						voidStack = true;
						Block bedrock = loc.getBlock();
						if (!bedrock.getType().isSolid()){
							bedrock.setType(Material.SANDSTONE);
							continue;
						}
					}
					Block below = loc.getBlock();
					if (plugin.Handler.landUpon(below) || voidStack){
						Block block = below.getRelative(BlockFace.UP);

						if (plugin.getConfig().getBoolean("Better Stacking")){
							fallingBlocks++;
							if (fallingBlocks >= 2){
								fallingBlocks = 0;
								continue;
							}

							int attempts = 0;
							while(block.getType() != Material.AIR && !block.isLiquid()){
								attempts++;
								String attempt = String.valueOf(attempts);
								if (attempt.endsWith("0") || attempt.endsWith("2") || attempt.endsWith("4") || attempt.endsWith("6") || attempt.endsWith("8")) continue;
								if (!plugin.Handler.landUpon(block)) return;
								block = block.getRelative(BlockFace.UP);
								if (block.getY() > 256) return;
							}
						}

						e.getEntity().remove();
						entity.remove();

						FallingBlock fallingBlock = (FallingBlock) entity;
						block.setTypeId(fallingBlock.getBlockId());
						block.setData(fallingBlock.getBlockData());
					}
				}
			}
		}
	}
}