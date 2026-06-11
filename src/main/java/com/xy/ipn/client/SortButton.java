package com.xy.ipn.client;

import com.xy.ipn.config.IPNConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.util.List;

public class SortButton extends GuiButton {

    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(
            "inventoryprofilesnext", "textures/gui/gui_buttons.png");

    private int spriteU;
    private final int spriteV;
    /** When non-null the button draws this text instead of an atlas sprite. */
    private final String label;
    private final List<String> tooltipLines;
    private final int action;
    private final int targetSlotId;

    public SortButton(int buttonId, int x, int y, int spriteU, int spriteV,
                      List<String> tooltipLines, int action, int targetSlotId) {
        super(buttonId, x, y, IPNConfig.buttonSize, IPNConfig.buttonSize, "");
        this.spriteU = spriteU;
        this.spriteV = spriteV;
        this.label = null;
        this.tooltipLines = tooltipLines;
        this.action = action;
        this.targetSlotId = targetSlotId;
    }

    /** Text-label variant for glyphs the atlas has no sprite for (e.g. "+"). */
    public SortButton(int buttonId, int x, int y, String label,
                      List<String> tooltipLines, int action, int targetSlotId) {
        super(buttonId, x, y, IPNConfig.buttonSize, IPNConfig.buttonSize, "");
        this.spriteU = -1;
        this.spriteV = -1;
        this.label = label;
        this.tooltipLines = tooltipLines;
        this.action = action;
        this.targetSlotId = targetSlotId;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        if (label != null) {
            int textWidth = mc.fontRenderer.getStringWidth(label);
            int color = this.hovered ? 0xFFFFA0 : 0xFFFFFF;
            mc.fontRenderer.drawStringWithShadow(label,
                    this.x + (this.width - textWidth) / 2.0f + 0.5f,
                    this.y + (this.height - 8) / 2.0f + 1, color);
            // Font rendering dirties GL color state — reset for later draws
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.enableTexture2D();

        mc.getTextureManager().bindTexture(BUTTON_TEXTURE);

        int vOffset = this.hovered ? 10 : 0;
        drawTexturedModalRect(this.x, this.y, spriteU, spriteV + vOffset, 10, 10);
    }

    public List<String> getTooltipLines() {
        return tooltipLines;
    }

    public int getAction() {
        return action;
    }

    public int getTargetSlotId() {
        return targetSlotId;
    }

    public void setSpriteU(int u) {
        this.spriteU = u;
    }
}
