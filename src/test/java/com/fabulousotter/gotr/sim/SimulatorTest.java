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

import com.google.common.collect.ImmutableSet;
import com.fabulousotter.gotr.plan.Strategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Plays whole games headlessly and reports what the planner got wrong. The score table
 * compares strategies on the same worlds (same seeds); the findings list is the edge-case
 * hunt. Set the environment variable {@code GOTR_SIM_VERBOSE=true} to print each game's timeline.
 */
public class SimulatorTest
{
	private static final int SEEDS = 6;

	@Test
	public void everyStrategySurvivesAMassWorld()
	{
		List<GameSim> games = new ArrayList<>();
		for (Strategy strategy : Strategy.values())
		{
			for (int seed = 1; seed <= SEEDS; seed++)
			{
				games.add(new GameSim(SimConfig.mass().strategy(strategy).seed(seed)));
			}
		}
		for (int seed = 1; seed <= SEEDS; seed++)
		{
			games.add(new GameSim(SimConfig.solo().seed(seed)));
			games.add(new GameSim(SimConfig.solo().strategy(Strategy.MASS).seed(seed)));
			games.add(new GameSim(SimConfig.team().seed(seed)));
			games.add(new GameSim(SimConfig.team().strategy(Strategy.MASS).seed(seed)));
			games.add(new GameSim(SimConfig.mass().grace().seed(seed)));
			games.add(new GameSim(SimConfig.mass().strategy(Strategy.GENERAL).grace().seed(seed)));
			games.add(new GameSim(SimConfig.solo().grace().seed(seed)));
			games.add(new GameSim(SimConfig.solo().strategy(Strategy.MASS).grace().seed(seed)));
		}
		report(games);
	}

	static void report(List<GameSim> games)
	{
		boolean verbose = "true".equals(System.getenv("GOTR_SIM_VERBOSE"));
		Map<String, Integer> kinds = new TreeMap<>();
		Map<String, int[]> totals = new TreeMap<>();
		StringBuilder out = new StringBuilder("\n==== scores ====\n");
		StringBuilder findings = new StringBuilder("\n==== findings ====\n");
		for (GameSim g : games)
		{
			String line = g.run();
			out.append(line).append('\n');
			int[] t = totals.computeIfAbsent(g.cfg.name + " " + g.cfg.strategy.name(), k -> new int[2]);
			t[0] += g.energy();
			t[1]++;
			for (Map.Entry<String, Integer> e : g.findingCounts().entrySet())
			{
				kinds.merge(e.getKey(), e.getValue(), Integer::sum);
			}
			if (!g.findings.isEmpty())
			{
				findings.append("-- ").append(g.cfg.name).append(' ').append(g.cfg.strategy.name()).append(" seed=").append(g.cfg.seed).append('\n');
				for (GameSim.Finding f : g.findings)
				{
					findings.append("   ").append(f).append('\n');
				}
			}
			if (verbose)
			{
				findings.append("   timeline:\n");
				for (String s : g.timeline)
				{
					findings.append("     ").append(s).append('\n');
				}
			}
		}
		out.append("\n==== mean energy per world/strategy ====\n");
		for (Map.Entry<String, int[]> e : totals.entrySet())
		{
			out.append(String.format("%-14s %5d over %d games%n", e.getKey(), e.getValue()[0] / e.getValue()[1], e.getValue()[1]));
		}
		out.append("\n==== finding kinds ====\n");
		for (Map.Entry<String, Integer> e : kinds.entrySet())
		{
			out.append(String.format("%-20s %d%n", e.getKey(), e.getValue()));
		}
		System.out.println(out);
		System.out.println(findings);
		assertTrue("simulator ran no games", !games.isEmpty());
		StringBuilder hard = new StringBuilder();
		for (String kind : kinds.keySet())
		{
			if (!ADVISORY.contains(kind))
			{
				hard.append(kind).append('=').append(kinds.get(kind)).append(' ');
			}
		}
		assertTrue("planner defects: " + hard, hard.length() == 0);
	}

	/**
	 * Finding kinds that are reported but do not fail the build: what is left at the close is a
	 * property of the wiki strategies (they do not predict the close) and of where the game
	 * happened to end, not a wrong instruction.
	 */
	private static final Set<String> ADVISORY = ImmutableSet.of("ESSENCE_LEFT", "FRAGMENTS_LEFT", "STONES_LEFT");
}
