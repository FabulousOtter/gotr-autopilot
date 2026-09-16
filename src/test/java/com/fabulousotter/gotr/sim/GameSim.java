/*
 * Copyright (c) 2026, FabulousOtter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fabulousotter.gotr.sim;

import com.google.common.collect.ImmutableList;
import com.fabulousotter.gotr.model.Alignment;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.model.CellTier;
import com.fabulousotter.gotr.model.PouchType;
import com.fabulousotter.gotr.plan.Instruction;
import com.fabulousotter.gotr.plan.Planner;
import com.fabulousotter.gotr.plan.PlannerSettings;
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.plan.Target;
import com.fabulousotter.gotr.state.BarrierState;
import com.fabulousotter.gotr.state.GamePhase;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.PouchState;
import com.fabulousotter.gotr.state.RateTracker;
import com.fabulousotter.gotr.state.Snapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * A headless game of Guardians of the Rift that drives the {@link Planner} tick by tick and
 * carries out whatever it says, the way an obedient player would: walk to the target at
 * running pace, then do the one thing the step names. The world side runs the portal and
 * altar clocks, the crowd's power, barrier decay and the close.
 *
 * Its purpose is not fidelity to the game's every rule but finding places where the planner
 * tells the player something wrong (an instruction with nowhere to go, a portal it lets pass,
 * stones still in hand at the close) or something wasteful (flip-flopping between two steps,
 * idling with work available). Each such event is a {@link Finding}; the final energy is
 * the score for comparing strategies.
 */
final class GameSim
{
	static final class Finding
	{
		final int tick;
		final String kind;
		final String detail;

		Finding(int tick, String kind, String detail)
		{
			this.tick = tick;
			this.kind = kind;
			this.detail = detail;
		}

		@Override
		public String toString()
		{
			return String.format("t=%4d (%3ds) %-18s %s", tick, Math.round(tick * 0.6), kind, detail);
		}
	}

	static final class Barrier
	{
		final WorldPoint tile;
		CellTier tier;
		double health;

		Barrier(WorldPoint tile, CellTier tier)
		{
			this.tile = tile;
			this.tier = tier;
			this.health = 100;
		}
	}

	private static final int INVENTORY = 28;

	final SimConfig cfg;
	private final PlannerSettings settings;
	private final Planner planner = new Planner();
	private final RateTracker rates = new RateTracker();
	private final Random rng;

	// World.
	int tick;
	GamePhase phase = GamePhase.ACTIVE;
	double power;
	int elemental;
	int catalytic;
	Altar activeElemental;
	Altar activeCatalytic;
	int nextRotation;
	boolean portalOpen;
	WorldPoint portalPos;
	int portalClosesAt;
	int lastPortalSpawn = -1;
	int nextPortalSpawn;
	boolean anyPortal;
	int closingUntil;
	final List<Barrier> barriers = new ArrayList<>();
	final Set<WorldPoint> brokenTiles = new HashSet<>();
	int playerGuardians;

	// Player.
	WorldPoint pos = Temple.WORKBENCH;
	Location location = Location.TEMPLE;
	Altar altarRoom;
	int insideRemaining;
	int busy;
	int fragments;
	double fragAccum;
	int essence;
	double craftAccum;
	int pouch;
	final Map<Alignment, Integer> stones = new EnumMap<>(Alignment.class);
	final Set<Altar> runes = EnumSet.noneOf(Altar.class);
	CellTier chargedCell;
	int unchargedCells;
	boolean cellChargedThisTrip;

	// Bookkeeping.
	final List<Finding> findings = new ArrayList<>();
	final List<String> timeline = new ArrayList<>();
	private final ArrayDeque<int[]> stepChanges = new ArrayDeque<>();
	private Step lastStep;
	private int lastOscillationReport = -100;
	private int idleTicks;
	private int holdTicks;
	private int cellHeldTicks;
	private WorldPoint lastTile;
	private WorldPoint lastTileSeen;
	private int stuckTicks;
	private String lastStateKey = "";
	private int portalTicksInTemple;
	private boolean portalEntered;
	private int portalsMissed;
	private int portalsUsed;
	private int altarTrips;
	private int cellsUsed;
	private int wastedGuardianArrivals;
	int closeTick = -1;

	GameSim(SimConfig cfg)
	{
		this.cfg = cfg;
		this.settings = cfg.settings();
		this.rng = new Random(cfg.seed);
		stones.put(Alignment.ELEMENTAL, 0);
		stones.put(Alignment.CATALYTIC, 0);
		unchargedCells = cfg.startWithCells ? 10 : 0;
		// No altar is open for the first rotation period (the HUD counts down "Altars change
		// in Ns" from the start), as seen live on 2026-09-15.
		nextRotation = cfg.altarRotationTicks;
		nextPortalSpawn = cfg.firstPortalTicks;
		if (cfg.graceStart)
		{
			pouch = cfg.pouchCapacity;
			pos = Temple.GREAT_GUARDIAN;
		}
	}

	// ---- driving ---------------------------------------------------------------------------

	/** Runs the game to its end and returns the score line. */
	String run()
	{
		while (phase != GamePhase.ENDED && tick < cfg.maxTicks)
		{
			stepWorld();
			if (phase == GamePhase.ENDED)
			{
				break;
			}
			Snapshot s = snapshot().toBuilder().chosenTile(lastTile).lastStep(lastStep).build();
			Instruction i = planner.plan(s, settings);
			lastTile = i.getTarget() == Target.CELL_TILE || i.getTarget() == Target.BARRIER ? i.getLocation() : null;
			if (lastTileSeen != null && lastTile != null && !lastTile.equals(lastTileSeen))
			{
				note("TILE_SWITCH", i.getStep() + " moved from " + lastTileSeen + " to " + lastTile);
			}
			lastTileSeen = lastTile;
			observe(s, i);
			int fragBefore = fragments;
			int essBefore = essence + pouch;
			RateTracker.Activity activity = execute(i, s);
			rates.tick(activity, fragments - fragBefore, essence + pouch - essBefore);
			rates.samplePower(tick, (int) power);
			detect(s, i);
			tick++;
		}
		if (phase != GamePhase.ENDED)
		{
			note("NO_CLOSE", "the game never closed within " + cfg.maxTicks + " ticks");
		}
		leftovers();
		return score();
	}

	private void stepWorld()
	{
		if (phase == GamePhase.CLOSING)
		{
			if (tick >= closingUntil)
			{
				phase = GamePhase.ENDED;
			}
			return;
		}
		power += cfg.crowdPowerPerTick;
		if (power >= cfg.maxPower)
		{
			power = cfg.maxPower;
			phase = GamePhase.CLOSING;
			closeTick = tick;
			closingUntil = tick + cfg.closingTicks;
			portalOpen = false;
			if (location == Location.HUGE_REMAINS)
			{
				location = Location.TEMPLE;
			}
			timeline.add(tick + ": rift closes");
			return;
		}
		if (tick >= nextRotation)
		{
			rotateAltars();
			nextRotation += cfg.altarRotationTicks;
		}
		if (portalOpen && tick >= portalClosesAt)
		{
			portalOpen = false;
			if (location == Location.HUGE_REMAINS)
			{
				location = Location.TEMPLE;
				pos = portalPos;
				timeline.add(tick + ": ejected from the huge remains");
			}
			if (!portalEntered)
			{
				if (portalTicksInTemple >= cfg.portalOpenTicks - 2)
				{
					portalsMissed++;
					note("PORTAL_MISSED", "a portal stayed open " + cfg.portalOpenTicks + " ticks while the player was in the temple with room");
				}
			}
		}
		if (!portalOpen && tick >= nextPortalSpawn)
		{
			portalOpen = true;
			portalPos = Temple.PORTAL_SPAWNS.get(rng.nextInt(Temple.PORTAL_SPAWNS.size()));
			portalClosesAt = tick + cfg.portalOpenTicks;
			lastPortalSpawn = tick;
			nextPortalSpawn = tick + cfg.portalIntervalTicks;
			anyPortal = true;
			portalEntered = false;
			portalTicksInTemple = 0;
			timeline.add(tick + ": portal opens at " + portalPos.getX() + "," + portalPos.getY());
		}
		if (powerPercent() >= 60)
		{
			for (int k = barriers.size() - 1; k >= 0; k--)
			{
				Barrier b = barriers.get(k);
				b.health -= cfg.barrierDecay / b.tier.getRank();
				if (b.health <= 0)
				{
					barriers.remove(k);
					brokenTiles.add(b.tile);
					timeline.add(tick + ": " + b.tier.getLabel() + " barrier broke");
				}
			}
		}
	}

	private void rotateAltars()
	{
		List<Altar> elem = new ArrayList<>();
		List<Altar> cat = new ArrayList<>();
		for (Altar a : Altar.values())
		{
			if (a == activeElemental || a == activeCatalytic || a.getLevelRequired() > cfg.runecraftLevel)
			{
				continue;
			}
			(a.getAlignment() == Alignment.ELEMENTAL ? elem : cat).add(a);
		}
		activeElemental = elem.get(rng.nextInt(elem.size()));
		activeCatalytic = cat.get(rng.nextInt(cat.size()));
		timeline.add(tick + ": altars " + activeElemental.getLabel() + " / " + activeCatalytic.getLabel());
	}

	// ---- the snapshot the planner sees ------------------------------------------------------

	private int freeSlots()
	{
		int used = (cfg.pickaxeWorn ? 0 : 1) + 1 + 1; // chisel, pouch
		used += unchargedCells > 0 ? 1 : 0;
		used += chargedCell != null ? 1 : 0;
		used += fragments > 0 ? 1 : 0;
		used += essence;
		for (int n : stones.values())
		{
			used += n > 0 ? 1 : 0;
		}
		used += runes.size();
		return Math.max(0, INVENTORY - used);
	}

	private int powerPercent()
	{
		return (int) Math.round(100.0 * power / cfg.maxPower);
	}

	Snapshot snapshot()
	{
		int sinceStart = seconds(tick);
		int sinceLastPortal = lastPortalSpawn >= 0 ? seconds(tick - lastPortalSpawn) : -1;
		int toNextPortal = -1;
		if (phase == GamePhase.ACTIVE && !portalOpen)
		{
			toNextPortal = Math.max(0, seconds(nextPortalSpawn - tick));
		}
		List<BarrierState> bs = new ArrayList<>();
		for (Barrier b : barriers)
		{
			bs.add(new BarrierState(b.tile, b.tier, b.health < 100 ? (int) Math.max(0, b.health) : -1));
		}
		List<WorldPoint> empty = new ArrayList<>();
		for (WorldPoint t : Temple.CELL_TILES)
		{
			boolean taken = brokenTiles.contains(t);
			for (Barrier b : barriers)
			{
				taken |= b.tile.equals(t);
			}
			if (!taken)
			{
				empty.add(t);
			}
		}
		Map<Target, Integer> travel = new EnumMap<>(Target.class);
		for (Target t : Target.values())
		{
			WorldPoint p = Temple.landmark(t, pos, portalOpen ? portalPos : null);
			if (p != null)
			{
				travel.put(t, Temple.walkTicks(pos, p));
			}
		}
		if (!empty.isEmpty())
		{
			travel.put(Target.CELL_TILE, Temple.walkTicks(pos, Temple.nearest(empty, pos)));
		}
		Map<Target, Integer> back = new EnumMap<>(Target.class);
		back.put(Target.LARGE_REMAINS, Temple.walkTicks(Temple.LARGE_REMAINS, Temple.WORKBENCH));
		back.put(Target.GUARDIAN_REMAINS, Temple.walkTicks(Temple.nearest(Temple.PARTS, pos), Temple.WORKBENCH));
		Map<Altar, Integer> guardianTravel = new EnumMap<>(Altar.class);
		for (Map.Entry<Altar, WorldPoint> g : Temple.GUARDIANS.entrySet())
		{
			guardianTravel.put(g.getKey(), Temple.walkTicks(pos, g.getValue()));
		}
		boolean inTemple = location == Location.TEMPLE;
		return Snapshot.builder()
			.location(location)
			.altarRoom(location == Location.ALTAR_ROOM ? altarRoom : null)
			.playerLocation(pos)
			.phase(phase)
			.secondsSinceStart(sinceStart)
			.elementalEnergy(elemental)
			.catalyticEnergy(catalytic)
			.power((int) power)
			.maxPower(cfg.maxPower)
			.activeElemental(activeElemental)
			.activeCatalytic(activeCatalytic)
			.altarSecondsRemaining(Math.max(0, seconds(nextRotation - tick)))
			.portalOpen(inTemple || location == Location.HUGE_REMAINS ? portalOpen : false)
			.portalSecondsRemaining(portalOpen ? seconds(portalClosesAt - tick) : -1)
			.portalDirection(portalOpen ? "north" : null)
			.secondsSinceLastPortal(sinceLastPortal)
			.secondsToNextPortal(toNextPortal)
			.anyPortalThisGame(anyPortal)
			.guardiansActive(Math.min(10, cfg.guardiansFromCrowd + playerGuardians))
			.guardiansMax(10)
			.fragments(fragments)
			.essence(essence)
			.elementalStones(stones.get(Alignment.ELEMENTAL))
			.catalyticStones(stones.get(Alignment.CATALYTIC))
			.chargedCell(chargedCell)
			.unchargedCells(unchargedCells)
			.chisel(true)
			.pickaxe(true)
			.freeSlots(freeSlots())
			.depositableRunes(runes.size())
			.pouches(ImmutableList.of(new PouchState(PouchType.COLOSSAL, pouch, cfg.pouchCapacity, false)))
			.runecraftLevel(cfg.runecraftLevel)
			.agilityLevel(cfg.agilityLevel)
			.magicLevel(90)
			.barriers(bs)
			.emptyTiles(empty)
			.brokenTiles(new ArrayList<>(brokenTiles))
			.portalGuardians(Temple.GUARDIANS)
			.fragmentsPerTick(rates.largePerTick())
			.partsPerTick(rates.partsPerTick())
			.craftPerTick(rates.craftPerTick())
			.hugePerTick(rates.hugePerTick())
			.secondsToClose(phase == GamePhase.ACTIVE ? rates.secondsToClose((int) power, cfg.maxPower) : -1)
			.travelTicks(travel)
			.returnTicks(back)
			.guardianTravelTicks(guardianTravel)
			.build();
	}

	private static int seconds(int ticks)
	{
		return (int) Math.round(ticks * 0.6);
	}

	// ---- carrying out an instruction ----------------------------------------------------

	private double gain(double rate)
	{
		return rate * (0.5 + rng.nextDouble());
	}

	/** Moves the player toward {@code dest} at running pace; true once adjacent. */
	private boolean arrive(WorldPoint dest)
	{
		if (busy > 0)
		{
			busy--;
			return false;
		}
		if (Temple.walkTicks(pos, dest) <= 1)
		{
			return true;
		}
		WorldPoint waypoint = dest;
		if (Temple.beyondRubble(pos) != Temple.beyondRubble(dest))
		{
			WorldPoint near = Temple.beyondRubble(pos) ? Temple.RUBBLE_EAST : Temple.RUBBLE_WEST;
			if (Temple.walkTicks(pos, near) <= 1)
			{
				pos = Temple.beyondRubble(pos) ? Temple.RUBBLE_WEST : Temple.RUBBLE_EAST;
				busy = Temple.RUBBLE_COST - 1;
				return false;
			}
			waypoint = near;
		}
		for (int step = 0; step < 2; step++)
		{
			if (pos.equals(waypoint))
			{
				break;
			}
			int dx = Integer.signum(waypoint.getX() - pos.getX());
			int dy = Integer.signum(waypoint.getY() - pos.getY());
			pos = new WorldPoint(pos.getX() + dx, pos.getY() + dy, 0);
		}
		return false;
	}

	private boolean wait1()
	{
		if (busy > 0)
		{
			busy--;
			return false;
		}
		return true;
	}

	private RateTracker.Activity execute(Instruction i, Snapshot s)
	{
		Step step = i.getStep();
		WorldPoint dest = i.getLocation() != null ? i.getLocation() : Temple.landmark(i.getTarget(), pos, portalPos);
		if (step == Step.GO_TO_ALTAR && i.getAltar() != null)
		{
			dest = Temple.GUARDIANS.get(i.getAltar());
		}
		switch (step)
		{
			case MINE_FRAGMENTS:
			case MINE_FRAGMENTS_FOR_REPAIR:
			{
				if (dest == null || !arrive(dest))
				{
					return RateTracker.Activity.NONE;
				}
				boolean large = i.getTarget() == Target.LARGE_REMAINS;
				if (fragments == 0 && freeSlots() == 0)
				{
					note("MINE_NO_ROOM", "told to mine with no free slot for a fragment stack");
					return RateTracker.Activity.NONE;
				}
				fragAccum += gain(large ? cfg.largeRate : cfg.partsRate);
				int n = (int) fragAccum;
				fragAccum -= n;
				fragments += n;
				return large ? RateTracker.Activity.MINING_LARGE : RateTracker.Activity.MINING_PARTS;
			}
			case CRAFT_ESSENCE:
			{
				if (!arrive(Temple.WORKBENCH))
				{
					return RateTracker.Activity.NONE;
				}
				if (fragments == 0 || freeSlots() == 0)
				{
					note("CRAFT_NOTHING", "told to craft with fragments=" + fragments + " free=" + freeSlots());
					return RateTracker.Activity.NONE;
				}
				craftAccum += gain(cfg.craftRate);
				int n = Math.min((int) craftAccum, Math.min(fragments, freeSlots()));
				craftAccum -= n;
				essence += n;
				fragments -= n;
				return RateTracker.Activity.CRAFTING_ESSENCE;
			}
			case FILL_POUCHES:
			{
				if (!wait1())
				{
					return RateTracker.Activity.NONE;
				}
				int n = Math.min(essence, cfg.pouchCapacity - pouch);
				if (n <= 0)
				{
					note("FILL_NOTHING", "told to fill pouches with essence=" + essence + " pouch=" + pouch);
				}
				essence -= n;
				pouch += n;
				busy = 1;
				return location == Location.HUGE_REMAINS ? RateTracker.Activity.MINING_HUGE : RateTracker.Activity.CRAFTING_ESSENCE;
			}
			case GO_TO_ALTAR:
			{
				if (i.getAltar() == null)
				{
					note("NO_ALTAR", "GO_TO_ALTAR without an altar");
					return RateTracker.Activity.NONE;
				}
				if (!arrive(dest))
				{
					return RateTracker.Activity.NONE;
				}
				if (i.getAltar() != activeElemental && i.getAltar() != activeCatalytic)
				{
					wastedGuardianArrivals++;
					note("GUARDIAN_INACTIVE", "arrived at the " + i.getAltar().getLabel() + " guardian after the rotation closed it");
					busy = 1;
					return RateTracker.Activity.NONE;
				}
				location = Location.ALTAR_ROOM;
				altarRoom = i.getAltar();
				insideRemaining = i.getAltar().getInsideTiles();
				cellChargedThisTrip = false;
				altarTrips++;
				busy = 1;
				timeline.add(tick + ": enters " + altarRoom.getLabel() + " with " + (essence + pouch) + " essence");
				return RateTracker.Activity.NONE;
			}
			case CRAFT_RUNES:
			{
				if (location != Location.ALTAR_ROOM)
				{
					note("CRAFT_OUTSIDE", "CRAFT_RUNES while not in an altar room");
					return RateTracker.Activity.NONE;
				}
				if (!wait1())
				{
					return RateTracker.Activity.NONE;
				}
				if (insideRemaining > 0)
				{
					insideRemaining = Math.max(0, insideRemaining - 2);
					return RateTracker.Activity.NONE;
				}
				if (essence > 0)
				{
					Alignment a = altarRoom.getAlignment();
					stones.put(a, stones.get(a) + essence);
					runes.add(altarRoom);
					essence = 0;
					if (!cellChargedThisTrip && unchargedCells > 0 && chargedCell == null)
					{
						unchargedCells--;
						chargedCell = altarRoom.getCellTier();
						cellChargedThisTrip = true;
					}
					busy = 2;
					return RateTracker.Activity.NONE;
				}
				if (pouch > 0)
				{
					int n = Math.min(pouch, freeSlots());
					pouch -= n;
					essence += n;
					busy = 1;
					return RateTracker.Activity.NONE;
				}
				note("CRAFT_EMPTY", "CRAFT_RUNES with no essence anywhere");
				return RateTracker.Activity.NONE;
			}
			case LEAVE_ALTAR:
			{
				if (location != Location.ALTAR_ROOM)
				{
					note("LEAVE_OUTSIDE", "LEAVE_ALTAR while not in an altar room");
					return RateTracker.Activity.NONE;
				}
				if (!wait1())
				{
					return RateTracker.Activity.NONE;
				}
				if (insideRemaining < altarRoom.getInsideTiles())
				{
					insideRemaining = Math.min(altarRoom.getInsideTiles(), insideRemaining + 2);
					return RateTracker.Activity.NONE;
				}
				location = Location.TEMPLE;
				pos = Temple.GUARDIANS.get(altarRoom);
				busy = 1;
				return RateTracker.Activity.NONE;
			}
			case POWER_UP:
			{
				if (!arrive(Temple.GREAT_GUARDIAN))
				{
					return RateTracker.Activity.NONE;
				}
				int total = 0;
				for (Alignment a : Alignment.values())
				{
					int n = stones.get(a);
					total += n;
					if (a == Alignment.ELEMENTAL)
					{
						elemental += 2 * n;
					}
					else
					{
						catalytic += 2 * n;
					}
					stones.put(a, 0);
				}
				if (total == 0)
				{
					note("POWER_NOTHING", "told to power up with no stones");
				}
				power = Math.min(cfg.maxPower, power + total * cfg.powerPerStone);
				rates.noteOwnHandIn(tick);
				busy = 1;
				return RateTracker.Activity.NONE;
			}
			case HOLD_STONES:
				holdTicks++;
				return RateTracker.Activity.NONE;
			case CELL_GUARDIAN:
			case PRE_BUILD_GUARDIAN:
			{
				if (chargedCell == null)
				{
					note("CELL_NONE", step + " with no charged cell");
					return RateTracker.Activity.NONE;
				}
				if (dest == null || !arrive(dest))
				{
					return RateTracker.Activity.NONE;
				}
				Alignment a = i.getTarget() == Target.ESSENCE_PILE_ELEMENTAL ? Alignment.ELEMENTAL : Alignment.CATALYTIC;
				if (a == Alignment.ELEMENTAL)
				{
					elemental += chargedCell.getGuardianEnergy();
				}
				else
				{
					catalytic += chargedCell.getGuardianEnergy();
				}
				playerGuardians++;
				cellsUsed++;
				timeline.add(tick + ": " + chargedCell.getLabel() + " " + a.getLabel() + " guardian");
				chargedCell = null;
				busy = 1;
				return RateTracker.Activity.NONE;
			}
			case CELL_BARRIER_BUILD:
			case PRE_BUILD_BARRIER:
			case CELL_BARRIER_UPGRADE:
			case CELL_BARRIER_RECHARGE:
			{
				if (chargedCell == null)
				{
					note("CELL_NONE", step + " with no charged cell");
					return RateTracker.Activity.NONE;
				}
				if (dest == null)
				{
					note("NO_TARGET", step + " without a tile");
					return RateTracker.Activity.NONE;
				}
				if (!arrive(dest))
				{
					return RateTracker.Activity.NONE;
				}
				Barrier at = null;
				for (Barrier b : barriers)
				{
					if (b.tile.equals(dest))
					{
						at = b;
					}
				}
				if (step == Step.CELL_BARRIER_BUILD || step == Step.PRE_BUILD_BARRIER)
				{
					if (at != null || brokenTiles.contains(dest))
					{
						note("BUILD_ON_TAKEN", "build a barrier on a tile that is " + (at != null ? "already built" : "broken"));
						return RateTracker.Activity.NONE;
					}
					barriers.add(new Barrier(dest, chargedCell));
					elemental += 2;
					catalytic += 2;
					timeline.add(tick + ": " + chargedCell.getLabel() + " barrier built");
				}
				else if (at == null)
				{
					note("NO_BARRIER", step + " on a tile with no barrier");
					return RateTracker.Activity.NONE;
				}
				else if (step == Step.CELL_BARRIER_UPGRADE)
				{
					if (at.tier.getRank() >= chargedCell.getRank())
					{
						note("UPGRADE_DOWN", "strengthen a " + at.tier.getLabel() + " barrier with a " + chargedCell.getLabel() + " cell");
					}
					at.tier = chargedCell;
					at.health = 100;
					elemental += chargedCell.getStrengthenEnergy();
					catalytic += chargedCell.getStrengthenEnergy();
					timeline.add(tick + ": barrier strengthened to " + chargedCell.getLabel());
				}
				else
				{
					at.health = 100;
					elemental += chargedCell.getRechargeEnergy();
					catalytic += chargedCell.getRechargeEnergy();
					timeline.add(tick + ": " + at.tier.getLabel() + " barrier recharged with " + chargedCell.getLabel());
				}
				chargedCell = null;
				cellsUsed++;
				busy = 1;
				return RateTracker.Activity.NONE;
			}
			case REPAIR_TILE:
			{
				if (dest == null || !arrive(dest))
				{
					return RateTracker.Activity.NONE;
				}
				if (!brokenTiles.remove(dest))
				{
					note("REPAIR_NOT_BROKEN", "repair a tile that is not broken");
					return RateTracker.Activity.NONE;
				}
				fragments -= 12;
				elemental += 25;
				catalytic += 25;
				busy = 1;
				timeline.add(tick + ": tile repaired");
				return RateTracker.Activity.NONE;
			}
			case DEPOSIT_RUNES:
			case FREE_SPACE:
			{
				if (!arrive(Temple.DEPOSIT_POOL))
				{
					return RateTracker.Activity.NONE;
				}
				if (runes.isEmpty())
				{
					note("DEPOSIT_NOTHING", step + " with no runes");
				}
				runes.clear();
				busy = 1;
				return RateTracker.Activity.NONE;
			}
			case PRE_TAKE_CELLS:
			{
				if (!arrive(Temple.CELL_TABLE))
				{
					return RateTracker.Activity.NONE;
				}
				unchargedCells = 10;
				busy = 1;
				return RateTracker.Activity.NONE;
			}
			case DROP_ESSENCE:
			{
				if (!wait1())
				{
					return RateTracker.Activity.NONE;
				}
				if (essence <= 0)
				{
					note("DROP_NOTHING", "told to drop essence with none in the inventory");
					return RateTracker.Activity.NONE;
				}
				essence--;
				busy = 1;
				timeline.add(tick + ": drops one essence");
				return RateTracker.Activity.NONE;
			}
			case PRE_TAKE_WEAK_CELL:
				note("WEAK_CELL_MIDGAME", "the weak cell table only exists between games");
				return RateTracker.Activity.NONE;
			case ENTER_PORTAL:
			{
				if (!portalOpen)
				{
					note("PORTAL_CLOSED", "ENTER_PORTAL with no portal open");
					return RateTracker.Activity.NONE;
				}
				if (!arrive(portalPos))
				{
					return RateTracker.Activity.NONE;
				}
				if (s.capacity() < 10)
				{
					note("PORTAL_LOW_ROOM", "entered the portal with room for only " + s.capacity());
				}
				location = Location.HUGE_REMAINS;
				portalEntered = true;
				portalsUsed++;
				busy = 1;
				timeline.add(tick + ": enters the portal with room for " + s.capacity());
				return RateTracker.Activity.NONE;
			}
			case MINE_HUGE_REMAINS:
			{
				if (location != Location.HUGE_REMAINS)
				{
					note("HUGE_OUTSIDE", "MINE_HUGE_REMAINS while not inside");
					return RateTracker.Activity.NONE;
				}
				if (!wait1())
				{
					return RateTracker.Activity.NONE;
				}
				craftAccum += gain(cfg.hugeRate);
				int n = Math.min((int) craftAccum, freeSlots());
				craftAccum -= n;
				essence += n;
				return RateTracker.Activity.MINING_HUGE;
			}
			case LEAVE_HUGE_REMAINS:
			{
				if (!wait1())
				{
					return RateTracker.Activity.NONE;
				}
				location = Location.TEMPLE;
				pos = portalPos;
				busy = 2;
				timeline.add(tick + ": leaves the huge remains with " + (essence + pouch) + " essence");
				return RateTracker.Activity.NONE;
			}
			case PRE_POSITION:
			{
				if (dest != null)
				{
					arrive(dest);
				}
				return RateTracker.Activity.NONE;
			}
			case WAIT_FOR_PORTAL:
			case WAIT_FOR_ALTAR:
			case IDLE_DEFEND:
			case PRE_WAIT:
			case GAME_OVER:
				return RateTracker.Activity.NONE;
			default:
				note("UNHANDLED", "step " + step + " in phase " + phase + " at " + location);
				return RateTracker.Activity.NONE;
		}
	}

	// ---- detectors -----------------------------------------------------------------------------

	private void note(String kind, String detail)
	{
		findings.add(new Finding(tick, kind, detail));
	}

	private void observe(Snapshot s, Instruction i)
	{
		if (i.getStep() != lastStep)
		{
			stepChanges.addLast(new int[]{tick, i.getStep().ordinal()});
			timeline.add(tick + ": " + i.getStep() + "  \"" + i.getHeadline() + "\"  " + i.getDetail());
			lastStep = i.getStep();
		}
		while (!stepChanges.isEmpty() && tick - stepChanges.peekFirst()[0] > 12)
		{
			stepChanges.pollFirst();
		}
		if (stepChanges.size() >= 4 && tick - lastOscillationReport > 12)
		{
			Set<Integer> distinct = new HashSet<>();
			for (int[] c : stepChanges)
			{
				distinct.add(c[1]);
			}
			if (distinct.size() <= 2)
			{
				StringBuilder sb = new StringBuilder();
				for (int[] c : stepChanges)
				{
					sb.append(Step.values()[c[1]]).append(' ');
				}
				note("OSCILLATION", sb.toString().trim());
				lastOscillationReport = tick;
			}
		}
		if (portalOpen && location == Location.TEMPLE && s.capacity() >= 10 && !s.energyCapped())
		{
			portalTicksInTemple++;
		}
	}

	private void detect(Snapshot s, Instruction i)
	{
		Step step = i.getStep();
		boolean movingStep = step == Step.GO_TO_ALTAR || step == Step.ENTER_PORTAL || step == Step.MINE_FRAGMENTS
			|| step == Step.CRAFT_ESSENCE || step == Step.POWER_UP || step == Step.DEPOSIT_RUNES || step == Step.REPAIR_TILE
			|| step == Step.CELL_GUARDIAN || step == Step.CELL_BARRIER_BUILD || step == Step.CELL_BARRIER_UPGRADE
			|| step == Step.CELL_BARRIER_RECHARGE || step == Step.PRE_TAKE_CELLS;
		if (movingStep && i.getTarget() == Target.NONE && i.getLocation() == null)
		{
			note("NO_TARGET", step + " \"" + i.getHeadline() + "\" points at nothing");
		}

		boolean idle = step == Step.IDLE_DEFEND || step == Step.WAIT_FOR_ALTAR || step == Step.WAIT_FOR_PORTAL
			|| step == Step.PRE_POSITION;
		boolean workAvailable = (fragments > 0 && s.capacity() > 0) || stones.get(Alignment.ELEMENTAL) + stones.get(Alignment.CATALYTIC) > 0
			|| chargedCell != null || (s.essenceTotal() > 0 && (activeElemental != null || activeCatalytic != null));
		int close = s.getSecondsToClose();
		boolean designedWait = step == Step.WAIT_FOR_ALTAR && s.getAltarSecondsRemaining() >= 0
			&& s.getAltarSecondsRemaining() <= settings.getWeakAltarWaitSeconds();
		if (idle && workAvailable && !designedWait && (close < 0 || close > 90) && !s.energyCapped())
		{
			idleTicks++;
			if (idleTicks == 30)
			{
				note("IDLE_WITH_WORK", step + " \"" + i.getHeadline() + "\" for 30 ticks with fragments=" + fragments
					+ " essence=" + s.essenceTotal() + " stones=" + s.stonesTotal() + " cell=" + chargedCell + " close=" + close);
			}
		}
		else
		{
			idleTicks = 0;
		}

		// HOLD_STONES is only issued when nothing else is worth doing, and the planner caps the
		// hold at the grace-portal window (95 s), so only a hold past that window is a defect.
		if (holdTicks == 170)
		{
			note("HOLD_LONG", "held stones for 170 ticks, past the grace-portal window");
		}
		// The cell is spent after the stones are handed in, and an open portal outranks it by
		// design, so only ticks spent on ordinary temple work with a cell in the bag count.
		boolean templeWork = location == Location.TEMPLE && (step == Step.MINE_FRAGMENTS || step == Step.CRAFT_ESSENCE
			|| step == Step.FILL_POUCHES || step == Step.IDLE_DEFEND || step == Step.WAIT_FOR_ALTAR || step == Step.WAIT_FOR_PORTAL
			|| step == Step.HOLD_STONES || step == Step.DEPOSIT_RUNES);
		boolean nearBarrier = false;
		for (Barrier b : barriers)
		{
			nearBarrier |= b.tile.distanceTo2D(pos) <= 10;
		}
		if (chargedCell != null && phase == GamePhase.ACTIVE && templeWork && nearBarrier)
		{
			cellHeldTicks++;
			if (cellHeldTicks == 20)
			{
				note("CELL_HELD", "a " + chargedCell.getLabel() + " cell carried through 20 ticks of temple work beside a barrier, last " + step);
			}
		}
		else if (chargedCell == null)
		{
			cellHeldTicks = 0;
		}

		String key = step + "|" + pos + "|" + fragments + "|" + essence + "|" + pouch + "|" + elemental + "|" + catalytic
			+ "|" + location + "|" + insideRemaining + "|" + s.stonesTotal() + "|" + runes.size();
		if (key.equals(lastStateKey) && !idle && step != Step.HOLD_STONES)
		{
			stuckTicks++;
			if (stuckTicks == 25)
			{
				note("STUCK", step + " \"" + i.getHeadline() + "\" made no progress for 25 ticks at " + pos);
			}
		}
		else
		{
			stuckTicks = 0;
		}
		lastStateKey = key;
	}

	private void leftovers()
	{
		int st = stones.get(Alignment.ELEMENTAL) + stones.get(Alignment.CATALYTIC);
		if (st > 0)
		{
			note("STONES_LEFT", st + " stones never handed in (" + 2 * st + " energy lost)");
		}
		if (essence + pouch >= 5)
		{
			note("ESSENCE_LEFT", (essence + pouch) + " essence never crafted");
		}
		// A cell charged on the last trip is spent by the between-games checklist, so only a
		// cell carried through the active game is a finding (see CELL_HELD).
		if (fragments >= 30)
		{
			note("FRAGMENTS_LEFT", fragments + " fragments never crafted");
		}
	}

	String score()
	{
		return String.format("%-5s %-8s seed=%-3d  E=%4d C=%4d  points=%2d  game=%3ds  trips=%d portals=%d/%d cells=%d  findings=%d",
			cfg.name, cfg.strategy.name(), cfg.seed, elemental, catalytic, Math.min(10, elemental / 100) + Math.min(10, catalytic / 100),
			Math.round((closeTick < 0 ? tick : closeTick) * 0.6), altarTrips, portalsUsed, portalsUsed + portalsMissed, cellsUsed, findings.size());
	}

	int energy()
	{
		return elemental + catalytic;
	}

	Map<String, Integer> findingCounts()
	{
		Map<String, Integer> m = new HashMap<>();
		for (Finding f : findings)
		{
			m.merge(f.kind, 1, Integer::sum);
		}
		return m;
	}
}
