package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Immutable native model data. All coordinates and names remain in Bedrock model space. */
public record BedrockGeometry(int textureWidth, int textureHeight, List<Bone> bones) {
    public record Vec(double x, double y, double z) {
        public static final Vec ZERO = new Vec(0, 0, 0);
    }
    public record Uv(double u, double v, double width, double height, String material, int rotation) {
        public Uv(double u, double v, double width, double height, String material) {
            this(u, v, width, height, material, 0);
        }
        /** Clockwise rotation of the UV rectangle; signed widths/heights remain meaningful. */
        public double[] at(double s, double t) {
            double a = s, b = t;
            switch (rotation) {
                case 90 -> { a = t; b = 1 - s; }
                case 180 -> { a = 1 - s; b = 1 - t; }
                case 270 -> { a = 1 - t; b = s; }
            }
            return new double[]{u + a * width, v + b * height};
        }
    }
    public record Cube(Vec origin, Vec size, Vec pivot, Vec rotation, double inflate,
                       boolean mirror, Map<String, Uv> faces) {}
    public record Locator(Vec offset, Vec rotation, boolean ignoreInheritedScale) {}
    public record MeshVertex(Vec position, Vec normal, double u, double v) {}
    public record Polygon(List<MeshVertex> vertices) {}
    public record Bone(String name, String parent, Vec pivot, Vec rotation,
                       List<Cube> cubes, Map<String, Locator> locators, List<Polygon> polygons, boolean neverRender) {
        public Bone(String name, String parent, Vec pivot, Vec rotation,
                    List<Cube> cubes, Map<String, Locator> locators, List<Polygon> polygons) {
            this(name,parent,pivot,rotation,cubes,locators,polygons,false);
        }
        public Bone(String name, String parent, Vec pivot, Vec rotation,
                    List<Cube> cubes, Map<String, Locator> locators) {
            this(name, parent, pivot, rotation, cubes, locators, List.of());
        }
    }

    public static BedrockGeometry parse(JsonObject source) {
        JsonObject description = source.has("description") ? source.getAsJsonObject("description") : source;
        int width = dimension(description, "texture_width", "texturewidth");
        int height = dimension(description, "texture_height", "textureheight");
        Map<String, Bone> bones = new LinkedHashMap<>();
        int cubeCount = 0;
        int[] meshBudget = {262144, 65536}; // Aggregate table entries and polygons across all bones.
        JsonArray entries = source.has("bones") ? source.getAsJsonArray("bones") : new JsonArray();
        if (entries.size() > 4096) throw invalid("Too many bones");
        for (JsonElement entry : entries) {
            JsonObject bone = entry.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
            if (name.isEmpty() || bones.containsKey(name)) throw invalid("Empty or duplicate bone: " + name);
            List<Cube> cubes = new ArrayList<>();
            if (bone.has("cubes")) for (JsonElement element : bone.getAsJsonArray("cubes")) {
                if (++cubeCount > 65536) throw invalid("Too many cubes");
                JsonObject cube = element.getAsJsonObject();
                Vec origin = vector(cube, "origin", Vec.ZERO), size = vector(cube, "size", Vec.ZERO);
                Vec center = new Vec(origin.x + size.x / 2, origin.y + size.y / 2, origin.z + size.z / 2);
                double inflate = number(cube, "inflate", number(bone, "inflate", 0));
                boolean mirror = bool(cube, "mirror", bool(bone, "mirror", false));
                cubes.add(new Cube(origin, size, vector(cube, "pivot", center), vector(cube, "rotation", Vec.ZERO),
                    inflate, mirror, faces(cube.get("uv"), size)));
            }
            Map<String, Locator> locators = new LinkedHashMap<>();
            if (bone.has("locators")) for (var locator : bone.getAsJsonObject("locators").entrySet()) {
                JsonElement value = locator.getValue();
                if (value.isJsonArray()) locators.put(locator.getKey(), new Locator(vector(value), Vec.ZERO, false));
                else {
                    JsonObject object = value.getAsJsonObject();
                    locators.put(locator.getKey(), new Locator(vector(object, "offset", Vec.ZERO),
                        vector(object, "rotation", Vec.ZERO), bool(object, "ignore_inherited_scale", false)));
                }
            }
            bones.put(name, new Bone(name, parent, vector(bone, "pivot", Vec.ZERO),
                vector(bone, "rotation", Vec.ZERO), List.copyOf(cubes), Map.copyOf(locators),
                bone.has("poly_mesh") ? polygons(bone.getAsJsonObject("poly_mesh"), width, height, meshBudget) : List.of(),
                bool(bone,"neverRender",false)));
        }
        // Validate independently of file order; parents commonly occur after their children.
        Set<String> complete = new HashSet<>();
        for (Bone bone : bones.values()) {
            Set<String> path = new HashSet<>();
            Bone cursor = bone;
            while (cursor != null && !complete.contains(cursor.name)) {
                if (!path.add(cursor.name)) throw invalid("Cyclic bone hierarchy at " + cursor.name);
                if (cursor.parent == null) break;
                Bone parent = bones.get(cursor.parent);
                if (parent == null) throw invalid("Missing parent " + cursor.parent + " of " + cursor.name);
                cursor = parent;
            }
            complete.addAll(path);
        }
        return new BedrockGeometry(width, height, List.copyOf(bones.values()));
    }

    /** Both indexed faces and sequential lists use the same immutable, normalized corners. */
    private static List<Polygon> polygons(JsonObject mesh, int width, int height, int[] budget) {
        JsonArray positions = mesh.has("positions") ? mesh.getAsJsonArray("positions") : new JsonArray();
        JsonArray normals = mesh.has("normals") ? mesh.getAsJsonArray("normals") : new JsonArray();
        JsonArray uvs = mesh.has("uvs") ? mesh.getAsJsonArray("uvs") : new JsonArray();
        long entries = (long) positions.size() + normals.size() + uvs.size();
        if (entries > budget[0]) throw invalid("Too many polygon mesh table entries");
        budget[0] -= (int) entries;
        List<Vec> points = new ArrayList<>(), directions = new ArrayList<>();
        List<double[]> coordinates = new ArrayList<>();
        positions.forEach(p -> points.add(vector(p)));
        normals.forEach(n -> directions.add(vector(n)));
        boolean normalized = bool(mesh, "normalized_uvs", false);
        for (JsonElement uv : uvs) {
            JsonArray pair = uv.getAsJsonArray();
            if (pair.size() != 2) throw invalid("Expected polygon UV pair");
            coordinates.add(new double[]{finite(pair.get(0)) / (normalized ? 1 : width),
                finite(pair.get(1)) / (normalized ? 1 : height)});
        }
        JsonElement polys = mesh.get("polys");
        if (polys == null) throw invalid("Missing polygon list");
        List<Polygon> result = new ArrayList<>();
        if (polys.isJsonPrimitive() && polys.getAsJsonPrimitive().isString()) {
            int stride = switch (polys.getAsString()) {
                case "tri_list" -> 3;
                case "quad_list" -> 4;
                default -> throw invalid("Unknown polygon list type");
            };
            if (points.size() != directions.size() || points.size() != coordinates.size() || points.size() % stride != 0)
                throw invalid("Mismatched sequential polygon tables");
            if (points.size() / stride > budget[1]) throw invalid("Too many polygons");
            budget[1] -= points.size() / stride;
            for (int i = 0; i < points.size(); i += stride) {
                List<MeshVertex> face = new ArrayList<>(stride);
                for (int j = i; j < i + stride; j++) face.add(corner(points, directions, coordinates, j, j, j));
                result.add(new Polygon(List.copyOf(face)));
            }
        } else {
            JsonArray faces = polys.getAsJsonArray();
            if (faces.size() > budget[1]) throw invalid("Too many polygons");
            budget[1] -= faces.size();
            for (JsonElement faceElement : faces) {
                JsonArray face = faceElement.getAsJsonArray();
                if (face.size() != 3 && face.size() != 4) throw invalid("Expected triangle or quad");
                List<MeshVertex> vertices = new ArrayList<>(face.size());
                for (JsonElement vertex : face) {
                    JsonArray indices = vertex.getAsJsonArray();
                    if (indices.size() != 3) throw invalid("Expected position/normal/UV indices");
                    vertices.add(corner(points, directions, coordinates, index(indices.get(0), points.size()),
                        index(indices.get(1), directions.size()), index(indices.get(2), coordinates.size())));
                }
                result.add(new Polygon(List.copyOf(vertices)));
            }
        }
        return List.copyOf(result);
    }
    private static MeshVertex corner(List<Vec> points, List<Vec> normals, List<double[]> uvs, int p, int n, int uv) {
        double[] pair = uvs.get(uv);
        return new MeshVertex(points.get(p), normals.get(n), pair[0], pair[1]);
    }
    private static int index(JsonElement element, int size) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw invalid("Non-numeric mesh index");
        double value = finite(element);
        if (value != Math.rint(value) || value < 0 || value >= size) throw invalid("Invalid mesh index");
        return (int) value;
    }

    private static Map<String, Uv> faces(JsonElement value, Vec size) {
        Map<String, Uv> out = new LinkedHashMap<>();
        if (value == null || value.isJsonArray()) {
            double u = 0, v = 0;
            if (value != null) {
                JsonArray a = value.getAsJsonArray();
                if (a.size() != 2) throw invalid("Expected box UV pair");
                u = finite(a.get(0)); v = finite(a.get(1));
            }
            double x = size.x, y = size.y, z = size.z;
            out.put("west", new Uv(u, v + z, z, y, "*"));
            out.put("north", new Uv(u + z, v + z, x, y, "*"));
            out.put("east", new Uv(u + z + x, v + z, z, y, "*"));
            out.put("south", new Uv(u + z * 2 + x, v + z, x, y, "*"));
            out.put("up", new Uv(u + z, v, x, z, "*"));
            out.put("down", new Uv(u + z + x, v + z, x, -z, "*"));
        } else for (var entry : value.getAsJsonObject().entrySet()) {
            String face = entry.getKey();
            if (!Set.of("north", "south", "east", "west", "up", "down").contains(face))
                throw invalid("Unknown cube face: " + face);
            JsonObject f = entry.getValue().getAsJsonObject();
            JsonArray uv = f.getAsJsonArray("uv");
            if (uv == null || uv.size() != 2) throw invalid("Expected face UV pair");
            double w = face.equals("east") || face.equals("west") ? size.z : size.x;
            double h = face.equals("up") || face.equals("down") ? size.z : size.y;
            if (f.has("uv_size")) {
                JsonArray s = f.getAsJsonArray("uv_size");
                if (s.size() != 2) throw invalid("Expected UV size pair");
                w = finite(s.get(0)); h = finite(s.get(1));
            }
            double rotation = number(f, "uv_rotation", 0);
            if (!Set.of(0d, 90d, 180d, 270d).contains(rotation)) throw invalid("Invalid UV rotation");
            out.put(face, new Uv(finite(uv.get(0)), finite(uv.get(1)), w, h,
                f.has("material_instance") ? f.get("material_instance").getAsString() : "*", (int) rotation));
        }
        return Map.copyOf(out);
    }

    private static int dimension(JsonObject d, String modern, String legacy) {
        double value = number(d, modern, number(d, legacy, 64));
        if (value < 1 || value > 16384 || value != Math.rint(value)) throw invalid("Invalid texture dimension");
        return (int) value;
    }
    private static Vec vector(JsonObject o, String key, Vec fallback) {return o.has(key) ? vector(o.get(key)) : fallback;}
    private static Vec vector(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 3) throw invalid("Expected three coordinates");
        return new Vec(finite(a.get(0)), finite(a.get(1)), finite(a.get(2)));
    }
    private static boolean bool(JsonObject o, String k, boolean fallback) {return o.has(k) ? o.get(k).getAsBoolean() : fallback;}
    private static double number(JsonObject o, String k, double fallback) {return o.has(k) ? finite(o.get(k)) : fallback;}
    private static double finite(JsonElement e) {
        double value = e.getAsDouble();
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) throw invalid("Invalid geometry number");
        return value;
    }
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
}
