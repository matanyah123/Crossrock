package dev.matanyah.bedrockcompat.behavior;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Shared atomic import for RP/BP/add-on screens; never overwrites an existing installation. */
public final class BedrockPackImporter {
    public static Path copy(Path source,Path directory)throws IOException {
        Files.createDirectories(directory);Path target=directory.resolve(source.getFileName().toString());
        if(Files.exists(target))throw new IOException("A file or folder with this name already exists");
        Path staging=Files.createTempDirectory(directory.toAbsolutePath().getParent(),".crossrock-import-");
        Path ready=staging.resolve(source.getFileName().toString());
        try {
            if(Files.isSymbolicLink(source))throw new IOException("Symbolic-link pack imports are not supported");
            if(Files.isDirectory(source)) {
                long total=0;int count=0;
                try(var files=Files.walk(source)) {
                    for(var file:files.toList()) {
                        if(++count>100000)throw new IOException("Too many imported files");if(Files.isSymbolicLink(file))throw new IOException("Pack contains a symbolic link");
                        Path destination=ready.resolve(source.relativize(file));
                        if(Files.isDirectory(file))Files.createDirectories(destination);else if(Files.isRegularFile(file)) {
                            total+=Files.size(file);if(total>PackCatalog.MAX_BYTES)throw new IOException("Imported pack exceeds content budget");Files.copy(file,destination);
                        }
                    }
                }
            }else {String name=source.getFileName().toString().toLowerCase(Locale.ROOT);if(!name.endsWith(".mcpack")&&!name.endsWith(".mcaddon"))throw new IOException("Expected a .mcpack, .mcaddon or pack folder");if(Files.size(source)>PackCatalog.MAX_BYTES)throw new IOException("Imported archive exceeds content budget");Files.copy(source,ready);}
            try{Files.move(ready,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(ready,target);}return target;
        }finally{try(var files=Files.walk(staging)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
    }
}
