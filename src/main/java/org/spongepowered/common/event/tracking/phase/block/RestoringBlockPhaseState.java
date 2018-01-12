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

import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.event.tracking.context.GeneralizedContext;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;

import java.util.ArrayList;

final class RestoringBlockPhaseState extends BlockPhaseState {

    RestoringBlockPhaseState() {
    }

    @Override
    public void unwind(GeneralizedContext phaseContext) {

    }

    @Override
    public boolean allowEntitySpawns() {
        return false;
    }

    @Override
    public boolean spawnEntityOrCapture(GeneralizedContext context, Entity entity, int chunkX, int chunkZ) {
        final User user = context.getNotifier().orElseGet(() -> context.getOwner().orElse(null));
        if (user != null) {
            entity.setCreator(user.getUniqueId());
        }
        final ArrayList<Entity> entities = new ArrayList<>(1);
        entities.add(entity);
        final SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(Sponge.getCauseStackManager().getCurrentCause(),
            entities);
        SpongeImpl.postEvent(event);
        if (!event.isCancelled() && event.getEntities().size() > 0) {
            for (org.spongepowered.api.entity.Entity item: event.getEntities()) {
                ((IMixinWorldServer) item.getWorld()).forceSpawnEntity(item);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean requiresBlockCapturing() {
        return false;
    }
}
