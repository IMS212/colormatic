/*
 * Colormatic
 * Copyright (C) 2019  Thalia Nero
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.kvverti.colormatic.mixin.render;

import io.github.kvverti.colormatic.Colormatic;
import io.github.kvverti.colormatic.resource.LightmapResource;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    @Shadow @Final private NativeImageBackedTexture texture;
    @Shadow @Final private NativeImage image;
    @Shadow private boolean isDirty;
    @Shadow @Final private GameRenderer worldRenderer;
    @Shadow @Final private MinecraftClient client;

    /* Relevant bytecode:
     *  17: invokeinterface #156,  2          // InterfaceMethod net/minecraft/util/profiler/Profiler.push:(Ljava/lang/String;)V
     *  22: aload_0
     *  23: getfield      #41                 // Field client:Lnet/minecraft/client/MinecraftClient;
     *  26: getfield      #160                // Field net/minecraft/client/MinecraftClient.world:Lnet/minecraft/client/world/ClientWorld;
     *  29: astore_2
     *  <injection point>
     *  30: aload_2
     *  31: ifnonnull     35     <--- if (world_1 != null)
     *  34: return
     *  35: aload_2
     */
    @Inject(
        method = "update",
        at = @At(
            value = "JUMP",
            ordinal = 1,
            shift = At.Shift.BEFORE
        ),
        locals = LocalCapture.CAPTURE_FAILEXCEPTION,
        cancellable = true
    )
    private void onUpdate(float partialTicks, CallbackInfo info, World world) {
        // TODO: take dimension into account
        if(world != null && Colormatic.OVERWORLD_LIGHTMAP.hasCustomColormap()) {
            LightmapResource map = Colormatic.OVERWORLD_LIGHTMAP;
            // TODO: handle night vision flicker
            boolean nightVision = this.client.player.hasStatusEffect(StatusEffects.NIGHT_VISION);
            float ambience;
            if(world.getTicksSinceLightning() > 0) {
                ambience = -1.0f;
            } else {
                ambience = world.getAmbientLight(partialTicks);
            }
            for(int skyLight = 0; skyLight < 16; skyLight++) {
                for(int blockLight = 0; blockLight < 16; blockLight++) {
                    int skyColor = map.getSkyLight(skyLight, ambience, nightVision);
                    int blockColor = map.getBlockLight(blockLight, world.getRandom(), nightVision);
                    // color will merge the brightest channels
                    int color = 0xff000000;
                    color |= Math.max(skyColor & 0xff0000, blockColor & 0xff0000);
                    color |= Math.max(skyColor & 0x00ff00, blockColor & 0x00ff00);
                    color |= Math.max(skyColor & 0x0000ff, blockColor & 0x0000ff);
                    this.image.setPixelRGBA(blockLight, skyLight, color);
                }
            }
            // do the cleanup because we cancel the default
            this.texture.upload();
            this.isDirty = false;
            this.client.getProfiler().pop();
            info.cancel();
        }
    }
}