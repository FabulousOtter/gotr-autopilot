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
package com.fabulousotter.gotr.overlay;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathfinderTest
{
	private final Client client = mock(Client.class);
	private final Player player = mock(Player.class);
	private final WorldView view = mock(WorldView.class);
	private final CollisionData collision = mock(CollisionData.class);
	private final int[][] flags = new int[12][12];
	private final Pathfinder pathfinder = new Pathfinder(client);
	private final List<Pathfinder.Transport> transports = Collections.emptyList();

	@Before
	public void setUp()
	{
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(view);
		when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
		when(collision.getFlags()).thenReturn(flags);
		moveTo(1, 1);
	}

	@Test
	public void reusesStationaryRoute()
	{
		Pathfinder.Path path = pathfinder.pathTo(8, 8, 8, 8, transports);
		assertFalse(path.isEmpty());
		assertSame(path, pathfinder.pathTo(8, 8, 8, 8, transports));
		moveTo(2, 2);
		assertNotSame(path, pathfinder.pathTo(8, 8, 8, 8, transports));
	}

	@Test
	public void landmarkSweepPreservesPlayerRoute()
	{
		Pathfinder.Path before = pathfinder.pathTo(5, 5, 5, 5, transports);
		assertTrue(pathfinder.distanceBetween(9, 1, 9, 1, 8, 8, 8, 8, transports) >= 0);
		pathfinder.pathTo(10, 10, 10, 10, transports);
		assertEquals(before, pathfinder.pathTo(5, 5, 5, 5, transports));
	}

	@Test
	public void clearInvalidatesPlayerAndLandmarkDistances()
	{
		assertTrue(pathfinder.distanceBetween(2, 2, 2, 2, 9, 9, 9, 9, transports) >= 0);
		Arrays.fill(flags[6], 0xFFFFFF);
		pathfinder.clear();
		assertEquals(-1, pathfinder.distanceTo(9, 9, 9, 9, transports));
		assertEquals(-1, pathfinder.distanceBetween(2, 2, 2, 2, 9, 9, 9, 9, transports));
	}

	@Test
	public void replacingCollisionMapInvalidatesSameSizedFields()
	{
		assertTrue(pathfinder.distanceBetween(2, 2, 2, 2, 9, 9, 9, 9, transports) >= 0);
		int[][] replacement = new int[12][12];
		Arrays.fill(replacement[6], 0xFFFFFF);
		when(collision.getFlags()).thenReturn(replacement);
		assertEquals(-1, pathfinder.distanceBetween(2, 2, 2, 2, 9, 9, 9, 9, transports));
	}

	@Test
	public void sceneBaseChangeInvalidatesWorldWaypoints()
	{
		Pathfinder.Path before = pathfinder.pathTo(8, 8, 8, 8, transports);
		when(view.getBaseX()).thenReturn(1);
		LocalPoint local = LocalPoint.fromScene(0, 1, view);
		when(player.getLocalLocation()).thenReturn(local);
		Pathfinder.Path after = pathfinder.pathTo(8, 8, 8, 8, transports);
		assertNotSame(before, after);
		assertTrue(after.getPoints().get(after.getPoints().size() - 1).getX() >= 8);
	}

	@Test
	public void planeChangeInvalidatesWaypointsEvenWithSharedCollisionData()
	{
		Pathfinder.Path before = pathfinder.pathTo(8, 8, 8, 8, transports);
		when(view.getPlane()).thenReturn(1);
		when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision, collision});
		Pathfinder.Path after = pathfinder.pathTo(8, 8, 8, 8, transports);
		assertNotSame(before, after);
		assertEquals(1, after.getPoints().get(0).getPlane());
	}

	@Test
	public void changingTransportsInvalidatesBothFields()
	{
		Arrays.fill(flags[6], 0xFFFFFF);
		Pathfinder.Transport shortcut = new Pathfinder.Transport(
			Collections.singleton(Pathfinder.tileKey(5, 2)),
			Collections.singleton(Pathfinder.tileKey(7, 2)), null, null);
		List<Pathfinder.Transport> shortcuts = Collections.singletonList(shortcut);
		assertTrue(pathfinder.distanceBetween(2, 2, 2, 2, 9, 9, 9, 9, shortcuts) >= 0);
		assertFalse(pathfinder.pathTo(9, 9, 9, 9, shortcuts).getClimbs().isEmpty());
		assertEquals(-1, pathfinder.distanceBetween(2, 2, 2, 2, 9, 9, 9, 9, transports));
		assertTrue(pathfinder.pathTo(9, 9, 9, 9, transports).isEmpty());
	}

	@Test
	public void reusedBuffersDoNotRetainClimbLegs()
	{
		Arrays.fill(flags[6], 0xFFFFFF);
		Pathfinder.Transport shortcut = new Pathfinder.Transport(
			Collections.singleton(Pathfinder.tileKey(5, 2)),
			Collections.singleton(Pathfinder.tileKey(7, 2)), null, null);
		List<Pathfinder.Transport> shortcuts = Collections.singletonList(shortcut);
		assertFalse(pathfinder.pathTo(9, 9, 9, 9, shortcuts).getClimbs().isEmpty());
		moveTo(8, 2);
		assertTrue(pathfinder.pathTo(9, 9, 9, 9, shortcuts).getClimbs().isEmpty());
	}

	private void moveTo(int x, int y)
	{
		LocalPoint local = LocalPoint.fromScene(x, y, view);
		when(player.getLocalLocation()).thenReturn(local);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
	}
}
