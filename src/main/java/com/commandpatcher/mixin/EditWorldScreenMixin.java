package com.commandpatcher.mixin;

import com.commandpatcher.WorldConverter;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.EditWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(EditWorldScreen.class)
public abstract class EditWorldScreenMixin extends Screen {

    @Shadow @Final
    private LevelStorage.Session storageSession;

    protected EditWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addConvertButton(CallbackInfo ci) {
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Convert Commands"),
            button -> {
                button.active = false;
                button.setMessage(Text.literal("Converting..."));

                Thread thread = new Thread(() -> {
                    Path worldDir = storageSession.getDirectory().path();
                    WorldConverter.ConversionResult result = WorldConverter.convert(worldDir);

                    this.client.execute(() -> {
                        // Show summary on button
                        if (result.commandBlocksPatched() > 0) {
                            button.setMessage(Text.literal(
                                "Done! " + result.commandBlocksPatched() + "/" + result.commandBlocksFound() + " patched"));
                        } else if (result.commandBlocksFound() > 0) {
                            button.setMessage(Text.literal(
                                "Done — " + result.commandBlocksFound() + " found, none needed patching"));
                        } else {
                            button.setMessage(Text.literal(
                                "Done — no command blocks found"));
                        }

                        // Log warnings and errors to chat-style on-screen display
                        if (!result.warnings().isEmpty()) {
                            button.setMessage(Text.literal(
                                result.commandBlocksPatched() + " patched, "
                                + result.warnings().size() + " warnings")
                                .formatted(Formatting.YELLOW));
                        }
                        if (!result.errors().isEmpty()) {
                            button.setMessage(Text.literal(
                                result.commandBlocksPatched() + " patched, "
                                + result.errors().size() + " errors")
                                .formatted(Formatting.RED));
                        }

                        // Re-enable for another run if needed
                        button.active = true;
                    });
                }, "CommandPatcher-Converter");
                thread.setDaemon(true);
                thread.start();
            }
        ).dimensions(this.width / 2 - 100, this.height - 52, 200, 20).build());
    }
}
