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
package com.fabulousotter.gotr.trace;

import com.fabulousotter.gotr.GotrAutopilotConfig;
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.state.BarrierState;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.RateTracker;
import com.fabulousotter.gotr.state.Snapshot;
import com.fabulousotter.gotr.state.StateTracker;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;

/**
 * Writes each game as a CSV file under {@code .runelite/gotr-autopilot/traces}: one row per
 * tick with the HUD's raw figures, the player's position, work and inventory and the
 * barriers' health, and a row for each game message. The simulator's calibration test reads
 * the files back to measure the real game: the crowd's hand-in rate, the portal and altar
 * clocks, mining and crafting rates, barrier damage. Rows are formatted on the client thread
 * and written on a thread of their own, so the client never waits on the disk.
 */
@Slf4j
@Singleton
public class GameTrace
{
	/** The HUD script's figures, in the order {@link StateTracker#hudArgs()} gives them. */
	public static final int HUD_ELEMENTAL = 0;
	public static final int HUD_CATALYTIC = 1;
	public static final int HUD_POWER = 2;
	public static final int HUD_MAX_POWER = 3;
	public static final int HUD_PORTAL_LOCATION = 4;
	public static final int HUD_ELEMENTAL_INDEX = 5;
	public static final int HUD_CATALYTIC_INDEX = 6;
	public static final int HUD_GUARDIANS = 7;
	public static final int HUD_GUARDIANS_MAX = 8;
	public static final int HUD_GUARDIAN_TICKS = 9;
	public static final int HUD_PORTAL_TICKS = 10;
	public static final int HUD_TICK = 11;
	public static final int HUD_FIELDS = 12;

	public static final String HEADER = "kind,tick,phase,step,loc,x,y,activity,"
		+ "elemental,catalytic,power,maxPower,portalLoc,elemIdx,catIdx,guardians,guardiansMax,altarTicks,portalTicks,hudTick,"
		+ "portalObj,fragments,essence,pouch,stones,cell,cells,free,barriers,"
		+ "capacity,toPortal,toClose,largeRate,craftRate";
	public static final String START_MESSAGE = "The rift becomes active!";

	private static final String TRACE_DIR = "gotr-autopilot" + File.separator + "traces";
	private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss-SSS");

	private final Client client;
	private final StateTracker tracker;
	private final GotrAutopilotConfig config;
	private ExecutorService writer;
	// Touched on the writer thread only.
	private Writer out;
	private boolean tracing;

	@Inject
	GameTrace(Client client, StateTracker tracker, GotrAutopilotConfig config)
	{
		this.client = client;
		this.tracker = tracker;
		this.config = config;
	}

	public void startUp()
	{
		writer = Executors.newSingleThreadExecutor(r ->
		{
			Thread thread = new Thread(r, "gotr-autopilot-trace");
			thread.setDaemon(true);
			return thread;
		});
	}

	/** Queues the close of the open file; shutdown() lets the queue drain without waiting on it. */
	public void shutDown()
	{
		closeFile();
		if (writer != null)
		{
			writer.shutdown();
			writer = null;
		}
	}

	/** One row for this tick, or the end of the file once the player has left the minigame. */
	public void tick(Snapshot s, Step step)
	{
		if (!config.calibrationTrace() || s.getLocation() == Location.OUTSIDE)
		{
			closeFile();
			return;
		}
		// The lobby has nothing to measure: the file stays open for the next game, without rows.
		if (s.getLocation() == Location.LOBBY)
		{
			return;
		}
		if (!tracing)
		{
			openFile();
		}
		submit(row(client.getTickCount(), s, step, tracker.getLastActivity(), tracker.hudArgs(), tracker.getPortal() != null));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!tracing || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}
		String msg = event.getMessage();
		if (msg.contains(START_MESSAGE))
		{
			// One file per game, opening with its start.
			closeFile();
			openFile();
		}
		submit(event(client.getTickCount(), msg));
	}

	/** The per-tick row. {@code hud} holds the HUD script's figures in {@link #HUD_ELEMENTAL} order. */
	public static String row(int tick, Snapshot s, Step step, RateTracker.Activity activity, int[] hud, boolean portalObject)
	{
		WorldPoint at = s.getPlayerLocation();
		StringBuilder sb = new StringBuilder(200);
		sb.append("T,").append(tick).append(',').append(s.getPhase()).append(',').append(step).append(',').append(s.getLocation())
			.append(',').append(at == null ? -1 : at.getX()).append(',').append(at == null ? -1 : at.getY())
			.append(',').append(activity);
		for (int k = 0; k < HUD_FIELDS; k++)
		{
			sb.append(',').append(k < hud.length ? hud[k] : -1);
		}
		sb.append(',').append(portalObject ? 1 : 0)
			.append(',').append(s.getFragments()).append(',').append(s.getEssence()).append(',').append(s.pouchStored())
			.append(',').append(s.stonesTotal()).append(',').append(s.getChargedCell() == null ? "" : s.getChargedCell().name())
			.append(',').append(s.getUnchargedCells()).append(',').append(s.getFreeSlots()).append(',');
		boolean first = true;
		for (BarrierState b : s.getBarriers())
		{
			if (!first)
			{
				sb.append(';');
			}
			first = false;
			sb.append(b.getLocation().getX()).append('_').append(b.getLocation().getY())
				.append(':').append(b.getTier().name()).append(':').append(b.getHealthPercent());
		}
		// What the planner was told, derived: room, the two clocks and the measured rates.
		sb.append(',').append(s.capacity()).append(',').append(s.getSecondsToNextPortal()).append(',').append(s.getSecondsToClose())
			.append(',').append(String.format("%.2f", s.getFragmentsPerTick())).append(',').append(String.format("%.2f", s.getCraftPerTick()));
		return sb.toString();
	}

	/** A game message on its tick. */
	public static String event(int tick, String text)
	{
		return "E," + tick + ",\"" + text.replace("\"", "\"\"") + "\"";
	}

	private void openFile()
	{
		if (writer == null)
		{
			return;
		}
		tracing = true;
		File dir = new File(RuneLite.RUNELITE_DIR, TRACE_DIR);
		File file = new File(dir, LocalDateTime.now().format(FILE_NAME) + ".csv");
		log.debug("Tracing the game to {}", file);
		writer.execute(() ->
		{
			closeOut();
			try
			{
				if (!dir.isDirectory() && !dir.mkdirs())
				{
					log.debug("Cannot create {}", dir);
					return;
				}
				out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
				out.write(HEADER);
				out.write('\n');
				out.flush();
			}
			catch (IOException ex)
			{
				log.debug("Cannot open the trace {}", file, ex);
				out = null;
			}
		});
	}

	private void closeFile()
	{
		if (!tracing)
		{
			return;
		}
		tracing = false;
		if (writer != null)
		{
			writer.execute(this::closeOut);
		}
	}

	private void closeOut()
	{
		if (out == null)
		{
			return;
		}
		try
		{
			out.close();
		}
		catch (IOException ex)
		{
			log.debug("Cannot close the trace", ex);
		}
		out = null;
	}

	private void submit(String line)
	{
		if (writer == null)
		{
			return;
		}
		writer.execute(() ->
		{
			if (out == null)
			{
				return;
			}
			try
			{
				out.write(line);
				out.write('\n');
				// A line a tick: flushed as it comes, so nothing is lost if the client dies.
				out.flush();
			}
			catch (IOException ex)
			{
				log.debug("Trace write failed", ex);
				closeOut();
			}
		});
	}
}
