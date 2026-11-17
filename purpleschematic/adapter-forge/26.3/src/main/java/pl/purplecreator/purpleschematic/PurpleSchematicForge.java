package pl.purplecreator.purpleschematic;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(PurpleSchematicForge.MOD_ID)
public class PurpleSchematicForge {
    public static final String MOD_ID = "purpleschematic";
    private static final int PORT = 8766;
    private static final Gson GSON = new Gson();
    private static final BlockingQueue<JsonObject> TASKS = new LinkedBlockingQueue<>();
    private static final AtomicBoolean LOCK = new AtomicBoolean(false);
    private static volatile HttpServer http;
    private static volatile String status = "Waiting for desktop app";
    private static volatile BuildTask active;

    public PurpleSchematicForge() { net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent public void started(ServerStartedEvent event) { startBridge(); }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) { stopBridge(); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent.Post event) { tickBuild(event.getServer()); }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("purpleschem").then(Commands.literal("status")
            .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal("PurpleSchematic: " + status), false); return 1; })));
    }

    private static void startBridge() {
        try {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            http.createContext("/status", e -> {
                if (!"GET".equals(e.getRequestMethod())) { respond(e,405,"{}"); return; }
                JsonObject out=new JsonObject(); out.addProperty("connected",true); out.addProperty("status",status); respond(e,200,GSON.toJson(out));
            });
            http.createContext("/mods", e -> {
                if (!"GET".equals(e.getRequestMethod())) { respond(e,405,"{}"); return; }
                JsonArray mods=new JsonArray();
                ModList.get().getMods().stream().sorted((a,b)->a.getModId().compareToIgnoreCase(b.getModId())).forEach(m->{
                    JsonObject x=new JsonObject(); x.addProperty("id",m.getModId()); x.addProperty("name",m.getDisplayName()); x.addProperty("version",m.getVersion().toString()); mods.add(x);
                });
                JsonObject out=new JsonObject(); out.add("mods",mods); out.addProperty("count",mods.size()); respond(e,200,GSON.toJson(out));
            });
            http.createContext("/analyze", e -> {
                if (!"POST".equals(e.getRequestMethod())) { respond(e,405,"{}"); return; }
                try(InputStream in=e.getRequestBody()) {
                    JsonObject req=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonArray ids=req.getAsJsonArray("ids");
                    if(ids==null||ids.size()>5000){respond(e,400,"{\"error\":\"Invalid block list\"}");return;}
                    JsonArray found=new JsonArray();
                    for(JsonElement value:ids){
                        String raw=value.getAsString(); JsonObject x=new JsonObject(); x.addProperty("id",raw);
                        ResourceLocation id=parseId(raw);
                        if(id==null){x.addProperty("known",false);x.addProperty("reason","Invalid identifier");}
                        else {
                            Block block=BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                            x.addProperty("namespace",id.getNamespace()); x.addProperty("modLoaded","minecraft".equals(id.getNamespace())||ModList.get().isLoaded(id.getNamespace()));
                            x.addProperty("known",block!=null); x.addProperty("blockExists",block!=null);
                            if(block!=null){
                                x.addProperty("name",block.getName().getString());
                                Item item=block.asItem(); boolean has=item!=Items.AIR; x.addProperty("hasItem",has);
                                x.addProperty("itemId",has?BuiltInRegistries.ITEM.getKey(item).toString():"");
                                x.addProperty("itemExists",has);
                            } else x.addProperty("reason","Missing mod or block registration");
                        }
                        found.add(x);
                    }
                    JsonObject out=new JsonObject();out.add("blocks",found);out.addProperty("checked",found.size());respond(e,200,GSON.toJson(out));
                } catch(Exception ex){JsonObject out=new JsonObject();out.addProperty("error",String.valueOf(ex.getMessage()));respond(e,400,GSON.toJson(out));}
            });
            http.createContext("/build", e -> {
                if(!"POST".equals(e.getRequestMethod())){respond(e,405,"{}");return;}
                try(InputStream in=e.getRequestBody()){
                    JsonObject task=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonArray blocks=task.getAsJsonArray("blocks");
                    if(blocks==null||blocks.isEmpty()||blocks.size()>100000){respond(e,400,"{\"error\":\"Schematic must contain 1..100000 blocks\"}");return;}
                    if(!LOCK.compareAndSet(false,true)){respond(e,409,"{\"error\":\"Build already running\"}");return;}
                    TASKS.offer(task);status="Build queued: "+blocks.size();
                    JsonObject out=new JsonObject();out.addProperty("accepted",true);respond(e,202,GSON.toJson(out));
                }catch(Exception ex){LOCK.set(false);JsonObject out=new JsonObject();out.addProperty("error",String.valueOf(ex.getMessage()));respond(e,400,GSON.toJson(out));}
            });
            http.setExecutor(Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PurpleSchematic HTTP");t.setDaemon(true);return t;}));
            http.start(); status="Connected - waiting for build";
        } catch(IOException ex){status="Bridge failed: "+ex.getMessage();}
    }

    private static ResourceLocation parseId(String raw){try{return ResourceLocation.parse(raw);}catch(Exception ex){return null;}}
    private static void respond(HttpExchange e,int code,String body)throws IOException{
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.sendResponseHeaders(code,bytes.length);
        try(OutputStream out=e.getResponseBody()){out.write(bytes);}
    }
    private static void stopBridge(){if(http!=null){http.stop(0);http=null;}TASKS.clear();active=null;LOCK.set(false);status="Minecraft stopped";}

    private static void tickBuild(MinecraftServer server){
        if(active==null){
            JsonObject next=TASKS.poll();if(next==null)return;
            ServerPlayer operator=server.getPlayerList().getPlayers().stream().filter(p->p.hasPermissions(2)).findFirst().orElse(null);
            if(operator==null){status="Build rejected: operator required";LOCK.set(false);return;}
            boolean creative="creative".equalsIgnoreCase(next.has("mode")?next.get("mode").getAsString():"creative");
            if(creative!=operator.isCreative()){status="Build rejected: game mode mismatch";LOCK.set(false);return;}
            JsonObject origin=next.getAsJsonObject("origin");active=new BuildTask(next.getAsJsonArray("blocks"),origin,next.has("rotation")?next.get("rotation").getAsInt():0,operator);
        }
        BuildTask task=active;if(task==null)return;
        if(!task.player.isAlive()||!task.player.hasPermissions(2)||task.player.getServer()!=server){status="Build stopped: operator unavailable";active=null;LOCK.set(false);return;}
        if(task.index>=task.blocks.size()){status="Completed "+task.index+" blocks";task.player.sendSystemMessage(Component.literal("[PurpleSchematic] Build complete: "+task.index));active=null;LOCK.set(false);return;}
        try{
            JsonObject data=task.blocks.get(task.index).getAsJsonObject();ResourceLocation id=parseId(data.get("id").getAsString());
            Block block=id==null?null:BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if(block==null)throw new IllegalArgumentException("Unknown block: "+data.get("id").getAsString());
            if(block==Blocks.AIR){task.index++;return;}
            int x=data.get("x").getAsInt(),y=data.get("y").getAsInt(),z=data.get("z").getAsInt(),rx=x,rz=z;
            switch(Math.floorMod(task.rotation,360)){case 90->{rx=-z;rz=x;}case 180->{rx=-x;rz=-z;}case 270->{rx=z;rz=-x;}}
            BlockPos pos=new BlockPos(task.x+rx,task.y+y,task.z+rz);ServerLevel level=task.player.serverLevel();
            if(!level.isInWorldBounds(pos)){throw new IllegalArgumentException("Position outside world bounds");}
            if(!task.player.isCreative()&&!consume(task.player,block))throw new IllegalStateException("Missing item: "+BuiltInRegistries.BLOCK.getKey(block));
            level.setBlock(pos,block.defaultBlockState(),3);task.index++;if(task.index%100==0)status="Building "+task.index+"/"+task.blocks.size();
        }catch(Exception ex){status="Build stopped: "+ex.getMessage();task.player.sendSystemMessage(Component.literal("[PurpleSchematic] "+status));active=null;LOCK.set(false);}
    }
    private static boolean consume(ServerPlayer player,Block block){
        Item item=block.asItem();if(item==Items.AIR)return false;
        for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack stack=player.getInventory().getItem(i);if(!stack.isEmpty()&&stack.is(item)){stack.shrink(1);return true;}}
        return false;
    }
    private static final class BuildTask{
        final JsonArray blocks;final int x,y,z,rotation;final ServerPlayer player;int index;
        BuildTask(JsonArray b,JsonObject o,int r,ServerPlayer p){blocks=b;x=o.get("x").getAsInt();y=o.get("y").getAsInt();z=o.get("z").getAsInt();rotation=r;player=p;}
    }
}
