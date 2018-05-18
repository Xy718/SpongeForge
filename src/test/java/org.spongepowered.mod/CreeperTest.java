/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.mod;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.flowpowered.math.vector.Vector3d;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.monster.Creeper;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.cause.EventContextKey;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.entity.Hotbar;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.mctester.internal.TestUtils;
import org.spongepowered.mctester.internal.BaseTest;
import org.spongepowered.mctester.junit.MinecraftRunner;
import org.spongepowered.mctester.junit.WorldOptions;

import java.util.List;
import java.util.stream.Collectors;

@RunWith(MinecraftRunner.class)
@WorldOptions(deleteWorldOnSuccess = true)
public class CreeperTest extends BaseTest {

    public CreeperTest(TestUtils testUtils) {
        super(testUtils);
    }

    @Test
    public void explodeCreeper() throws Throwable {
        this.testUtils.batchActions(() -> {
            Player player = this.testUtils.getThePlayer();
            player.offer(Keys.GAME_MODE, GameModes.CREATIVE);

            ((Hotbar) player.getInventory().query(QueryOperationTypes.INVENTORY_TYPE.of(Hotbar.class))).setSelectedSlotIndex(0);
            player.setItemInHand(HandTypes.MAIN_HAND, ItemStack.builder().itemType(ItemTypes.SPAWN_EGG).quantity(1).add(Keys.SPAWNABLE_ENTITY_TYPE, EntityTypes.CREEPER).build());
        });

        testUtils.waitForInventoryPropagation();

        // Look at the ground two blocks in the z direction
        Vector3d targetPos = this.testUtils.batchActions(() -> { return CreeperTest.this.testUtils.getThePlayer().getLocation().getPosition().add(0, -1, 2); });
        client.lookAt(targetPos);

        final Creeper[] creeper = new Creeper[1];

        this.testUtils.listen(SpawnEntityEvent.class, new EventListener<SpawnEntityEvent>() {

            @Override
            public void handle(SpawnEntityEvent event) throws Exception {
                if (!event.getEntities().stream().anyMatch(e -> e.getType().equals(EntityTypes.CREEPER))) {
                    return;
                }

                assertThat(event.getEntities(), hasSize(1));
                creeper[0] = (Creeper) event.getEntities().get(0);

                assertTrue("Cause doesn't contain player: " + event.getCause(), event.getCause().contains(CreeperTest.this.testUtils.getThePlayer()));
                assertTrue("Cause doesn't contain correct item: " + event.getCause(),
                        event.getCause().getContext().get(EventContextKeys.USED_ITEM).map(i -> i.getType().equals(ItemTypes.SPAWN_EGG)).orElse(false)
                );

                Sponge.getEventManager().unregisterListeners(this);

            }
        });

        client.rightClick();

        assertNotNull("Creeper did not spawn!", creeper[0]);


        EventListener<MoveEntityEvent> moveListener = this.testUtils.listen(MoveEntityEvent.class, new EventListener<MoveEntityEvent>() {

            @Override
            public void handle(MoveEntityEvent event) throws Exception {
                if (event.getTargetEntity().getUniqueId() == creeper[0].getUniqueId()) {
                    event.setCancelled(true);
                }
            }
        });

        testUtils.listen(MoveEntityEvent.class, moveListener);

        this.testUtils.batchActions(() -> {

            ((Hotbar) this.testUtils.getThePlayer().getInventory().query(QueryOperationTypes.INVENTORY_TYPE.of(Hotbar.class))).setSelectedSlotIndex(1);
            this.testUtils.getThePlayer().setItemInHand(HandTypes.MAIN_HAND, ItemStack.of(ItemTypes.FLINT_AND_STEEL, 1));
        });

        testUtils.waitForInventoryPropagation();
        this.client.lookAt(creeper[0]);

        this.testUtils.listenTimeout(ChangeBlockEvent.Break.class, new EventListener<ChangeBlockEvent.Break>() {

            @Override
            public void handle(ChangeBlockEvent.Break event) throws Exception {
                assertThat(event.getCause().getContext().get(EventContextKeys.OWNER).get().getUniqueId(), equalTo(CreeperTest.this.testUtils.getThePlayer().getUniqueId()));

                for (Transaction<BlockSnapshot> transaction: event.getTransactions()) {
                    assertThat(transaction.getFinal().getState().getType(), equalTo(BlockTypes.AIR));
                }
            }
        }, 2 * 20);

        this.client.rightClick();
        //throw new AssertionError("Dummy assertion failure!");
    }
}
