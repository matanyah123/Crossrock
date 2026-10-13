package dev.matanyah.bedrockcompat.behavior;

import com.google.gson.*;
import java.nio.file.*;
import java.io.*;

/** Versioned, atomic world-owned storage; JSON payloads remain namespace scoped. */
public final class BehaviorStateStore {
    private final Path path;private JsonObject data=new JsonObject();private boolean dirty;
    public BehaviorStateStore(Path path)throws IOException {
        this.path=path;
        if(Files.exists(path)){var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();if(root.get("version").getAsInt()!=1)throw new IOException("Unsupported Crossrock state version");data=root.getAsJsonObject("data");}
    }
    public JsonObject snapshot(){return data.deepCopy();}
    public void put(String namespace,String key,JsonElement value){
        if(net.minecraft.resources.Identifier.tryParse(namespace)==null)throw new IllegalArgumentException("Invalid storage namespace");
        if(!data.has(namespace))data.add(namespace,new JsonObject());var values=data.getAsJsonObject(namespace);if(value==null)values.remove(key);else values.add(key,value.deepCopy());dirty=true;
    }
    public void save()throws IOException {
        if(!dirty)return;JsonObject root=new JsonObject();root.addProperty("version",1);root.add("data",data);byte[] bytes=root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>32*1024*1024)throw new IOException("Crossrock persistent state exceeds limit");
        Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),"state-",".tmp");
        try{Files.write(temp,bytes);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}dirty=false;}finally{Files.deleteIfExists(temp);}
    }
}
