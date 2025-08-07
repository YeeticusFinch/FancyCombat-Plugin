package com.lerdorf.fancycombat;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.Multimap;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import net.md_5.bungee.api.ChatColor;

public class FancyCombat extends JavaPlugin implements Listener, TabExecutor {

	private File configFile;
	private Map<String, Object> configValues;

	private Plugin plugin;
	
	boolean useVanillaSwords = false;
	boolean useWEPSwords = true;

	public void loadConfig() {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // <-- use block style
		options.setIndent(2);
		options.setPrettyFlow(true);

		File pluginFolder = this.getDataFolder();
		if (!pluginFolder.exists())
			pluginFolder.mkdirs();

		configFile = new File(pluginFolder, "config.yml");

		Yaml yaml = new Yaml(options);

		// If file doesn't exist, create it with defaults
		if (!configFile.exists()) {
			configValues = new HashMap<>();
			// configValues.put("requireBothHandsEmpty", requireBothHandsEmpty);
			saveConfig(); // Save default config
		}

		try {
			String yamlAsString = Files.readString(configFile.toPath());
			configValues = (Map<String, Object>) yaml.load(yamlAsString);
			if (configValues == null)
				configValues = new HashMap<>();
		} catch (Exception e) {
			e.printStackTrace();
			configValues = new HashMap<>();
		}

		// Now parse and update values
		try {
			if (configValues.containsKey("useVanillaSwords"))
				useVanillaSwords = (boolean) configValues.get("useVanillaSwords");
		} catch (Exception e) {
			e.printStackTrace();
		}
		configValues.put("useVanillaSwords", useVanillaSwords);
		

		try {
			if (configValues.containsKey("useWEPSwords"))
				useWEPSwords = (boolean) configValues.get("useWEPSwords");
		} catch (Exception e) {
			e.printStackTrace();
		}
		configValues.put("useWEPSwords", useWEPSwords);


		saveConfig(); // Ensure config is up to date
	}

	public void saveConfig() {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // <-- use block style
		options.setIndent(2);
		options.setPrettyFlow(true);

		Yaml yaml = new Yaml(options);
		try (FileWriter writer = new FileWriter(configFile)) {
			yaml.dump(configValues, writer);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onEnable() {
		plugin = this;
		getServer().getPluginManager().registerEvents(this, this);

		// this.getCommand("fc").setExecutor(this);

		loadConfig();
		saveConfig();

		/*
		 * new BukkitRunnable() {
		 * 
		 * @Override public void run() {
		 * 
		 * } }.runTaskTimer(this, 0L, 1L); // Run every 1 tick
		 */

		getLogger().info("FancyCombat enabled!");
	}

	@Override
	public void onDisable() {
		getLogger().info("FancyCombat disabled!");
	}

	public boolean rightClick(Player p, Block block) {
		if (p.isSneaking()) {
			// return false;
		} else {

		}
		return false;
	}

	@EventHandler
	public void onUseEvent(PlayerInteractEvent event) {
		// Player player = event.getPlayer();
		// Bukkit.broadcastMessage("Interaction");
		if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
			// Bukkit.broadcastMessage("Left click");
			Player player = event.getPlayer();
			ItemStack weapon = player.getEquipment().getItemInMainHand();
			if (isCustomSwingItem(weapon)) {
				event.setCancelled(true);
				startCustomSwing(player, weapon);
			}
		}
	}

	public double getAttackRange(Player player) {
		AttributeInstance attackRangeAttr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
		if (attackRangeAttr == null) {
			// Defensive fallback, vanilla default reach in survival is ~3 blocks
			return 3.0;
		}
		return attackRangeAttr.getValue();
	}

	public double getSweepDamageMult(Player player, ItemStack weapon) {
		AttributeInstance sweepRatio = player.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
		double sweepAttr = sweepRatio != null ? sweepRatio.getValue() : 0;
		int enchantLevel = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
		return Math.min(sweepAttr + enchantLevel / (enchantLevel + 1), 1);
	}

	public int getLethalLevel(ItemStack item) {
		if (item == null || item.getType().isAir())
			return 0;
		
		NBTItem nbt = new NBTItem(item);
	    return nbt.hasKey("Lethal") ? nbt.getByte("Lethal") : 0;


		//return 0; // No lethal tag
	}

	public double getCritMult(Player p, ItemStack weapon) {
		
		double result = 1;
		boolean isCrit = p.getFallDistance() > 0.0F && !p.isOnGround() && !p.isClimbing() && !p.isSwimming() && !p.hasPotionEffect(PotionEffectType.BLINDNESS) && !p.isInsideVehicle();
		
		int lethalLevel = getLethalLevel(weapon);
		if (isCrit) {
			result *= 1.5;
			result *= 1 + lethalLevel*0.1;
		}
		
		//Bukkit.broadcastMessage("Critmult: " + result);
		
		return result;
	}

	HashMap<UUID, Integer> swinging = new HashMap<>();
	HashMap<UUID, String> hits = new HashMap<>();
	
	HashMap<UUID, Long> lastSwing = new HashMap<>();

	private void startCustomSwing(Player player, ItemStack weapon) {

		UUID uuid = player.getUniqueId();
		if (lastSwing.containsKey(uuid) && System.currentTimeMillis()-lastSwing.get(uuid) < 100) {
			return;
		}
		lastSwing.put(uuid, System.currentTimeMillis());
		
		double range = getAttackRange(player);

		double cooldownMod = getDamageFraction(player.getAttackCooldown());

		
		FancyParticle p = getParticle(weapon);

		// Bukkit.broadcastMessage("Custom Swing");
		Location eye = player.getEyeLocation().add(player.getVelocity());
		Vector startDir = eye.getDirection().normalize().multiply(range);
		// float ogCooldown = player.getAttackCooldown();
		if (plugin == null)
			plugin = this;
		// float originalCooldown = player.getAttackCooldown();

		storeOgItemModel(weapon);

		float bladeSpeed = 2.5f;

		Quaternionf rot = new Quaternionf().rotateX((float) Math.toRadians(90)) // rotate 90° on X axis
				.rotateZ((float) Math.toRadians(-45)); // then 45° on Y axis

		// Convert to AxisAngle
		AxisAngle4f axisAngle = new AxisAngle4f().set(rot);

		Vector offset = new Vector(0, -0.2f, 0);
		float rightOffset = 0.2f;
		float forwardOffset = 0.4f;

		double critMult = cooldownMod > 0.9 ? getCritMult(player, weapon) : 1;

		restoreOgItemModel(weapon);
		ItemDisplay display = player.getWorld().spawn(
				player.getEyeLocation().add(startDir.clone().multiply(forwardOffset).add(offset)), ItemDisplay.class,
				entity -> {
					// customize the entity!
					entity.setItemStack(weapon.clone());
					entity.setTransformation(
							new Transformation(new Vector3f(), axisAngle, new Vector3f(1f, 1f, 1f), new AxisAngle4f()));

				});

		makeInvisible(weapon);

		Collection<LivingEntity> nearbyEntities = player.getWorld().getNearbyLivingEntities(
			    player.getLocation(), 10, 10, 10, entity -> !entity.equals(player)
				);

		final int swingKey = (int) (Math.random() * Integer.MAX_VALUE);
		swinging.put(uuid, swingKey);

		double sweepMult = getSweepDamageMult(player, weapon);

		int lifetime = 8;

		hits.put(uuid, "");
		
		if (cooldownMod > 0.9) {
			if (sweepMult > 0.1)
				player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
			else
				player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
		}
		else
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0f, 1.0f);

		new BukkitRunnable() {
			boolean stab = false;
			int ticks = 0;
			Vector swingDir = new Vector(0, 0, 0);
			Vector prevSwingDir = startDir.clone();
			Vector blade = startDir.clone();
			ArrayList<UUID> hitEntities = new ArrayList<>();
			double knockback = getWeaponKnockback(player, weapon);

			double stabDist = 0;

			// List<Location> swingPath = new ArrayList<>();
			// float lastYaw = yawStart;
			// float lastPitch = pitchStart;

			@Override
			public void run() {
				// Bukkit.broadcastMessage("Running")
				if (!player.isOnline() || swinging.get(uuid) != swingKey) {
					restoreOgItemModel(weapon);
					display.remove();
					cancel();
					return;
				}
				makeInvisible(weapon);

				display.setVelocity(player.getVelocity());

				Location eyePos = player.getEyeLocation().add(player.getVelocity());
				Vector left = eyePos.getDirection().rotateAroundAxis(new Vector(0, 1, 0), 90);
				if (ticks < 2) {
					swingDir = eyePos.getDirection().normalize().multiply(range).subtract(prevSwingDir);
					if (swingDir.length() < 0.05f) {
						// Stab
						if (ticks == 0) {
							blade = blade.multiply(1.0 / range);
							stabDist = 1.0 / range;
						}
						swingDir = eyePos.getDirection().multiply(bladeSpeed);
						stab = true;
					} else {
						swingDir = perp(swingDir, blade).normalize().multiply(bladeSpeed);
						stab = false;
					}
				} else if (ticks == 2) {
					display.setInterpolationDelay(0);
					display.setInterpolationDuration(4);
				}
				prevSwingDir = eyePos.getDirection().normalize().multiply(range);

				int frames = 3;
				float step = 1f / (float) frames;
				Vector[] points = new Vector[frames];
				int c = 0;
				for (float i = step; i <= 1.01f; i += step) {

					if (stab) {
						if (stabDist >= range) {
							blade.add(swingDir.clone().multiply(step * 0.05));
							stabDist += swingDir.length() * step;
						} else {
							blade.add(swingDir.clone().multiply(step));
							stabDist += swingDir.length() * step;
						}
					} else
						blade.add(swingDir.clone().multiply(step));

					if (!stab)
						blade = blade.normalize().multiply(range);

					Location point = raycastForBlocks(player, blade);
					points[c] = point.toVector();
					c++;
					// Bukkit.broadcastMessage(point.getBlockX() + " " + point.getBlockY() + " " +
					// point.getBlockZ());
					p.spawn(point.clone().add(eyePos.getDirection().multiply(forwardOffset).add(offset)
							.add(left.clone().multiply(-rightOffset))));
					// player.getWorld().spawnParticle(p,
					// point.clone().add(swingDir.clone().multiply(0.5)), 1, 0, 0, 0, 0);
					// player.getWorld().spawnParticle(p,
					// point.clone().add(swingDir.clone().multiply(-0.5)), 1, 0, 0, 0, 0);
				}
				
				// Hit detection
				for (LivingEntity le : nearbyEntities) {
						BoundingBox bb = le.getBoundingBox();
						boolean intersects = false;
						for (Vector point : points) {
							if (intersectsSegmentAABB(eyePos.toVector(), point, bb))
							{
								intersects = true;
								break;
							}
						}
						if (intersects) {
							if (!hitEntities.contains(le.getUniqueId())) {

								le.setMetadata("attackDir", new FixedMetadataValue(plugin, swingDir));
								le.setMetadata("attackKb", new FixedMetadataValue(plugin, knockback * 2));
								if (hitEntities.size() > 0) {
									le.setMetadata("attackMult", new FixedMetadataValue(plugin, sweepMult
											* cooldownMod / getDamageFraction(player.getAttackCooldown())));
								}
								else {
									le.setMetadata("attackMult", new FixedMetadataValue(plugin, critMult
											* cooldownMod / getDamageFraction(player.getAttackCooldown())));
									if (critMult > 1.1) {
										le.getWorld().playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
										if (weapon.getEnchantments() != null && weapon.getEnchantments().size() > 0)
											le.getWorld().spawnParticle(Particle.ENCHANTED_HIT, le.getLocation().add(0, le.getHeight()*0.5, 0), 10, 0.1, 0.1, 0.05*le.getHeight(), 0.1);
										else
											le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, le.getHeight()*0.5, 0), 10, 0.3, 0.2*le.getHeight(), 0.3, 0.2);
									}
								}
								// player.sendMessage("Attacking " + entity.getName());

								player.attack(le);

								hitEntities.add(le.getUniqueId());
							}
						}
				}

				display.setRotation(player.getYaw(), player.getPitch());
				display.teleport(player.getEyeLocation().add(
						blade.clone().multiply(forwardOffset).add(offset).add(left.clone().multiply(-rightOffset))));

				// display.setInterpolationDelay(0); // no delay to the interpolation
				// display.setInterpolationDuration(10); // set the duration of the interpolated
				// rotation

				Quaternionf orientation = new Quaternionf().rotateX((float) Math.toRadians(90))
						.rotateZ((float) Math.toRadians(-45));

				Quaternionf yRotation = new Quaternionf().rotateAxis(
						(float) (stab ? 0
								: (Math.PI - Math.atan2(swingDir.getY(), (swingDir.dot(left.clone().multiply(-1)))))),
						0, 0, 1); // rotate around world Y

				Quaternionf rot = yRotation.mul(orientation); // left-multiply to apply world Y last

				// Convert to AxisAngle
				AxisAngle4f axisAngle = new AxisAngle4f().set(rot);
				display.setTransformation(
						new Transformation(new Vector3f(), axisAngle, new Vector3f(1f, 1f, 1f), new AxisAngle4f()));

				if (ticks > lifetime) {
					display.remove();
					restoreOgItemModel(weapon);
					cancel();
					return;
				}
				ticks++;
			}
		}.runTaskTimer(plugin, 0L, 1L);
	}

	private FancyParticle getParticle(ItemStack weapon) {

		float dustSize = 1;

		ItemMeta meta = weapon.getItemMeta();
		if (meta.hasEnchant(Enchantment.FIRE_ASPECT)) {
			return new FancyParticle(Particle.DRIPPING_LAVA, 1, 0, 0, 0, 0);
		} else if (meta.hasEnchant(Enchantment.SHARPNESS)) {
			return new FancyParticle(Particle.ENCHANTED_HIT, 1, 0, 0, 0, 0);
		} else if (meta.hasEnchant(Enchantment.SMITE)) {
			return new FancyParticle(Particle.DRIPPING_HONEY, 1, 0, 0, 0, 0);
		} else if (meta.hasEnchant(Enchantment.BANE_OF_ARTHROPODS)) {
			return new FancyParticle(Particle.SQUID_INK, 1, 0, 0, 0, 0);
		} else if (meta.hasEnchant(Enchantment.KNOCKBACK)) {
			return new FancyParticle(Particle.SMALL_GUST, 1, 0, 0, 0, 0);
		}

		String model = getModel(weapon);

		if (model != null) {
			model = model.toLowerCase();
			if (model.contains("emerald")) {
				return new FancyParticle(Particle.HAPPY_VILLAGER, 1, 0, 0, 0, 0);
			} else if (model.contains("redstone")) {
				return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0, new DustOptions(Color.RED, dustSize));
			} else if (model.contains("obsidian")) {
				return new FancyParticle(Particle.DRIPPING_OBSIDIAN_TEAR, 1, 0, 0, 0, 0);
			} else if (model.contains("star")) {
				return new FancyParticle(Particle.END_ROD, 1, 0, 0, 0, 0);
			} else if (model.contains("copper")) {
				return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0,
						new DustOptions(Color.fromRGB(169, 141, 92), dustSize));
			} else if (model.contains("lapis")) {
				return new FancyParticle(Particle.DRIPPING_WATER, 1, 0, 0, 0, 0);
			}
		}

		String type = weapon.getType().toString().toLowerCase();

		if (type.contains("diamond")) {
			return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0,
					new DustOptions(Color.fromRGB(111, 255, 241), dustSize));
		} else if (type.contains("gold")) {
			return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0,
					new DustOptions(Color.fromRGB(255, 222, 93), dustSize));
		} else if (type.contains("iron")) {
			return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0,
					new DustOptions(Color.fromRGB(228, 228, 228), dustSize));
		} else if (type.contains("stone")) {
			return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0,
					new DustOptions(Color.fromRGB(140, 140, 140), dustSize));
		} else if (type.contains("wood")) {
			return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0,
					new DustOptions(Color.fromRGB(95, 67, 42), dustSize));
		} else if (type.contains("netherite")) {
			return new FancyParticle(Particle.DUST, 1, 0, 0, 0, 0, new DustOptions(Color.fromRGB(0, 0, 0), dustSize));
		}

		return new FancyParticle(Particle.CRIT, 1, 0, 0, 0, 0);
	}

	private void makeInvisible(ItemStack weapon) {
		// TODO Auto-generated method stub
		String model = getModel(weapon);
		if (model != null && model.contains("wep:ghost_item"))
			return;
		else
			setItemModel(weapon, "wep:ghost_item");
	}

	public Vector perp(Vector a, Vector b) {
		Vector bNormalized = b.clone().normalize();
		double projectionLength = a.dot(bNormalized);
		Vector projection = bNormalized.multiply(projectionLength);
		return a.clone().subtract(projection); // a - proj_b(a)
	}

	public double sign(double d) {
		return d / Math.abs(d);
	}

	public boolean intersectsSegmentAABB(Vector start, Vector end, BoundingBox box) {
		Vector dir = end.clone().subtract(start);
		Vector invDir = new Vector(dir.getX() == 0 ? Double.POSITIVE_INFINITY : 1.0 / dir.getX(),
				dir.getY() == 0 ? Double.POSITIVE_INFINITY : 1.0 / dir.getY(),
				dir.getZ() == 0 ? Double.POSITIVE_INFINITY : 1.0 / dir.getZ());

		double tMin = 0.0;
		double tMax = 1.0;

		Vector min = new Vector(box.getMinX(), box.getMinY(), box.getMinZ());
		Vector max = new Vector(box.getMaxX(), box.getMaxY(), box.getMaxZ());

		// Iterate over X, Y, Z manually
		double[] startComponents = { start.getX(), start.getY(), start.getZ() };
		double[] invComponents = { invDir.getX(), invDir.getY(), invDir.getZ() };
		double[] minComponents = { min.getX(), min.getY(), min.getZ() };
		double[] maxComponents = { max.getX(), max.getY(), max.getZ() };

		for (int i = 0; i < 3; i++) {
			double startComponent = startComponents[i];
			double inv = invComponents[i];
			double t1 = (minComponents[i] - startComponent) * inv;
			double t2 = (maxComponents[i] - startComponent) * inv;

			double tNear = Math.min(t1, t2);
			double tFar = Math.max(t1, t2);

			tMin = Math.max(tMin, tNear);
			tMax = Math.min(tMax, tFar);

			if (tMax < tMin) {
				return false;
			}
		}

		return true;
	}

	public Location raycastForBlocks(Player player, Vector target) {
		Location result = player.getEyeLocation();

		double inc = 0.9;

		for (int i = 0; i < target.length() / inc; i++) {
			Block block = result.getBlock();
			if (!block.isPassable() && block.getBoundingBox().contains(result.toVector())) {
				return getClosestPoint(player.getEyeLocation().toVector(), block.getBoundingBox())
						.toLocation(player.getWorld()).subtract(target.clone().normalize().multiply(0.5f));
			}
			result = result.add(target.clone().normalize().multiply(0.9));
		}

		result.add(target.clone().normalize().multiply(target.length() - inc * ((int) (target.length() / inc))));

		return result;
	}

	public static Vector getClosestPoint(Vector point, BoundingBox box) {
		double x = clamp(point.getX(), box.getMinX(), box.getMaxX());
		double y = clamp(point.getY(), box.getMinY(), box.getMaxY());
		double z = clamp(point.getZ(), box.getMinZ(), box.getMaxZ());
		return new Vector(x, y, z);
	}

	private static double clamp(double val, double min, double max) {
		return Math.max(min, Math.min(max, val));
	}

	public double getWeaponKnockback(Player player, ItemStack weapon) {
		double knockback = 0;

		// 1. Attribute Modifiers (GENERIC_ATTACK_KNOCKBACK)
		if (weapon != null && weapon.hasItemMeta()) {
			ItemMeta meta = weapon.getItemMeta();
			if (meta != null && meta.hasAttributeModifiers()) {
				Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
				if (modifiers != null && modifiers.containsKey(Attribute.ATTACK_KNOCKBACK)) {
					for (AttributeModifier mod : modifiers.get(Attribute.ATTACK_KNOCKBACK)) {
						knockback += mod.getAmount(); // Usually additive
					}
				}
			}
		}

		// 2. Knockback enchantment
		int knockbackEnchantLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
		knockback += knockbackEnchantLevel;

		// 3. Sprinting knockback
		if (player != null && player.isSprinting()) {
			knockback += 1;
		}

		return knockback + 1;
	}

	public boolean isCustomSwingItem(ItemStack item) {
		if (item.getType().toString().toLowerCase().contains("sword")) {
			if (modelContains(item, "wep") && !useWEPSwords)
				return false;
			if (modelContains(item, "lance"))
				return false;
			if (item.getItemMeta().getItemModel() == null && !useVanillaSwords)
				return false;

			return true;
		}

		return false;
	}

	public double getDamageFraction(double cooldown) {
		if (cooldown >= 1)
			return 1;
		if (cooldown <= 0)
			return 0.2;
		return 0.8 * Math.pow(cooldown, 2) + 0.2;
	}

	@EventHandler
	public void onVanillaAttack(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof Player p) {
			ItemStack weapon = p.getEquipment().getItemInMainHand();
			if (isCustomSwingItem(weapon)) {
				if (event.getEntity().hasMetadata("attackDir")) {
					Entity entity = event.getEntity();
					Vector attackDir = (Vector) entity.getMetadata("attackDir").get(0).value();
					double knockback = entity.getMetadata("attackKb").get(0).asDouble();
					double dmgMult = entity.getMetadata("attackMult").get(0).asDouble();
					if (Double.isFinite(knockback))
						entity.setVelocity(entity.getVelocity().add(attackDir.normalize().multiply(knockback)));
					entity.removeMetadata("attackDir", plugin);
					entity.removeMetadata("attackKb", plugin);
					entity.removeMetadata("attackMult", plugin);
					double newDmg = event.getDamage() * dmgMult;
					event.setDamage(newDmg);
					// p.sendMessage("Hit " + event.getEntity().getName() + " for " + newDmg + "
					// damage");
					hits.put(p.getUniqueId(),
							hits.get(p.getUniqueId()) + (hits.get(p.getUniqueId()).length() > 0 ? "\n" : "") + ChatColor.AQUA + "Hit " + ChatColor.GREEN
									+ event.getEntity().getName() + ChatColor.AQUA + " for " + ChatColor.YELLOW + ((int)(newDmg*10))*0.1f
									+ ChatColor.AQUA + " damage");
					p.sendActionBar(hits.get(p.getUniqueId()));
				} else {
					event.setCancelled(true); // block normal sword hits
					startCustomSwing(p, weapon);
					// p.sendMessage("Hit " + event.getEntity().getName() + " for " +
					// event.getDamage() + " damage " + p.getAttackCooldown() + " cooldown");
				}
			}
		}
	}

	private boolean modelContains(ItemStack item, String sub) {
		if (item == null)
			return false;
		String model = getModel(item);
		if (model != null && model.toLowerCase().contains(sub.toLowerCase()))
			return true;
		return false;
	}

	public String getModel(ItemStack item) {
		if (!item.hasItemMeta())
			return null;
		ItemMeta meta = item.getItemMeta();
		if (!meta.hasEquippable()) {
			if (meta.getItemModel() == null)
				return null;
			return meta.getItemModel().toString();
		}
		EquippableComponent equippable = meta.getEquippable();
		String currentModel = equippable.getModel().toString();
		return currentModel;
	}

	public void storeOgItemModel(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (getOgItemModel(item) == null) {
			List<String> lore = meta.getLore();
			if (lore == null)
				lore = new ArrayList<String>();
			lore.add("Og Model:" + (meta.hasItemModel() ? meta.getItemModel() : "$null$"));
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
	}

	public String getOgItemModel(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		List<String> lore = meta.getLore();
		if (lore != null && lore.size() > 0) {
			for (String str : lore) {
				if (str.startsWith("Og Model:")) {
					String model = str.substring(str.indexOf(':') + 1);
					if (model.contains("$null$"))
						return "";
					return model;
				}
			}
		}
		return null;
	}

	public void restoreOgItemModel(ItemStack item) {
		String model = getOgItemModel(item);

		if (model != null) {
			setItemModel(item, model);

			/*
			 * ItemMeta meta = item.getItemMeta(); List<String> lore = meta.getLore(); if
			 * (lore != null && lore.size() > 0) { String rm = null; for (String str : lore)
			 * { if (str.startsWith("Og Model:")) { rm = str; break; } } if (rm != null) {
			 * lore.remove(rm); meta.setLore(lore); item.setItemMeta(meta); } }
			 */
		}
	}

	public void setItemModel(ItemStack item, String model) {
		// if (!item.hasItemMeta())
		// return;
		ItemMeta meta = item.getItemMeta();
		if (model == null || model.length() == 0)
			meta.setItemModel(null);
		else
			meta.setItemModel(NamespacedKey.fromString(model));
		item.setItemMeta(meta);
	}

	public void setEquippableModel(ItemStack item, String model) {
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			if (meta.hasEquippable()) {
				EquippableComponent equippable = meta.getEquippable();
				String ogModel = equippable.getModel().toString();
				equippable.setModel(NamespacedKey.fromString(model));
				meta.setEquippable(equippable);
				item.setItemMeta(meta);
			} else {
				NBT.modifyComponents(item, components -> {
					ReadWriteNBT equippable = components.getOrCreateCompound("minecraft:equippable");

					// Set the asset_id
					equippable.setString("asset_id", "invisible");

					// If this is a new equippable component, set defaults based on item type
					if (!equippable.hasTag("slot")) {
						setDefaultEquippableData(equippable, item.getType());
					}

				});

				meta = item.getItemMeta();
				item.setItemMeta(meta);
			}
		}
	}

	private void setDefaultEquippableData(ReadWriteNBT equippable, Material material) {
		String materialName = material.name().toLowerCase();

		// Set slot based on material type
		if (materialName.contains("helmet") || materialName.contains("cap")) {
			equippable.setString("slot", "head");
		} else if (materialName.contains("chestplate") || materialName.contains("tunic")) {
			equippable.setString("slot", "chest");
		} else if (materialName.contains("leggings") || materialName.contains("pants")) {
			equippable.setString("slot", "legs");
		} else if (materialName.contains("boots") || materialName.contains("shoes")) {
			equippable.setString("slot", "feet");
		} else { // Default to chest if we can't determine
			equippable.setString("slot", "chest");
		}

		// Set equip sound based on material
		String equipSound = getEquipSoundString(material);
		if (equipSound != null) {
			equippable.setString("equip_sound", equipSound);
		}

		// Set default properties
		equippable.setBoolean("dispensable", true);
		equippable.setBoolean("swappable", true);
		equippable.setBoolean("damage_on_hurt", true);
	}

	private String getEquipSoundString(Material material) {
		String name = material.name().toLowerCase();

		if (name.contains("leather")) {
			return "minecraft:item.armor.equip_leather";
		} else if (name.contains("chain")) {
			return "minecraft:item.armor.equip_chain";
		} else if (name.contains("iron")) {
			return "minecraft:item.armor.equip_iron";
		} else if (name.contains("diamond")) {
			return "minecraft:item.armor.equip_diamond";
		} else if (name.contains("gold")) {
			return "minecraft:item.armor.equip_gold";
		} else if (name.contains("netherite")) {
			return "minecraft:item.armor.equip_netherite";
		}

		return "minecraft:item.armor.equip_generic";
	}

	/*
	 * @Override public boolean onCommand(CommandSender sender, Command command,
	 * String label, String[] args) { sender.sendMessage(ChatColor.GREEN +
	 * "Reloading FancyChairs config.yml"); loadConfig();
	 * sender.sendMessage("Config reloaded!");
	 * sender.sendMessage("requireBothHandsEmpty: " + requireBothHandsEmpty);
	 * sender.sendMessage("onlySitOnCarpetedChairs: " + onlySitOnCarpetedChairs);
	 * sender.sendMessage("enableSlabs: " + enableSlabs);
	 * sender.sendMessage("enableEmptyHandsException: " +
	 * enableEmptyHandsException); return true; }
	 */
}