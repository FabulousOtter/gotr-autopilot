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
package com.fabulousotter.gotr.state;

import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class StateTrackerTest
{
	private final Client client = mock(Client.class);
	private final Player player = mock(Player.class);
	private final ItemManager items = mock(ItemManager.class);
	private final StateTracker tracker = new StateTracker(client, mock(ClientThread.class), items, mock(ConfigManager.class));

	@Before
	public void setUp()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(player.getAnimation()).thenReturn(-1);
		when(client.getIntStack()).thenReturn(new int[]{2});
	}

	@Test
	public void outsideGotrSkipsInventoryAndQuestPolling()
	{
		tracker.tick();
		assertSame(Snapshot.OUTSIDE, tracker.snapshot());
		verify(client, never()).getItemContainer(anyInt());
		verify(client, never()).getIntStack();
		verify(client, never()).getVarbitValue(anyInt());
		verifyNoInteractions(items);
	}

	@Test
	public void unrelatedSceneryDoesNotLoadDefinitions()
	{
		GameObject object = mock(GameObject.class);
		when(object.getId()).thenReturn(ObjectID.REDWOODTREE_L);
		when(object.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		GameObjectSpawned event = new GameObjectSpawned();
		event.setGameObject(object);
		tracker.onGameObjectSpawned(event);
		verify(client, never()).getObjectDefinition(anyInt());
		assertTrue(tracker.getShortcuts().isEmpty());
	}

	@Test
	public void groundShortcutDespawnRemovesTransportSource()
	{
		GroundObject object = mock(GroundObject.class);
		when(object.getId()).thenReturn(ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM);
		GroundObjectSpawned spawn = new GroundObjectSpawned();
		spawn.setGroundObject(object);
		tracker.onGroundObjectSpawned(spawn);
		assertEquals(1, tracker.getShortcuts().size());
		int version = tracker.getSceneVersion();
		GroundObjectDespawned despawn = new GroundObjectDespawned();
		despawn.setGroundObject(object);
		tracker.onGroundObjectDespawned(despawn);
		assertTrue(tracker.getShortcuts().isEmpty());
		assertTrue(tracker.getSceneVersion() > version);
		verify(client, never()).getObjectDefinition(anyInt());
	}

	@Test
	public void inventoryIsSampledOncePerTickEvenWithoutContainerEvents()
	{
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3610, 9500, 0));
		ItemContainer inventory = mock(ItemContainer.class);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.GOTR_GUARDIAN_FRAGMENT, 10)});
		ItemContainerChanged event = new ItemContainerChanged(InventoryID.INV, inventory);
		tracker.onItemContainerChanged(event);
		tracker.onItemContainerChanged(event);
		tracker.tick();
		assertEquals(10, tracker.snapshot().getFragments());
		verify(inventory, times(1)).getItems();
		when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.GOTR_GUARDIAN_ESSENCE, 6)});
		tracker.tick();
		Snapshot snapshot = tracker.snapshot();
		assertEquals(6, snapshot.getEssence());
		assertEquals(0, snapshot.getFragments());
		assertEquals(27, snapshot.getFreeSlots());
		verify(inventory, times(2)).getItems();
	}

	@Test
	public void unchangedInventoryDoesNotRepeatItemDefinitionLookups()
	{
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3610, 9500, 0));
		ItemContainer inventory = mock(ItemContainer.class);
		ItemComposition pickaxe = mock(ItemComposition.class);
		when(pickaxe.getName()).thenReturn("Rune pickaxe");
		when(items.getItemComposition(ItemID.RUNE_PICKAXE)).thenReturn(pickaxe);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(inventory.getItems()).thenAnswer(invocation -> new Item[]{new Item(ItemID.RUNE_PICKAXE, 1)});
		tracker.tick();
		tracker.tick();
		assertTrue(tracker.snapshot().isPickaxe());
		verify(items, times(1)).getItemComposition(ItemID.RUNE_PICKAXE);
		tracker.reset();
		tracker.tick();
		verify(items, times(2)).getItemComposition(ItemID.RUNE_PICKAXE);
	}

	@Test
	public void reenableSeedsTheSceneAgain()
	{
		WorldView view = mock(WorldView.class, RETURNS_DEEP_STUBS);
		when(player.getWorldView()).thenReturn(view);
		when(view.getScene().getTiles()).thenReturn(new Tile[1][1][1]);
		when(view.npcs().iterator()).thenAnswer(invocation -> Collections.emptyIterator());
		tracker.primeFromClient();
		tracker.tick();
		verify(view.getScene()).getTiles();
		tracker.reset();
		clearInvocations(view.getScene());
		tracker.primeFromClient();
		tracker.tick();
		verify(view.getScene()).getTiles();
	}
}
