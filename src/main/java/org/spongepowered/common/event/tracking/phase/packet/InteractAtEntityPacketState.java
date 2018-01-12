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
package org.spongepowered.common.event.tracking.phase.packet;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketUseEntity;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.tracking.context.ItemDropData;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

final class InteractAtEntityPacketState extends BasicPacketState {

    @Override
    public boolean ignoresItemPreMerging() {
        return true;
    }

    @Override
    public boolean isPacketIgnored(Packet<?> packetIn, EntityPlayerMP packetPlayer) {
        final CPacketUseEntity useEntityPacket = (CPacketUseEntity) packetIn;
        // There are cases where a player is interacting with an entity that doesn't exist on the server.
        @Nullable net.minecraft.entity.Entity entity = useEntityPacket.getEntityFromWorld(packetPlayer.world);
        return entity == null;
    }

    @Override
    public void populateContext(EntityPlayerMP playerMP, Packet<?> packet, BasicPacketContext context) {
        final CPacketUseEntity useEntityPacket = (CPacketUseEntity) packet;
        final ItemStack stack = ItemStackUtil.cloneDefensive(playerMP.getHeldItem(useEntityPacket.getHand()));
        if (stack != null) {
            context.itemUsed(stack);
        }
    }

    @Override
    public boolean doesCaptureEntityDrops() {
        return true;
    }

    @Override
    public void unwind(BasicPacketContext context) {
        final EntityPlayerMP player = context.getPacketPlayer();

        final CPacketUseEntity useEntityPacket = context.getPacket();
        final net.minecraft.entity.Entity entity = useEntityPacket.getEntityFromWorld(player.world);
        if (entity == null) {
            // Something happened?
            return;
        }
        final World spongeWorld = EntityUtil.getSpongeWorld(player);
        EntityUtil.toMixin(entity).setNotifier(player.getUniqueID());


        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            Sponge.getCauseStackManager().pushCause(player);
            Sponge.getCauseStackManager().addContext(EventContextKeys.SPAWN_TYPE, InternalSpawnTypes.PLACEMENT);
            context.getCapturedEntitySupplier().acceptAndClearIfNotEmpty(entities -> {

                SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(Sponge.getCauseStackManager().getCurrentCause(), entities);
                SpongeImpl.postEvent(event);
                if (!event.isCancelled()) {
                    processSpawnedEntities(player, event);
                }
            });
            context.getCapturedItemsSupplier().acceptAndClearIfNotEmpty(entities -> {
                final List<Entity> items = entities.stream().map(EntityUtil::fromNative).collect(Collectors.toList());
                SpawnEntityEvent event =
                    SpongeEventFactory.createSpawnEntityEvent(Sponge.getCauseStackManager().getCurrentCause(), items);
                SpongeImpl.postEvent(event);
                if (!event.isCancelled()) {
                    processSpawnedEntities(player, event);
                }
            });
            context.getCapturedEntityDropSupplier().acceptIfNotEmpty(map -> {
                final PrettyPrinter printer = new PrettyPrinter(80);
                printer.add("Processing Interact At Entity").centre().hr();
                printer.add("The item stacks captured are: ");

                for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                    printer.add("  - Entity with UUID: %s", entry.getKey());
                    for (ItemDropData stack : entry.getValue()) {
                        printer.add("    - %s", stack);
                    }
                }
                printer.trace(System.err);
            });
            context.getCapturedEntityItemDropSupplier().acceptIfNotEmpty(map -> {
                for (Map.Entry<UUID, Collection<EntityItem>> entry : map.asMap().entrySet()) {
                    final UUID entityUuid = entry.getKey();
                    final net.minecraft.entity.Entity entityFromUuid = player.getServerWorld().getEntityFromUuid(entityUuid);
                    if (entityFromUuid != null) {
                        final List<Entity> entities = entry.getValue()
                            .stream()
                            .map(EntityUtil::fromNative)
                            .collect(Collectors.toList());
                        if (!entities.isEmpty()) {
                            DropItemEvent.Custom event =
                                SpongeEventFactory.createDropItemEventCustom(Sponge.getCauseStackManager().getCurrentCause(),
                                    entities);
                            SpongeImpl.postEvent(event);
                            if (!event.isCancelled()) {
                                processSpawnedEntities(player, event);
                            }
                        }
                    }
                }
            });
            context.getCapturedItemStackSupplier().acceptAndClearIfNotEmpty(drops -> {
                final List<EntityItem> items =
                    drops.stream().map(drop -> drop.create(player.getServerWorld())).collect(Collectors.toList());
                final List<Entity> entities = items
                    .stream()
                    .map(EntityUtil::fromNative)
                    .collect(Collectors.toList());
                if (!entities.isEmpty()) {
                    DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(Sponge.getCauseStackManager().getCurrentCause(),
                        entities);
                    SpongeImpl.postEvent(event);
                    if (!event.isCancelled()) {
                        processSpawnedEntities(player, event);
                    }
                }

            });
        }
        context.getCapturedBlockSupplier()
            .acceptAndClearIfNotEmpty(snapshots -> TrackingUtil.processBlockCaptures(snapshots, this, context));
    }
}
