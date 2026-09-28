package dev.skylasliver.creeperphantom.client;

import com.google.gson.*;
import dev.skylasliver.creeperphantom.ConfigDocumentation;
import dev.skylasliver.creeperphantom.ConfigEditorDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Client-only, paged tree editor; all nested equipment lists share one detached draft. */
public final class PhantomConfigScreen extends Screen {
    private static final String PREFIX = "config.skylasliver_creeperphantom.";
    private final Screen parent;
    private final MinecraftServer owner;
    private ConfigEditorDocument document;
    private final Deque<Node> ancestors = new ArrayDeque<>();
    private final Map<String, String> invalidInputs = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private Node node;
    private String failure;
    private boolean saving;
    private int page;
    private int pageSize;
    private int left;
    private int contentWidth;
    private record Node(JsonElement value, String key, String path) {}
    private record Label(Component text, int y) {}

    public PhantomConfigScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        owner = Minecraft.getInstance().getSingleplayerServer();
        if (remoteOrShared()) failure = text("remote").getString();
        else try {
            document = ConfigEditorDocument.open();
            node = new Node(document.values, "title", "");
        } catch (Exception e) { failure = e.getMessage(); }
    }

    private boolean remoteOrShared() {
        var client = Minecraft.getInstance();
        return (client.getConnection() != null && owner == null) || (owner != null && owner.isPublished());
    }

    private static Component text(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    private static Component label(String key) { return Component.translatableWithFallback(PREFIX + "field." + key, key); }

    @Override protected void init() {
        clearWidgets();
        labels.clear();
        contentWidth = Math.min(580, width - 24);
        left = (width - contentWidth) / 2;
        pageSize = Math.max(1, (height - 146) / 28);
        if (document == null) {
            addRenderableWidget(Button.builder(text("back"), b -> onClose()).bounds(width / 2 - 75, height - 30, 150, 20).build());
            return;
        }
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>();
        if (node.value.isJsonObject()) entries.addAll(node.value.getAsJsonObject().entrySet());
        else for (int i = 0; i < node.value.getAsJsonArray().size(); i++)
            entries.add(Map.entry(Integer.toString(i), node.value.getAsJsonArray().get(i)));
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages - 1);
        int fieldX = left + contentWidth / 2;
        int fieldWidth = contentWidth - contentWidth / 2;
        for (int i = page * pageSize; i < Math.min(entries.size(), (page + 1) * pageSize); i++) {
            var entry = entries.get(i);
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            String path = node.path + "/" + key;
            int y = 62 + (i % pageSize) * 28;
            boolean array = node.value.isJsonArray();
            Component caption = array ? text("entry", Integer.parseInt(key) + 1) : label(key);
            labels.add(new Label(caption, y + 6));
            String help = ConfigDocumentation.comment(array ? node.key : key);
            Tooltip tooltip = Tooltip.create(caption.copy().append("\n" + key + "\n" + help));
            Consumer<JsonElement> setter = v -> {
                if (array) node.value.getAsJsonArray().set(Integer.parseInt(key), v);
                else node.value.getAsJsonObject().add(key, v);
            };
            if (value.isJsonObject() || value.isJsonArray()) {
                Component buttonText = value.isJsonArray() ? text("list", value.getAsJsonArray().size()) : text("edit");
                addRenderableWidget(Button.builder(buttonText, b -> {
                    ancestors.push(node);
                    node = new Node(value, array ? node.key : key, path);
                    page = 0;
                    rebuildWidgets();
                }).bounds(fieldX, y, fieldWidth - (array ? 48 : 0), 20).tooltip(tooltip).build());
                if (array) addRenderableWidget(Button.builder(text("remove"), b -> {
                    ConfigEditorDocument.removeListEntry(node.value.getAsJsonArray(), node.path,
                        Integer.parseInt(key), invalidInputs);
                    rebuildWidgets();
                }).bounds(left + contentWidth - 44, y, 44, 20).build());
            } else if (value.getAsJsonPrimitive().isBoolean()) {
                addRenderableWidget(Button.builder(text(value.getAsBoolean() ? "on" : "off"), b -> {
                    setter.accept(new JsonPrimitive(!value.getAsBoolean()));
                    rebuildWidgets();
                }).bounds(fieldX, y, fieldWidth, 20).tooltip(tooltip).build());
            } else {
                var input = new EditBox(font, fieldX, y, fieldWidth, 20, caption);
                input.setMaxLength(512);
                input.setValue(invalidInputs.getOrDefault(path, value.getAsString()));
                input.setTextColor(invalidInputs.containsKey(path) ? 0xFF7777 : 0xFFFFFF);
                input.setTooltip(tooltip);
                input.setResponder(raw -> {
                    try {
                        if (value.getAsJsonPrimitive().isNumber()) setter.accept(new JsonPrimitive(new BigDecimal(raw)));
                        else setter.accept(new JsonPrimitive(raw));
                        invalidInputs.remove(path);
                        input.setTextColor(0xFFFFFF);
                    } catch (NumberFormatException e) {
                        invalidInputs.put(path, raw);
                        input.setTextColor(0xFF7777);
                    }
                });
                addRenderableWidget(input);
            }
        }
        int navigationY = height - 78;
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
            .bounds(left, navigationY, 28, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
            .bounds(left + 96, navigationY, 28, 20).build());
        next.active = page + 1 < pages;
        labels.add(new Label(Component.literal((page + 1) + " / " + pages), navigationY + 6));
        if (node.value.isJsonArray()) {
            addRenderableWidget(Button.builder(text("add"), b -> {
                node.value.getAsJsonArray().add(ConfigEditorDocument.newListEntry(node.key));
                page = (node.value.getAsJsonArray().size() - 1) / pageSize;
                rebuildWidgets();
            }).bounds(left + contentWidth - 110, navigationY, 110, 20).build());
        } else if (ancestors.isEmpty()) {
            addRenderableWidget(Button.builder(text("defaults"), b -> minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    document.values.entrySet().clear();
                    ConfigEditorDocument.defaults().entrySet().forEach(e -> document.values.add(e.getKey(), e.getValue()));
                    invalidInputs.clear();
                }
                minecraft.setScreen(this);
            }, text("defaults"), text("defaults_confirm")))).bounds(left + contentWidth - 110, navigationY, 110, 20).build());
        }
        int buttonWidth = Math.min(150, (contentWidth - 8) / 2);
        addRenderableWidget(Button.builder(text(ancestors.isEmpty() ? "cancel" : "back"), b -> onClose())
            .bounds(width / 2 - buttonWidth - 4, height - 28, buttonWidth, 20).build());
        var save = addRenderableWidget(Button.builder(text(saving ? "saving" : "save"), b -> save())
            .bounds(width / 2 + 4, height - 28, buttonWidth, 20).build());
        save.active = !saving;
        if (saving) children().forEach(child -> { if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.active = false; });
    }

    private void save() {
        if (!invalidInputs.isEmpty()) { failure = text("invalid_number").getString(); return; }
        if (remoteOrShared() || owner != minecraft.getSingleplayerServer()) { failure = text("world_changed").getString(); return; }
        saving = true;
        failure = null;
        rebuildWidgets();
        Runnable operation = () -> {
            try {
                document.save(owner == null ? null : owner.registryAccess(), owner != null);
                minecraft.execute(() -> { saving = false; minecraft.setScreen(parent); });
            } catch (Exception e) {
                minecraft.execute(() -> { saving = false; failure = e.getMessage(); rebuildWidgets(); });
            }
        };
        if (owner == null) operation.run(); else owner.execute(operation);
    }

    @Override public void onClose() {
        if (saving) return;
        if (!ancestors.isEmpty()) {
            node = ancestors.pop(); page = 0; rebuildWidgets();
        } else if (document != null && (document.changed() || !invalidInputs.isEmpty())) {
            minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? parent : this),
                text("discard"), text("discard_confirm")));
        } else minecraft.setScreen(parent);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (document != null) {
            Component scope = text(owner == null ? "global" : "world");
            graphics.drawCenteredString(font, scope, width / 2, 25, 0xBBBBBB);
            graphics.drawCenteredString(font, node.path.isEmpty() ? text("root_hint") : label(node.key), width / 2, 42, 0xFFD580);
            for (Label label : labels) {
                int x = label.y == height - 72 ? left + 36 : left;
                graphics.drawString(font, font.plainSubstrByWidth(label.text.getString(), contentWidth / 2 - 8), x, label.y, 0xFFFFFF);
            }
            if (mouseY >= 22 && mouseY <= 36) graphics.renderTooltip(font, Component.literal(document.path().toString()), mouseX, mouseY);
        }
        if (failure != null) {
            Component message = Component.literal(failure);
            int y = document == null ? 60 : height - 50;
            graphics.drawCenteredString(font, font.plainSubstrByWidth(message.getString(), width - 24), width / 2, y, 0xFF7777);
            if (mouseY >= y - 2 && mouseY <= y + 13) graphics.renderTooltip(font, font.split(message, Math.min(420, width - 24)), mouseX, mouseY);
        }
    }
}
