package dev.matanyah.bedrockcompat.behavior;

import net.minecraft.server.packs.repository.PackRepository;
import java.nio.file.Path;
import java.util.*;
public final class BehaviorRepositories {
    private static final Map<PackRepository,Path> paths=Collections.synchronizedMap(new WeakHashMap<>());
    public static void mark(PackRepository repository,Path path){paths.put(repository,path);}
    public static Path path(PackRepository repository){return paths.get(repository);}
    public static boolean isData(PackRepository repository){return paths.containsKey(repository);}
}
