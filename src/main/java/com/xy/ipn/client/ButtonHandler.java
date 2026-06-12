package com.xy.ipn.client;

import com.xy.ipn.config.IPNConfig;
import com.xy.ipn.config.IPNConfigGui;
import com.xy.ipn.client.action.ClientMoveAll;
import com.xy.ipn.client.action.ClientProfiles;
import com.xy.ipn.client.action.ClientSorter;
import com.xy.ipn.proxy.ClientProxy;
import com.xy.ipn.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiShulkerBox;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class ButtonHandler {

    private static final int BUTTON_ID_BASE = 8000;
    private static final int BTN_SPACING = 12;
    // Quark draws its chest search bar across the top of chest/shulker GUIs,
    // overlapping our top container sort row. Cached once at class load.
    private static final boolean QUARK_LOADED = Loader.isModLoaded("quark");
    private static final List<SortButton> sortButtons = new ArrayList<SortButton>();
    private static SortButton craftingCheckbox = null;

    private static int trackedGuiLeft = Integer.MIN_VALUE;
    private static int trackedGuiTop = Integer.MIN_VALUE;

    // Horizontal profile bar (player inventory only):
    // [prev] [name] [next] [+] [x] — prev/next hidden when no profiles exist,
    // x hidden when no profile is active. Layout + visibility recomputed every
    // frame in onDrawScreenPre so delete/save state changes show instantly.
    private static final int PROFILE_NAME_MIN_WIDTH = 40;
    private static final int PROFILE_NAME_MAX_WIDTH = 140;
    private static boolean hasProfileBar = false;
    private static int profileBarX;
    private static int profileBarY;
    private static int profileBarNameX;
    private static SortButton profilePrevButton = null;
    private static SortButton profileNextButton = null;
    private static SortButton profileSaveButton = null;
    private static SortButton profileDeleteButton = null;

    /** Pixel width of the current profile bar name, clamped. */
    private static int profileNameWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.fontRenderer.getStringWidth(profileBarName());
        return Math.max(PROFILE_NAME_MIN_WIDTH, Math.min(PROFILE_NAME_MAX_WIDTH, w));
    }

    /** The text shown in the profile bar. */
    private static String profileBarName() {
        String name = ProfileClientState.currentProfileName;
        if (name == null || name.isEmpty()) {
            return ProfileClientState.profileCount > 0
                    ? "§7" + I18n.format("ipn.profilebar.none", ProfileClientState.profileCount)
                    : "§7" + I18n.format("ipn.profilebar.empty");
        }
        return name;
    }

    /**
     * Recompute profile bar layout + visibility from live profile state.
     * Called every frame; keeps the bar correct immediately after
     * save/overwrite/delete without reopening the GUI.
     */
    private static void layoutProfileBar() {
        if (profilePrevButton == null || profileNextButton == null
                || profileSaveButton == null || profileDeleteButton == null) {
            return;
        }
        boolean hasProfiles = ProfileClientState.profileCount > 0;
        String active = ProfileClientState.currentProfileName;
        boolean hasActive = active != null && !active.isEmpty();
        int btnSize = IPNConfig.buttonSize;

        profilePrevButton.visible = hasProfiles;
        profileNextButton.visible = hasProfiles;
        profileDeleteButton.visible = hasActive;

        profilePrevButton.x = profileBarX;
        profileBarNameX = profileBarX + (hasProfiles ? btnSize + 4 : 0);
        int afterName = profileBarNameX + profileNameWidth() + 4;
        if (hasProfiles) {
            profileNextButton.x = afterName;
            afterName += btnSize + 4;
        }
        profileSaveButton.x = afterName;
        profileDeleteButton.x = afterName + btnSize + 2;
    }

    private static List<String> sortTooltip() {
        String order = I18n.format("ipn.sort.order.item_id");
        return Arrays.asList(
                I18n.format("ipn.sort.button.sort"),
                "",
                "§5" + I18n.format("ipn.sort.button.sort_order", "§o" + order) + "§r",
                "",
                "§6 - " + I18n.format("ipn.sort.button.scroll_hint1"),
                "§6" + I18n.format("ipn.sort.button.scroll_hint2")
        );
    }

    private static List<String> singleTooltip(String key) {
        return Collections.singletonList(I18n.format(key));
    }

    private static List<String> sortColumnTooltip() {
        String order = I18n.format("ipn.sort.order.item_id");
        return Arrays.asList(
                I18n.format("ipn.sort.button.sort_columns"),
                "",
                "§5" + I18n.format("ipn.sort.button.sort_order", "§o" + order) + "§r",
                "",
                "§6 - " + I18n.format("ipn.sort.button.scroll_hint1"),
                "§6" + I18n.format("ipn.sort.button.scroll_hint2")
        );
    }

    private static List<String> sortRowTooltip() {
        String order = I18n.format("ipn.sort.order.item_id");
        return Arrays.asList(
                I18n.format("ipn.sort.button.sort_rows"),
                "",
                "§5" + I18n.format("ipn.sort.button.sort_order", "§o" + order) + "§r",
                "",
                "§6 - " + I18n.format("ipn.sort.button.scroll_hint1"),
                "§6" + I18n.format("ipn.sort.button.scroll_hint2")
        );
    }

    private static List<String> craftingCheckboxTooltip() {
        String state = IPNConfig.enableContinuousCrafting
                ? I18n.format("ipn.sort.button.crafting_on")
                : I18n.format("ipn.sort.button.crafting_off");
        return Arrays.asList(
                I18n.format("ipn.sort.button.continuous_crafting"),
                "§7" + state
        );
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (!(screen instanceof GuiContainer)) return;

        // Clear stale state BEFORE any early return — otherwise the profile
        // bar from a previous screen keeps drawing over creative etc.
        sortButtons.clear();
        craftingCheckbox = null;
        hasProfileBar = false;
        profilePrevButton = null;
        profileNextButton = null;
        profileSaveButton = null;
        profileDeleteButton = null;

        if (screen instanceof GuiContainerCreative) return;
        if (!IPNConfig.showSortButtons) return;

        GuiContainer gui = (GuiContainer) screen;
        boolean isPlayerInvScreen = (screen instanceof GuiInventory);

        int guiLeft = gui.getGuiLeft();
        int guiTop = gui.getGuiTop();
        int xSize = ObfuscationReflectionHelper.getPrivateValue(GuiContainer.class, gui, "field_146999_f");
        int ySize = ObfuscationReflectionHelper.getPrivateValue(GuiContainer.class, gui, "field_147000_g");

        event.getButtonList().removeIf(new java.util.function.Predicate<GuiButton>() {
            @Override
            public boolean test(GuiButton button) {
                return button instanceof SortButton;
            }
        });

        trackedGuiLeft = guiLeft;
        trackedGuiTop = guiTop;

        int nextId = BUTTON_ID_BASE;
        int btnSize = IPNConfig.buttonSize;
        // User-configurable nudge so the sort/move buttons can dodge other mods'
        // overlays (e.g. Quark's search bar pinned to the top-right of the GUI).
        int offsetX = IPNConfig.sortButtonOffsetX;
        int offsetY = IPNConfig.sortButtonOffsetY;
        int rightEdge = guiLeft + xSize - 7 + offsetX;

        boolean hasContainer = !isPlayerInvScreen && containerHasNonPlayerSlots(gui);

        int firstContainerSlot = -1;
        int firstPlayerSlot = -1;
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (!isPlayerInvScreen && !ContainerUtils.isPlayerSlot(slot) && firstContainerSlot == -1) {
                firstContainerSlot = slot.slotNumber;
            }
            if (ContainerUtils.isPlayerMainInvSlot(slot) && firstPlayerSlot == -1) {
                firstPlayerSlot = slot.slotNumber;
            }
        }

        // === Outside-right column for settings + profiles ===
        int outsideX = guiLeft + xSize + 2;
        int outsideY = guiTop + 2;
        int outsideRow = 0;

        if (isPlayerInvScreen) {
            SortButton settingsBtn = new SortButton(nextId++,
                    outsideX, outsideY + outsideRow * BTN_SPACING,
                    140, 0, singleTooltip("ipn.sort.button.settings"), 5, -1);
            event.getButtonList().add(settingsBtn);
            sortButtons.add(settingsBtn);
            outsideRow++;

            if (IPNConfig.enableProfiles) {
                // Horizontal profile bar above the GUI, like the original
                // ProfilesUICollectionWidget: [prev] [active profile name] [next]
                profileBarX = guiLeft;
                profileBarY = guiTop - btnSize - 2;
                hasProfileBar = true;

                SortButton profilePrev = new SortButton(nextId++,
                        profileBarX, profileBarY,
                        60, 20, singleTooltip("ipn.sort.button.profile_prev"), 7, -1);
                event.getButtonList().add(profilePrev);
                sortButtons.add(profilePrev);
                profilePrevButton = profilePrev;

                SortButton profileNext = new SortButton(nextId++,
                        profileBarX + btnSize + profileNameWidth() + 8, profileBarY,
                        50, 20, singleTooltip("ipn.sort.button.profile_next"), 6, -1);
                event.getButtonList().add(profileNext);
                sortButtons.add(profileNext);
                profileNextButton = profileNext;

                SortButton profileSave = new SortButton(nextId++,
                        profileNext.x + btnSize + 4, profileBarY, "+",
                        Arrays.asList(
                                I18n.format("ipn.sort.button.profile_save"),
                                "§7" + I18n.format("ipn.sort.button.profile_save_shift")),
                        9, -1);
                event.getButtonList().add(profileSave);
                sortButtons.add(profileSave);
                profileSaveButton = profileSave;

                SortButton profileDelete = new SortButton(nextId++,
                        profileSave.x + btnSize + 2, profileBarY, "§cx",
                        Arrays.asList(
                                I18n.format("ipn.sort.button.profile_delete"),
                                "§7" + I18n.format("ipn.sort.button.profile_delete_shift")),
                        10, -1);
                event.getButtonList().add(profileDelete);
                sortButtons.add(profileDelete);
                profileDeleteButton = profileDelete;

                layoutProfileBar();
            }

            int checkU = IPNConfig.enableContinuousCrafting ? 80 : 70;
            craftingCheckbox = new SortButton(nextId++,
                    outsideX, outsideY + outsideRow * BTN_SPACING,
                    checkU, 0, craftingCheckboxTooltip(), 8, -1);
            event.getButtonList().add(craftingCheckbox);
            sortButtons.add(craftingCheckbox);
            outsideRow++;
        }

        if (hasContainer) {
            // === CONTAINER GUI (chest, etc.) ===
            // Quark's search bar lives at the top of chest/shulker GUIs and overlaps
            // this top sort row. Auto-lift it above the GUI when Quark is present so
            // users get a clean layout without touching the offset config.
            boolean quarkSearchOverlap = QUARK_LOADED && IPNConfig.autoAdjustForQuark
                    && (screen instanceof GuiChest || screen instanceof GuiShulkerBox);
            int containerY = (quarkSearchOverlap ? guiTop - btnSize - 4 : guiTop + 5) + offsetY;
            int n = 0;

            SortButton cSortRow = new SortButton(nextId++,
                    rightEdge - btnSize - n * BTN_SPACING, containerY,
                    30, 0, sortRowTooltip(), 2, firstContainerSlot);
            n++;
            SortButton cSortCol = new SortButton(nextId++,
                    rightEdge - btnSize - n * BTN_SPACING, containerY,
                    20, 0, sortColumnTooltip(), 1, firstContainerSlot);
            n++;
            SortButton cSort = new SortButton(nextId++,
                    rightEdge - btnSize - n * BTN_SPACING, containerY,
                    10, 0, sortTooltip(), 0, firstContainerSlot);
            n++;

            event.getButtonList().add(cSort);
            event.getButtonList().add(cSortCol);
            event.getButtonList().add(cSortRow);
            sortButtons.add(cSort);
            sortButtons.add(cSortCol);
            sortButtons.add(cSortRow);

            if (IPNConfig.showMoveAllButtons) {
                SortButton moveToPlayer = new SortButton(nextId++,
                        rightEdge - btnSize - n * BTN_SPACING, containerY,
                        60, 0, singleTooltip("ipn.sort.button.move_to_player"), 4, firstContainerSlot);
                event.getButtonList().add(moveToPlayer);
                sortButtons.add(moveToPlayer);
            }

            int playerY = guiTop + ySize - 95 + offsetY;
            if (IPNConfig.showMoveAllButtons) {
                SortButton moveToCont = new SortButton(nextId++,
                        rightEdge - btnSize, playerY,
                        50, 0, singleTooltip("ipn.sort.button.move_to_container"), 3, firstPlayerSlot);
                event.getButtonList().add(moveToCont);
                sortButtons.add(moveToCont);
            }

            if (screen instanceof GuiCrafting) {
                int checkU = IPNConfig.enableContinuousCrafting ? 80 : 70;
                craftingCheckbox = new SortButton(nextId++,
                        outsideX, outsideY,
                        checkU, 0, craftingCheckboxTooltip(), 8, -1);
                event.getButtonList().add(craftingCheckbox);
                sortButtons.add(craftingCheckbox);
            }
        } else {
            // === PLAYER INVENTORY or no-container GUI ===
            int playerY = guiTop + ySize - 95 + offsetY;
            int n = 0;

            SortButton pSortRow = new SortButton(nextId++,
                    rightEdge - btnSize - n * BTN_SPACING, playerY,
                    30, 0, sortRowTooltip(), 2, firstPlayerSlot);
            n++;
            SortButton pSortCol = new SortButton(nextId++,
                    rightEdge - btnSize - n * BTN_SPACING, playerY,
                    20, 0, sortColumnTooltip(), 1, firstPlayerSlot);
            n++;
            SortButton pSort = new SortButton(nextId++,
                    rightEdge - btnSize - n * BTN_SPACING, playerY,
                    10, 0, sortTooltip(), 0, firstPlayerSlot);

            event.getButtonList().add(pSort);
            event.getButtonList().add(pSortCol);
            event.getButtonList().add(pSortRow);
            sortButtons.add(pSort);
            sortButtons.add(pSortCol);
            sortButtons.add(pSortRow);

        }
    }

    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.getGui();

        int currentLeft = gui.getGuiLeft();
        int currentTop = gui.getGuiTop();

        if (trackedGuiLeft != Integer.MIN_VALUE
                && (currentLeft != trackedGuiLeft || currentTop != trackedGuiTop)) {
            int dx = currentLeft - trackedGuiLeft;
            int dy = currentTop - trackedGuiTop;
            for (SortButton btn : sortButtons) {
                btn.x += dx;
                btn.y += dy;
            }
            profileBarX += dx;
            profileBarY += dy;
            trackedGuiLeft = currentLeft;
            trackedGuiTop = currentTop;
        }

        if (hasProfileBar) {
            layoutProfileBar();
        }
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiButton button = event.getButton();
        if (!(button instanceof SortButton)) return;
        if (!button.enabled) return;

        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        SortButton sortButton = (SortButton) button;
        int action = sortButton.getAction();
        int targetSlot = sortButton.getTargetSlotId();

        GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
        GuiContainer container = currentScreen instanceof GuiContainer
                ? (GuiContainer) currentScreen : null;

        switch (action) {
            case 0:
                if (container != null) ClientSorter.sort(container, targetSlot, 0);
                break;
            case 1:
                if (container != null) ClientSorter.sort(container, targetSlot, 1);
                break;
            case 2:
                if (container != null) ClientSorter.sort(container, targetSlot, 2);
                break;
            case 3:
                if (container != null) ClientMoveAll.moveAll(container, targetSlot, true);
                break;
            case 4:
                if (container != null) ClientMoveAll.moveAll(container, targetSlot, false);
                break;
            case 5:
                Minecraft mc = Minecraft.getMinecraft();
                mc.displayGuiScreen(new IPNConfigGui(mc.currentScreen));
                break;
            case 6:
                ClientProfiles.applyNext();
                break;
            case 7:
                ClientProfiles.applyPrev();
                break;
            case 8:
                IPNConfig.setContinuousCrafting(!IPNConfig.enableContinuousCrafting);
                if (craftingCheckbox != null) {
                    craftingCheckbox.setSpriteU(IPNConfig.enableContinuousCrafting ? 80 : 70);
                }
                break;
            case 9:
                if (GuiScreen.isShiftKeyDown()) {
                    ClientProfiles.overwriteCurrent();
                } else {
                    ClientProfiles.saveNew();
                }
                break;
            case 10:
                if (GuiScreen.isShiftKeyDown()) {
                    ClientProfiles.deleteCurrent();
                } else {
                    Minecraft mcDel = Minecraft.getMinecraft();
                    String activeName = ProfileClientState.currentProfileName;
                    String hintKey = (activeName == null || activeName.isEmpty())
                            ? "ipn.msg.no_active_profile" : "ipn.msg.profile_delete_hint";
                    mcDel.player.sendStatusMessage(
                            new net.minecraft.util.text.TextComponentTranslation(hintKey, activeName), true);
                }
                break;
            default:
                break;
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (!(screen instanceof GuiContainer)) return;

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        // Defensive instanceof check: creative tab switches don't refire
        // InitGuiEvent, so stale hasProfileBar state could draw over the tabs.
        if (hasProfileBar && screen instanceof GuiInventory) {
            drawProfileBarName(screen);
        }

        for (SortButton button : sortButtons) {
            if (button.visible && button.isMouseOver()) {
                List<String> lines = button.getTooltipLines();
                if (lines != null && !lines.isEmpty()) {
                    GuiUtils.drawHoveringText(
                            lines,
                            mouseX, mouseY,
                            screen.width, screen.height,
                            -1,
                            Minecraft.getMinecraft().fontRenderer);
                }
                break;
            }
        }
    }

    /**
     * Draw the active profile name between the prev/next buttons of the
     * profile bar. State comes from ClientProfiles via ProfileClientState.
     */
    private static void drawProfileBarName(GuiScreen screen) {
        Minecraft mc = Minecraft.getMinecraft();
        int textX = profileBarNameX;
        int textY = profileBarY + 1;
        String trimmed = mc.fontRenderer.trimStringToWidth(profileBarName(), PROFILE_NAME_MAX_WIDTH);
        mc.fontRenderer.drawStringWithShadow(trimmed, textX, textY, 0xFFFFFF);
        // Font rendering dirties GL color state — reset before any texture draw
        net.minecraft.client.renderer.GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static boolean containerHasNonPlayerSlots(GuiContainer gui) {
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (!ContainerUtils.isPlayerSlot(slot)) {
                return true;
            }
        }
        return false;
    }
}
