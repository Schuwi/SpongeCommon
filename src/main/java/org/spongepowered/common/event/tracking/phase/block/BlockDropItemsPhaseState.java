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
package org.spongepowered.common.event.tracking.phase.block;

import net.minecraft.entity.item.EntityItem;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.tracking.context.GeneralizedContext;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class BlockDropItemsPhaseState extends BlockPhaseState {

    BlockDropItemsPhaseState() {
    }

    @Override
    public GeneralizedContext createPhaseContext() {
        return super.createPhaseContext()
                .addBlockCaptures()
                .addEntityCaptures();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void unwind(GeneralizedContext phaseContext) {
        final BlockSnapshot blockSnapshot = phaseContext.getSource(BlockSnapshot.class)
                .orElseThrow(TrackingUtil.throwWithContext("Could not find a block dropping items!", phaseContext));
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            Sponge.getCauseStackManager().pushCause(blockSnapshot);
            Sponge.getCauseStackManager().addContext(EventContextKeys.SPAWN_TYPE, InternalSpawnTypes.DROPPED_ITEM);
            if (phaseContext.getNotifier().isPresent()) {
                Sponge.getCauseStackManager().addContext(EventContextKeys.NOTIFIER, phaseContext.getNotifier().get());
            }
            if (phaseContext.getOwner().isPresent()) {
                Sponge.getCauseStackManager().addContext(EventContextKeys.OWNER, phaseContext.getOwner().get());
            }
            phaseContext.getCapturedItemsSupplier()
                    .acceptAndClearIfNotEmpty(items -> {
                        final ArrayList<Entity> entities = new ArrayList<>();
                        for (EntityItem item : items) {
                            entities.add(EntityUtil.fromNative(item));
                        }
                        final DropItemEvent.Destruct event =
                                SpongeEventFactory.createDropItemEventDestruct(Sponge.getCauseStackManager().getCurrentCause(), entities);
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                EntityUtil.getMixinWorld(entity).forceSpawnEntity(entity);
                            }
                        }
                    });
            phaseContext.getCapturedEntitySupplier()
                    .acceptAndClearIfNotEmpty(entities -> {
                        final SpawnEntityEvent event =
                                SpongeEventFactory.createSpawnEntityEvent(Sponge.getCauseStackManager().getCurrentCause(), entities);
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity entity : event.getEntities()) {
                                EntityUtil.getMixinWorld(entity).forceSpawnEntity(entity);
                            }
                        }
                        entities.clear();
                    });
            final Location<World> worldLocation = blockSnapshot.getLocation().get();
            final IMixinWorldServer mixinWorld = ((IMixinWorldServer) worldLocation.getExtent());

            Sponge.getCauseStackManager().addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.BLOCK_SPAWNING);
            phaseContext.getCapturedBlockSupplier()
                    .acceptAndClearIfNotEmpty(blocks -> TrackingUtil.processBlockCaptures(blocks, this, phaseContext));
            phaseContext.getCapturedItemStackSupplier()
                    .acceptAndClearIfNotEmpty(drops -> {
                        final List<EntityItem> items = drops.stream()
                                .map(drop -> drop.create(mixinWorld.asMinecraftWorld()))
                                .collect(Collectors.toList());
                        final List<Entity> entities = (List<Entity>) (List<?>) items;
                        if (!entities.isEmpty()) {
                            DropItemEvent.Custom event =
                                    SpongeEventFactory.createDropItemEventCustom(Sponge.getCauseStackManager().getCurrentCause(), entities);
                            SpongeImpl.postEvent(event);
                            if (!event.isCancelled()) {
                                for (Entity droppedItem : event.getEntities()) {
                                    mixinWorld.forceSpawnEntity(droppedItem);
                                }
                            }
                        }
                        drops.clear();

                    });
        }
    }
}
