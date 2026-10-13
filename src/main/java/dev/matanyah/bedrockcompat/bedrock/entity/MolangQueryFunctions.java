package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Context-independent query functions. Unsupported state queries always reach the context provider. */
public final class MolangQueryFunctions {
    private MolangQueryFunctions() {}
    public static Object get(String name, List<Object> args, Molang.Queries fallback) {
        return switch (name) {
            case "count" -> (double)args.stream().mapToInt(v -> v instanceof List<?> list ? list.size() : 1).sum();
            case "any", "all" -> {
                if(args.size()<3)throw new IllegalArgumentException(name+" requires at least three arguments");
                Object expected=args.getFirst();
                yield (name.equals("any")?args.stream().skip(1).anyMatch(v->equal(expected,v)):
                    args.stream().skip(1).allMatch(v->equal(expected,v)))?1d:0d;
            }
            case "in_range" -> {
                if(args.size()!=3)throw new IllegalArgumentException("in_range requires three arguments");
                double value=Molang.number(args.getFirst());
                yield value>=Molang.number(args.get(1))&&value<=Molang.number(args.get(2))?1d:0d;
            }
            // These are explicitly documented client/legacy constants, not substitutes for missing state.
            case "unhappy_counter", "last_hit_by_player" -> 0d;
            default -> fallback.get(name,args);
        };
    }
    private static boolean equal(Object a,Object b) {
        return a instanceof Number && b instanceof Number ? Molang.number(a)==Molang.number(b):Objects.equals(a,b);
    }
    public static int index(Object value,int max,String query) {
        double number=Molang.number(value);
        if(!Double.isFinite(number)||number!=Math.rint(number)||number<0||number>max)
            throw new IllegalArgumentException(query+" requires an integer index in [0,"+max+"]");
        return (int)number;
    }
}
