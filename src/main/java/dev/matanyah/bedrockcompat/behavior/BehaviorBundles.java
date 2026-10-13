package dev.matanyah.bedrockcompat.behavior;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Verify actual inflated bytes, entry names and counts before any downloaded profile is admitted. */
public final class BehaviorBundles {
    public static void validate(byte[] data)throws IOException {
        long total=0;int count=0;Set<String> names=new HashSet<>();byte[] buffer=new byte[8192];
        try(var zip=new ZipInputStream(new ByteArrayInputStream(data))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                if(++count>100000)throw new IOException("Too many add-on entries");PackCatalog.safePath(entry.getName());
                if(!names.add(entry.getName()))throw new IOException("Duplicate add-on entry");
                String[] path=entry.getName().split("/",2);if(path.length<2)throw new IOException("Missing pack root");UUID.fromString(path[0]);
                long size=0;int read;while((read=zip.read(buffer))!=-1){size+=read;total+=read;if(size>32*1024*1024||total>PackCatalog.MAX_BYTES)throw new IOException("Inflated add-on profile exceeds budget");}
            }
        }
        if(count==0)throw new IOException("Empty add-on archive");
    }
}
