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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;

/**
 * Caches collision-map searches for route drawing and planner distances.
 */
public class Pathfinder
{
	/**
	 * Climb index k connects waypoints k and k + 1; -1 connects the player to the first waypoint.
	 */
	@Value
	public static class Path
	{
		public static final Path EMPTY = new Path(Collections.emptyList(), Collections.emptySet());

		List<WorldPoint> points;
		Set<Integer> climbs;

		public boolean isEmpty()
		{
			return points.isEmpty();
		}

		/**
		 * This route with a final step onto {@code tile}, for a target whose own tile cannot
		 * be walked on (a barrier, a cell tile): the search ends beside it, the line ends on it.
		 */
		public Path endingAt(WorldPoint tile)
		{
			if (points.isEmpty() || points.get(points.size() - 1).equals(tile))
			{
				return this;
			}
			List<WorldPoint> extended = new ArrayList<>(points);
			extended.add(tile);
			return new Path(extended, climbs);
		}
	}

	private static class Node
	{
		final int index;
		final WorldPoint point;
		final boolean anchor;
		final boolean climbFromPrev;

		Node(int index, WorldPoint point, boolean anchor, boolean climbFromPrev)
		{
			this.index = index;
			this.point = point;
			this.anchor = anchor;
			this.climbFromPrev = climbFromPrev;
		}
	}

	@Value
	public static class Transport
	{
		Set<Integer> bottom;
		Set<Integer> top;
		// Optional anchors pin the drawn route to the shortcut objects.
		@Nullable
		WorldPoint bottomAnchor;
		@Nullable
		WorldPoint topAnchor;
	}

	private static final int W = 0x1240108;
	private static final int E = 0x1240180;
	private static final int S = 0x1240102;
	private static final int N = 0x1240120;
	private static final int SW = 0x124010E;
	private static final int SE = 0x1240183;
	private static final int NW = 0x1240138;
	private static final int NE = 0x12401E0;
	private static final int TRANSPORT_COST = 3;
	private static final int MAX_STEPS = 120;

	private final Client client;

	private WorldPoint fieldStart;
	private List<Transport> fieldTransports = Collections.emptyList();
	private int originIdx = -1;
	private int[] originDist;
	private int baseX;
	private int baseY;
	private int fieldPlane;
	private int size;
	private int[] dist;
	private int[] prev;
	// Which transport (index + 1) reached each tile, 0 when walked to.
	private int[] via;
	private int[] queue;
	private int queueTail;
	private boolean recordParents;
	private int pathGoal = -1;
	private Path path = Path.EMPTY;
	private int[][] fieldFlags;
	private WorldView fieldView;
	private Point sweepStart;

	public Pathfinder(Client client)
	{
		this.client = client;
	}

	public static int tileKey(int sceneX, int sceneY)
	{
		return (sceneX << 8) | sceneY;
	}

	public void clear()
	{
		fieldStart = null;
		fieldView = null;
		fieldFlags = null;
		fieldTransports = Collections.emptyList();
		originIdx = -1;
		pathGoal = -1;
		path = Path.EMPTY;
	}

	/** Bounds are inclusive scene coordinates. Returns an empty path if adjacent or unreachable. */
	public Path pathTo(int minX, int minY, int maxX, int maxY, List<Transport> transports)
	{
		return pathTo(minX, minY, maxX, maxY, transports, null);
	}

	/**
	 * Prefer the footprint side facing toward, falling back to any reachable side.
	 */
	public Path pathTo(int minX, int minY, int maxX, int maxY, List<Transport> transports,
		@Nullable Point toward)
	{
		if (!ensureField(transports))
		{
			return Path.EMPTY;
		}
		int goal = toward == null ? -1 : nearestRingTile(minX, minY, maxX, maxY, toward);
		if (goal < 0)
		{
			goal = nearestRingTile(minX, minY, maxX, maxY);
		}
		if (goal < 0 || dist[goal] == 0)
		{
			return Path.EMPTY;
		}
		if (goal == pathGoal)
		{
			return path;
		}
		// Preserve shortcut anchors when reconstructing the parent chain.
		List<Node> nodes = new ArrayList<>();
		int at = goal;
		int guard = 0;
		while (dist[at] > 0 && guard++ < size * size)
		{
			int from = prev[at];
			boolean hop = via[at] > 0 && via[at] <= transports.size();
			Transport transport = hop ? transports.get(via[at] - 1) : null;
			boolean anchored = transport != null && transport.getBottomAnchor() != null && transport.getTopAnchor() != null;
			nodes.add(new Node(at, WorldPoint.fromScene(fieldView, at / size, at % size, fieldPlane), false, hop && !anchored));
			if (anchored)
			{
				boolean up = transport.getBottom().contains(tileKey(from / size, from % size));
				WorldPoint far = up ? transport.getTopAnchor() : transport.getBottomAnchor();
				WorldPoint near = up ? transport.getBottomAnchor() : transport.getTopAnchor();
				nodes.add(new Node(-1, far, true, true));
				nodes.add(new Node(-1, near, true, false));
			}
			at = from;
		}
		nodes.add(new Node(at, WorldPoint.fromScene(fieldView, at / size, at % size, fieldPlane), false, false));
		Collections.reverse(nodes);
		if (nodes.size() > MAX_STEPS)
		{
			nodes = nodes.subList(0, MAX_STEPS);
		}
		pathGoal = goal;
		path = toPath(nodes);
		return path;
	}

	// Smooth walkable segments without skipping anchors or climbs.
	// Every tile of the route, so the line follows the steps the player will take rather than
	// cutting across them.
	private static Path toPath(List<Node> nodes)
	{
		List<WorldPoint> points = new ArrayList<>();
		Set<Integer> climbs = new HashSet<>();
		for (int i = 1; i < nodes.size(); i++)
		{
			Node node = nodes.get(i);
			if (node.climbFromPrev)
			{
				climbs.add(points.size() - 1);
			}
			points.add(node.point);
		}
		return new Path(points, climbs);
	}

	/** Walking distance in tiles to the nearest tile touching the footprint, or -1 when unreachable. */
	public int distanceTo(int minX, int minY, int maxX, int maxY, List<Transport> transports)
	{
		if (!ensureField(transports))
		{
			return -1;
		}
		int goal = nearestRingTile(minX, minY, maxX, maxY);
		return goal < 0 ? -1 : dist[goal];
	}

	/**
	 * Distance from the origin's nearest reachable ring tile to the destination ring, or -1.
	 */
	public int distanceBetween(int aMinX, int aMinY, int aMaxX, int aMaxY, int bMinX, int bMinY, int bMaxX, int bMaxY,
		List<Transport> transports)
	{
		if (!ensureField(transports))
		{
			return -1;
		}
		int origin = nearestRingTile(aMinX, aMinY, aMaxX, aMaxY);
		if (origin < 0)
		{
			return -1;
		}
		if (origin != originIdx)
		{
			originIdx = origin;
			int[] keep = dist;
			Point keepStart = sweepStart;
			dist = originDist;
			sweepFrom(origin / size, origin % size, transports, false);
			originDist = dist;
			dist = keep;
			sweepStart = keepStart;
		}
		int best = -1;
		for (int x = bMinX - 1; x <= bMaxX + 1; x++)
		{
			for (int y = bMinY - 1; y <= bMaxY + 1; y++)
			{
				if (x < 0 || y < 0 || x >= size || y >= size)
				{
					continue;
				}
				int d = originDist[x * size + y];
				if (d >= 0 && (best < 0 || d < best))
				{
					best = d;
				}
			}
		}
		return best;
	}

	/**
	 * Exclude corners and tiles separated from the footprint by a wall.
	 */
	private int nearestRingTile(int minX, int minY, int maxX, int maxY)
	{
		return nearestRingTile(minX, minY, maxX, maxY, null);
	}

	private int nearestRingTile(int minX, int minY, int maxX, int maxY, @Nullable Point toward)
	{
		double centreX = (minX + maxX) / 2.0;
		double centreY = (minY + maxY) / 2.0;
		double centreGap = toward == null ? 0 : gap(centreX, centreY, toward);
		int best = -1;
		for (int x = minX - 1; x <= maxX + 1; x++)
		{
			for (int y = minY - 1; y <= maxY + 1; y++)
			{
				if (x < 0 || y < 0 || x >= size || y >= size)
				{
					continue;
				}
				if (toward != null && gap(x, y, toward) >= centreGap)
				{
					continue;
				}
				boolean outsideX = x < minX || x > maxX;
				boolean outsideY = y < minY || y > maxY;
				if (outsideX && outsideY)
				{
					continue;
				}
				if ((outsideX || outsideY) && wallBetween(x, y, minX, minY, maxX, maxY))
				{
					continue;
				}
				int idx = x * size + y;
				if (dist[idx] < 0)
				{
					continue;
				}
				// Break equal-distance ties by straight-line distance from the sweep origin.
				boolean closer = best < 0 || dist[idx] < dist[best]
					|| (dist[idx] == dist[best] && sweepStart != null
						&& gap(x, y, sweepStart) < gap(best / size, best % size, sweepStart));
				if (closer)
				{
					best = idx;
				}
			}
		}
		return best;
	}

	private static double gap(double x, double y, Point to)
	{
		double dx = x - to.getX();
		double dy = y - to.getY();
		return dx * dx + dy * dy;
	}

	private boolean wallBetween(int x, int y, int minX, int minY, int maxX, int maxY)
	{
		if (fieldFlags == null)
		{
			return false;
		}
		int ox = Math.min(Math.max(x, minX), maxX);
		int oy = Math.min(Math.max(y, minY), maxY);
		int own;
		int facing;
		if (x < minX)
		{
			own = 0x8;
			facing = 0x80;
		}
		else if (x > maxX)
		{
			own = 0x80;
			facing = 0x8;
		}
		else if (y < minY)
		{
			own = 0x2;
			facing = 0x20;
		}
		else
		{
			own = 0x20;
			facing = 0x2;
		}
		return (fieldFlags[x][y] & own) != 0 || (fieldFlags[ox][oy] & facing) != 0;
	}

	private boolean ensureField(List<Transport> transports)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			clear();
			return false;
		}
		WorldView wv = me.getWorldView();
		CollisionData[] maps = wv.getCollisionMaps();
		int plane = wv.getPlane();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			clear();
			return false;
		}
		int[][] flags = maps[plane].getFlags();
		LocalPoint lp = me.getLocalLocation();
		int sx = lp.getSceneX();
		int sy = lp.getSceneY();
		if (fieldView != wv || fieldPlane != plane || fieldFlags != flags
			|| baseX != wv.getBaseX() || baseY != wv.getBaseY() || !fieldTransports.equals(transports))
		{
			clear();
			fieldView = wv;
			fieldPlane = plane;
			fieldFlags = flags;
			baseX = wv.getBaseX();
			baseY = wv.getBaseY();
			fieldTransports = transports;
			size = flags.length;
		}
		if (sx < 0 || sy < 0 || sx >= size || sy >= size)
		{
			clear();
			return false;
		}
		WorldPoint start = me.getWorldLocation();
		if (!start.equals(fieldStart))
		{
			sweepFrom(sx, sy, transports, true);
			fieldStart = start;
		}
		return true;
	}

	private void sweepFrom(int sx, int sy, List<Transport> transports, boolean recordParents)
	{
		this.recordParents = recordParents;
		sweepStart = new Point(sx, sy);
		if (dist == null || dist.length != size * size)
		{
			dist = new int[size * size];
		}
		if (queue == null || queue.length != dist.length)
		{
			queue = new int[dist.length];
			prev = new int[dist.length];
			via = new int[dist.length];
		}
		Arrays.fill(dist, -1);
		if (recordParents)
		{
			Arrays.fill(via, 0);
			pathGoal = -1;
		}
		int startIdx = sx * size + sy;
		dist[startIdx] = 0;
		if (recordParents)
		{
			prev[startIdx] = startIdx;
		}
		int head = 0;
		queueTail = 0;
		queue[queueTail++] = startIdx;
		int[][] flags = fieldFlags;
		// Keep transport endpoints paired.
		List<List<Integer>> bottomTiles = new ArrayList<>();
		List<List<Integer>> topTiles = new ArrayList<>();
		for (Transport transport : transports)
		{
			bottomTiles.add(sceneIndices(transport.getBottom()));
			topTiles.add(sceneIndices(transport.getTop()));
		}
		while (head < queueTail)
		{
			int cur = queue[head++];
			int cx = cur / size;
			int cy = cur % size;
			int d = dist[cur] + 1;
			step(flags, cur, cx - 1, cy, W, d);
			step(flags, cur, cx + 1, cy, E, d);
			step(flags, cur, cx, cy - 1, S, d);
			step(flags, cur, cx, cy + 1, N, d);
			if (open(flags, cx - 1, cy, W) && open(flags, cx, cy - 1, S))
			{
				step(flags, cur, cx - 1, cy - 1, SW, d);
			}
			if (open(flags, cx + 1, cy, E) && open(flags, cx, cy - 1, S))
			{
				step(flags, cur, cx + 1, cy - 1, SE, d);
			}
			if (open(flags, cx - 1, cy, W) && open(flags, cx, cy + 1, N))
			{
				step(flags, cur, cx - 1, cy + 1, NW, d);
			}
			if (open(flags, cx + 1, cy, E) && open(flags, cx, cy + 1, N))
			{
				step(flags, cur, cx + 1, cy + 1, NE, d);
			}
			int here = tileKey(cx, cy);
			for (int g = 0; g < transports.size(); g++)
			{
				Transport transport = transports.get(g);
				if (transport.getBottom().contains(here))
				{
					hop(cur, topTiles.get(g), g + 1);
				}
				if (transport.getTop().contains(here))
				{
					hop(cur, bottomTiles.get(g), g + 1);
				}
			}
		}
	}

	// Transport distances are estimates: this FIFO search does not relax weighted edges.
	private void hop(int from, List<Integer> farEnd, int transportIndex)
	{
		for (int other : farEnd)
		{
			if (dist[other] < 0)
			{
				dist[other] = dist[from] + TRANSPORT_COST;
				if (recordParents)
				{
					prev[other] = from;
					via[other] = transportIndex;
				}
				queue[queueTail++] = other;
			}
		}
	}

	private List<Integer> sceneIndices(Set<Integer> tileKeys)
	{
		List<Integer> indices = new ArrayList<>();
		for (int key : tileKeys)
		{
			int tx = key >> 8;
			int ty = key & 0xff;
			if (tx >= 0 && ty >= 0 && tx < size && ty < size)
			{
				indices.add(tx * size + ty);
			}
		}
		return indices;
	}

	private void step(int[][] flags, int from, int nx, int ny, int mask, int d)
	{
		if (!open(flags, nx, ny, mask))
		{
			return;
		}
		int idx = nx * size + ny;
		if (dist[idx] >= 0)
		{
			return;
		}
		dist[idx] = d;
		if (recordParents)
		{
			prev[idx] = from;
		}
		queue[queueTail++] = idx;
	}

	private boolean open(int[][] flags, int x, int y, int mask)
	{
		return x >= 0 && y >= 0 && x < size && y < size && (flags[x][y] & mask) == 0;
	}
}
