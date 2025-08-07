package com.lerdorf.fancycombat;

import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.DustTransition;
import org.bukkit.block.data.BlockData;
import org.bukkit.Location;

public class FancyParticle {

	Particle p;
	int count;
	int dx;
	int dy;
	int dz;
	int extra;
	DustOptions dust = null;
	DustTransition trans = null;
	BlockData block = null;
	
	
	public FancyParticle(Particle p, int count, int dx, int dy, int dz, int extra) {
		this.p = p;
		this.count = count;
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
		this.extra = extra;
	}

	public FancyParticle(Particle p, int count, int dx, int dy, int dz, int extra, DustOptions dust) {
		this.p = p;
		this.count = count;
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
		this.extra = extra;
		this.dust = dust;
	}

	public FancyParticle(Particle p, int count, int dx, int dy, int dz, int extra, DustTransition trans) {
		this.p = p;
		this.count = count;
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
		this.extra = extra;
		this.trans = trans;
	}

	public FancyParticle(Particle p, int count, int dx, int dy, int dz, int extra, BlockData block) {
		this.p = p;
		this.count = count;
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
		this.extra = extra;
		this.block = block;
	}
	
	public void spawn(Location loc) {
		if (dust != null)
			loc.getWorld().spawnParticle(p, loc, count, dx, dy, dz, extra, dust);
		else if (trans != null)
			loc.getWorld().spawnParticle(p, loc, count, dx, dy, dz, extra, trans);
		else if (block != null)
			loc.getWorld().spawnParticle(p, loc, count, dx, dy, dz, extra, block);
		else
			loc.getWorld().spawnParticle(p, loc, count, dx, dy, dz, extra);
	}
}
