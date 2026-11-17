package pl.purplecreator.purpleschematic;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.server.command.CommandManager.literal;

public class PurpleSchematicMod implements ModInitializer {
    private static final int PORT = 8766;
    private static final Gson GSON = new Gson();
    private static final BlockingQueue<JsonObject> TASKS = new LinkedBlockingQueue<>();
    private static final AtomicReference<String> STATUS = new AtomicReference<>("Waiting for desktop app");
    private static volatile HttpServer httpServer;
    private static volatile BuildTask activeTask;
    private static final java.util.concurrent.atomic.AtomicBoolean BUILD_LOCK = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> startBridge());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopBridge());
        ServerTickEvents.END_SERVER_TICK.register(PurpleSchematicMod::tickBuild);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("purpleschem").then(literal("status").executes(context -> {
                context.getSource().sendFeedback(() -> Text.literal("PurpleSchematic: " + STATUS.get()), false);
                return 1;
            }))));
    }

    private static void startBridge() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            httpServer.createContext("/status", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{\"error\":\"GET required\"}"); return; }
                JsonObject result = new JsonObject();
                result.addProperty("connected", true);
                result.addProperty("status", STATUS.get());
                respond(exchange, 200, GSON.toJson(result));
            });
            httpServer.createContext("/mods", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{\"error\":\"GET required\"}"); return; }
                JsonObject result = new JsonObject();
                JsonArray mods = new JsonArray();
                FabricLoader.getInstance().getAllMods().stream()
                    .sorted((a, b) -> a.getMetadata().getId().compareToIgnoreCase(b.getMetadata().getId()))
                    .forEach(mod -> {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("id", mod.getMetadata().getId());
                        entry.addProperty("name", mod.getMetadata().getName());
                        entry.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                        mods.add(entry);
                    });
                result.add("mods", mods);
                result.addProperty("count", mods.size());
                respond(exchange, 200, GSON.toJson(result));
            });
            httpServer.createContext("/analyze", exchange -> {
                if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{\"error\":\"POST required\"}"); return; }
                try (InputStream input = exchange.getRequestBody()) {
                    JsonObject request = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonArray ids = request.getAsJsonArray("ids");
                    if (ids == null || ids.size() > 5000) { respond(exchange, 400, "{\"error\":\"Lista bloków jest nieprawidłowa lub przekracza 5000 wpisów.\"}"); return; }
                    JsonObject result = new JsonObject();
                    JsonArray detected = new JsonArray();
                    for (JsonElement element : ids) {
                        String rawId = element.getAsString();
                        Identifier id = Identifier.tryParse(rawId);
                        JsonObject entry = new JsonObject();
                        entry.addProperty("id", rawId);
                        if (id == null) {
                            entry.addProperty("known", false);
                            entry.addProperty("reason", "Nieprawidłowy identyfikator");
                        } else {
                            String namespace = id.getNamespace();
                            boolean modLoaded = "minecraft".equals(namespace) || FabricLoader.getInstance().isModLoaded(namespace);
                            boolean blockExists = Registries.BLOCK.containsId(id);
                            entry.addProperty("namespace", namespace);
                            entry.addProperty("modLoaded", modLoaded);
                            entry.addProperty("blockExists", blockExists);
                            entry.addProperty("known", blockExists);
                            if (blockExists) {
                                Block block = Registries.BLOCK.get(id);
                                entry.addProperty("name", block.getName().getString());
                                Item item = block.asItem();
                                boolean hasItem = item != Items.AIR;
                                entry.addProperty("hasItem", hasItem);
                                if (hasItem) {
                                    Identifier itemId = Registries.ITEM.getId(item);
                                    entry.addProperty("itemId", itemId.toString());
                                    entry.addProperty("itemExists", Registries.ITEM.containsId(itemId));
                                } else {
                                    entry.addProperty("itemId", "");
                                    entry.addProperty("itemExists", false);
                                }
                            } else {
                                entry.addProperty("hasItem", false);
                                entry.addProperty("itemExists", false);
                                entry.addProperty("reason", modLoaded ? "Blok nie jest zarejestrowany" : "Brak moda o tym identyfikatorze");
                            }
                        }
                        detected.add(entry);
                    }
                    result.add("blocks", detected);
                    result.addProperty("checked", detected.size());
                    respond(exchange, 200, GSON.toJson(result));
                } catch (Exception error) {
                    JsonObject result = new JsonObject();
                    result.addProperty("error", error.getMessage() == null ? "Nie można przeanalizować schematu" : error.getMessage());
                    respond(exchange, 400, GSON.toJson(result));
                }
            });
            httpServer.createContext("/build", exchange -> {
                if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, "{\"error\":\"POST required\"}"); return; }
                try (InputStream input = exchange.getRequestBody()) {
                    JsonObject task = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonArray blocks = task.getAsJsonArray("blocks");
                    if (blocks == null || blocks.isEmpty() || blocks.size() > 100000) {
                        respond(exchange, 400, "{\"error\":\"Schemat musi mieć od 1 do 100000 bloków.\"}"); return;
                    }
                    if (!BUILD_LOCK.compareAndSet(false, true)) {
                        respond(exchange, 409, "{\"error\":\"Inne zadanie budowania już trwa.\"}"); return;
                    }
                    TASKS.offer(task);
                    STATUS.set("Build queued: " + blocks.size() + " blocks");
                    JsonObject result = new JsonObject();
                    result.addProperty("accepted", true);
                    result.addProperty("message", "Adapter przyjął " + blocks.size() + " bloków.");
                    respond(exchange, 202, GSON.toJson(result));
                } catch (Exception error) {
                    JsonObject result = new JsonObject();
                    result.addProperty("error", error.getMessage() == null ? "Invalid request" : error.getMessage());
                    respond(exchange, 400, GSON.toJson(result));
                }
            });
            httpServer.setExecutor(Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "PurpleSchematic HTTP"); t.setDaemon(true); return t; }));
            httpServer.start();
            STATUS.set("Connected - waiting for build");
        } catch (IOException error) {
            STATUS.set("Bridge failed: " + error.getMessage());
        }
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static void stopBridge() {
        if (httpServer != null) { httpServer.stop(0); httpServer = null; }
        TASKS.clear(); activeTask = null; BUILD_LOCK.set(false); STATUS.set("Minecraft stopped");
    }

    private static void tickBuild(MinecraftServer server) {
        if (activeTask == null) {
            JsonObject next = TASKS.poll();
            if (next == null) return;
            ServerPlayerEntity operator = server.getPlayerManager().getPlayerList().stream()
                .filter(player -> player.hasPermissionLevel(2)).findFirst().orElse(null);
            if (operator == null) { STATUS.set("Build rejected: operator player required"); BUILD_LOCK.set(false); return; }
            boolean requestedCreative = "creative".equalsIgnoreCase(next.has("mode") ? next.get("mode").getAsString() : "creative");
            if (requestedCreative != operator.isCreative()) {
                STATUS.set("Build rejected: selected mode does not match player's actual game mode");
                BUILD_LOCK.set(false); return;
            }
            JsonObject origin = next.getAsJsonObject("origin");
            activeTask = new BuildTask(next.getAsJsonArray("blocks"), origin,
                next.has("rotation") ? next.get("rotation").getAsInt() : 0, operator);
            STATUS.set("Building 0/" + activeTask.blocks.size());
        }
        BuildTask task = activeTask;
        if (task == null) return;
        if (!task.player.isAlive() || !task.player.hasPermissionLevel(2) || task.player.getServer() != server) {
            STATUS.set("Build stopped: operator unavailable"); activeTask = null; BUILD_LOCK.set(false); return;
        }
        if (task.index >= task.blocks.size()) {
            STATUS.set("Completed " + task.index + " blocks");
            task.player.sendMessage(Text.literal("[PurpleSchematic] Budowanie zakończone: " + task.index + " bloków."), false);
            activeTask = null; BUILD_LOCK.set(false); return;
        }
        try {
            JsonObject blockData = task.blocks.get(task.index).getAsJsonObject();
            Identifier identifier = Identifier.tryParse(blockData.get("id").getAsString());
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) throw new IllegalArgumentException("Nieznany blok: " + blockData.get("id").getAsString());
            Block block = Registries.BLOCK.get(identifier);
            if (block == null || block == net.minecraft.block.Blocks.AIR) { task.index++; return; }
            int x = blockData.get("x").getAsInt(), y = blockData.get("y").getAsInt(), z = blockData.get("z").getAsInt();
            int rx = x, rz = z;
            switch (Math.floorMod(task.rotation, 360)) {
                case 90 -> { rx = -z; rz = x; }
                case 180 -> { rx = -x; rz = -z; }
                case 270 -> { rx = z; rz = -x; }
            }
            BlockPos pos = new BlockPos(task.originX + rx, task.originY + y, task.originZ + rz);
            ServerWorld world = task.player.getServerWorld();
            if (!world.isInBuildLimit(pos)) {
                STATUS.set("Build stopped: position outside world height");
                task.player.sendMessage(Text.literal("[PurpleSchematic] Pozycja poza dozwoloną wysokością świata. Budowa zatrzymana."), false);
                activeTask = null; BUILD_LOCK.set(false); return;
            }
            if (!task.player.isCreative() && !consumeItem(task.player, block)) {
                Identifier requiredItemId = Registries.ITEM.getId(block.asItem());
                String requiredItem = block.asItem() == Items.AIR ? "brak przedmiotu do postawienia dla " + identifier : requiredItemId.toString();
                STATUS.set("Stopped: missing item " + requiredItem);
                task.player.sendMessage(Text.literal("[PurpleSchematic] Brakuje przedmiotu: " + requiredItem + ". Budowa zatrzymana."), false);
                activeTask = null; BUILD_LOCK.set(false); return;
            }
            world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
            task.index++;
            if (task.index % 100 == 0) STATUS.set("Building " + task.index + "/" + task.blocks.size());
        } catch (Exception error) {
            STATUS.set("Build stopped: " + error.getMessage());
            task.player.sendMessage(Text.literal("[PurpleSchematic] Błąd: " + error.getMessage()), false);
            activeTask = null;
            BUILD_LOCK.set(false);
        }
    }

    private static boolean consumeItem(ServerPlayerEntity player, Block block) {
        Item required = block.asItem();
        if (required == Items.AIR) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(block.asItem())) { stack.decrement(1); return true; }
        }
        return false;
    }

    private static final class BuildTask {
        final JsonArray blocks;
        final int originX, originY, originZ, rotation;
        final ServerPlayerEntity player;
        int index = 0;
        BuildTask(JsonArray blocks, JsonObject origin, int rotation, ServerPlayerEntity player) {
            this.blocks = blocks;
            this.originX = origin.get("x").getAsInt();
            this.originY = origin.get("y").getAsInt();
            this.originZ = origin.get("z").getAsInt();
            this.rotation = rotation;
            this.player = player;
        }
    }
}
