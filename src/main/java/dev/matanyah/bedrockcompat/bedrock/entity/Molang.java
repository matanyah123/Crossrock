package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Compiles expressions once; values and variables belong to the individual entity instance. */
public final class Molang {
    @FunctionalInterface public interface Expression { Object evaluate(Context context); }
    @FunctionalInterface public interface Queries { Object get(String name, List<Object> arguments); }
    public static final class Context {
        private final Map<String, Object> variables = new HashMap<>();
        private final Map<String, Object> temporary = new HashMap<>();
        private final Set<String> variableParents=new HashSet<>(),temporaryParents=new HashSet<>();
        private Map<String, Object> bindings = Map.of();
        private int bindingDepth;
        private Map<String,Object> queryValues = Map.of();
        private final Queries queries;
        private final Random random;
        private final Map<String,Context> entityContexts=new HashMap<>();
        private double channelValue;
        private int loopSteps;
        public Context(Queries queries) { this(queries,0); }
        public Context(Queries queries,long seed) { this.queries = Objects.requireNonNull(queries);random=new Random(seed); }
        public void beginFrame() { temporary.clear();temporaryParents.clear();loopSteps=0; }
        /** Independent evaluation snapshot. Particle-event emitters must instead start with a fresh context. */
        public Context fork() {
            var child=new Context(queries,random.nextLong());
            child.variables.putAll(variables);child.temporary.putAll(temporary);
            child.variableParents.addAll(variableParents);child.temporaryParents.addAll(temporaryParents);
            child.bindings=bindings;child.queryValues=queryValues;child.entityContexts.putAll(entityContexts);
            return child;
        }
        private void loopStep(){if(++loopSteps>16384)throw new IllegalArgumentException("Molang frame loop budget exceeded");}
        public <T> T withThis(double value,java.util.function.Supplier<T> action) {
            double previous=channelValue;channelValue=value;
            try{return action.get();}finally{channelValue=previous;}
        }
        /** Geometry, texture, material and controller-array aliases; never persistent entity variables. */
        public void bind(Map<String, Object> values) {
            Map<String, Object> next = new HashMap<>();
            values.forEach((key,value) -> next.put(canonical(key), value));
            bindings = Map.copyOf(next);
        }
        /** Resource-type scopes must not leak arrays into the next controller or caller. */
        public <T> T withBindings(Map<String,Object> values,java.util.function.Supplier<T> action) {
            Map<String,Object> previous=bindings;
            bind(values);
            try { return action.get(); } finally { bindings=previous; }
        }
        public <T> T withAdditionalBindings(Map<String,Object> values,java.util.function.Supplier<T> action) {
            Map<String,Object> merged=new HashMap<>(bindings);merged.putAll(values);
            return withBindings(merged,action);
        }
        public void setVariable(String name, Object value) { variables.put(name.toLowerCase(Locale.ROOT), value); }
        /** Optional value-only owning/other entity context. Never store Minecraft entity objects. */
        public void setEntityContext(String name,Context context) {
            if(context==null)entityContexts.remove(name);else entityContexts.put(name,context);
        }
        public <T> T withEntityContext(String name,Context context,java.util.function.Supplier<T> action) {
            Context previous=entityContexts.get(name);setEntityContext(name,context);
            try{return action.get();}finally{setEntityContext(name,previous);}
        }
        /** Controller-remapped variables exist only while evaluating that controller. */
        public <T> T withVariables(Map<String,Object> values,java.util.function.Supplier<T> action) {
            Map<String,Object> previous=new HashMap<>();Set<String> absent=new HashSet<>();
            values.forEach((key,value)-> {
                if(variables.containsKey(key))previous.put(key,variables.get(key));else absent.add(key);
                variables.put(key,value);
            });
            try{return action.get();}finally{absent.forEach(variables::remove);variables.putAll(previous);}
        }
        public <T> T withQueries(Map<String,Object> values, java.util.function.Supplier<T> action) {
            Map<String,Object> previous = queryValues;
            Map<String,Object> merged = new HashMap<>(previous);
            merged.putAll(values);
            queryValues = merged;
            try { return action.get(); } finally { queryValues = previous; }
        }
        private Object query(String name, List<Object> args) {
            return args.isEmpty() && queryValues.containsKey(name) ? queryValues.get(name) : MolangQueryFunctions.get(name,args,queries);
        }
        private Object evaluateBound(Object selected) {
            if (!(selected instanceof Expression expression)) return selected;
            if (bindingDepth >= 64) throw new IllegalArgumentException("Cyclic or excessively nested Molang array");
            bindingDepth++;
            try { return expression.evaluate(this); } finally { bindingDepth--; }
        }
        public Object variable(String name) { return variables.getOrDefault(name, 0d); }
        /** Value snapshot for independently ticking child emitters; never share mutable variable scopes. */
        public Map<String,Object> variables(){return Map.copyOf(variables);}
        private Object get(String name) {
            if (name.startsWith("variable.")) return field(variables,name.substring(9));
            if (name.startsWith("temp.")) return field(temporary,name.substring(5));
            if(name.equals("this"))return channelValue;
            if(name.startsWith("context."))return bindings.containsKey(name)?bindings.get(name):entityContexts.get(name.substring(8));
            if (name.startsWith("query.")) return query(name.substring(6), List.of());
            // Historical animation documentation used this spelling before the query namespace.
            if (name.equals("global.key_frame_lerp_time")) return query("key_frame_lerp_time",List.of());
            if (name.equals("true")) return 1d;
            if (name.equals("false")) return 0d;
            if (name.equals("math.pi")) return Math.PI;
            if (bindings.containsKey(name)) return bindings.get(name);
            throw new IllegalArgumentException("Unsupported Molang reference: " + name);
        }
        private static Object field(Map<String,Object> scope,String key) {
            if(scope.containsKey(key))return scope.get(key);
            for(int dot=key.lastIndexOf('.');dot>=0;dot=key.lastIndexOf('.',dot-1)) {
                Object value=scope.get(key.substring(0,dot));
                if(value==null)continue;
                for(String part:key.substring(dot+1).split("\\."))value=value instanceof Map<?,?> fields?fields.get(part):null;
                return value==null?0d:value;
            }
            return 0d;
        }
        private Object put(String name, Object value) {
            value=validValue(value);
            if (name.startsWith("variable.")) replace(variables,variableParents,name.substring(9),value);
            else if (name.startsWith("temp.")) replace(temporary,temporaryParents,name.substring(5),value);
            else throw new IllegalArgumentException("Cannot assign Molang reference: " + name);
            return value;
        }
        private static void replace(Map<String,Object> scope,Set<String> parents,String key,Object value) {
            // Replacing a struct also replaces its previously initialized fields.
            // Otherwise flat zero defaults shadow a fresh bone_orientation_trs map.
            if(parents.contains(key))scope.keySet().removeIf(existing->existing.startsWith(key+"."));
            scope.put(key,value);
            for(int dot=key.indexOf('.');dot>=0;dot=key.indexOf('.',dot+1))parents.add(key.substring(0,dot));
        }
        private Object call(String name, List<Object> args) {
            if (name.startsWith("query.")) return query(name.substring(6), args);
            double a = args.isEmpty() ? 0 : number(args.getFirst());
            double b = args.size() < 2 ? 0 : number(args.get(1));
            double c = args.size() < 3 ? 0 : number(args.get(2));
            if(name.startsWith("math.ease_"))return a+(b-a)*MolangEasing.sample(name.substring(10),c);
            return validValue(switch (name) {
                case "math.sin" -> Math.sin(Math.toRadians(a));
                case "math.cos" -> Math.cos(Math.toRadians(a));
                case "math.abs" -> Math.abs(a);
                case "math.floor" -> Math.floor(a);
                case "math.ceil" -> Math.ceil(a);
                case "math.round" -> (double)Math.round(a);
                case "math.sqrt" -> Math.sqrt(Math.max(0, a));
                case "math.pow" -> Math.pow(a,b);
                case "math.min" -> Math.min(a,b);
                case "math.max" -> Math.max(a,b);
                case "math.clamp" -> Math.max(b,Math.min(c,a));
                case "math.lerp" -> a+(b-a)*c;
                case "math.inverse_lerp" -> a==b?0d:(c-a)/(b-a);
                case "math.mod" -> b == 0 ? 0d : a%b;
                case "math.exp" -> Math.exp(a);
                case "math.ln" -> Math.log(a);
                case "math.trunc" -> a < 0 ? Math.ceil(a) : Math.floor(a);
                case "math.acos" -> Math.toDegrees(Math.acos(a));
                case "math.asin" -> Math.toDegrees(Math.asin(a));
                case "math.atan" -> Math.toDegrees(Math.atan(a));
                case "math.atan2" -> Math.toDegrees(Math.atan2(a,b));
                case "math.lerprotate" -> a + (((b-a)%360+540)%360-180)*c;
                case "math.random" -> a+random.nextDouble()*(b-a);
                case "math.random_integer" -> Math.floor(Math.ceil(a)+random.nextDouble()*(Math.floor(b)-Math.ceil(a)+1));
                case "math.hermite_blend" -> a*a*(3-2*a);
                case "math.min_angle" -> ((a%360)+540)%360-180;
                case "math.sign" -> Math.signum(a);
                case "math.copy_sign" -> Math.copySign(a,b);
                case "math.tan" -> Math.tan(Math.toRadians(a));
                case "math.die_roll","math.die_roll_integer" -> {
                    int count=(int)a;
                    if(count<0||count>4096)throw new IllegalArgumentException("Invalid Molang dice count");
                    double result=0;
                    for(int i=0;i<count;i++)result+=name.endsWith("integer")
                        ?Math.floor(Math.ceil(b)+random.nextDouble()*(Math.floor(c)-Math.ceil(b)+1)):b+random.nextDouble()*(c-b);
                    yield result;
                }
                default -> throw new IllegalArgumentException("Unsupported Molang function: " + name);
            });
        }
    }
    private static Object validValue(Object value) {
        return value instanceof Number n&&!Double.isFinite(n.doubleValue())?0d:value;
    }
    public static double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0; }
    public static boolean truth(Object value) { return number(value) != 0; }
    private static String canonical(String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (name.startsWith("v.")) return "variable."+name.substring(2);
        if (name.startsWith("t.")) return "temp."+name.substring(2);
        if (name.startsWith("q.")) return "query."+name.substring(2);
        if (name.startsWith("c.")) return "context."+name.substring(2);
        return name;
    }
    public static Expression compile(String source) {
        if (source.length() > 131072) throw new IllegalArgumentException("Molang expression exceeds limit");
        Parser parser = new Parser(source);
        Expression result = parser.sequence(false);
        if (!parser.token.isEmpty()) throw parser.error("Unexpected token");
        return context -> { try { return validValue(result.evaluate(context)); } catch (Return value) { return validValue(value.value); } };
    }
    private static final class Return extends RuntimeException {
        final Object value;
        Return(Object value) { super(null, null, false, false); this.value = value; }
    }
    private static final class LoopFlow extends RuntimeException {
        final boolean stop;
        LoopFlow(boolean stop){super(stop?"break outside loop":"continue outside loop",null,false,false);this.stop=stop;}
    }
    private static final class Parser {
        final String source; int offset; String token; Object literal; int depth;
        Parser(String source) { this.source=source; next(); }
        IllegalArgumentException error(String message) { return new IllegalArgumentException(message+" at "+offset+": "+token); }
        void next() {
            literal=null;
            while(offset<source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
            if(offset==source.length()){token="";return;}
            int start=offset;char ch=source.charAt(offset++);
            if(ch=='\'' || ch=='"') {
                StringBuilder value=new StringBuilder(); boolean closed=false;
                while(offset<source.length()) {
                    char c=source.charAt(offset++);
                    if(c==ch){closed=true;break;}
                    if(c=='\\' && offset<source.length()) c=source.charAt(offset++);
                    value.append(c);
                }
                if(!closed) throw error("Unterminated string");
                token="$literal";literal=value.toString();return;
            }
            if(Character.isDigit(ch) || ch=='.' && offset<source.length() && Character.isDigit(source.charAt(offset))) {
                while(offset<source.length() && (Character.isDigit(source.charAt(offset)) || source.charAt(offset)=='.'))offset++;
                if(offset<source.length() && (source.charAt(offset)=='e'||source.charAt(offset)=='E')) {
                    offset++;if(offset<source.length() && (source.charAt(offset)=='+'||source.charAt(offset)=='-'))offset++;
                    while(offset<source.length() && Character.isDigit(source.charAt(offset)))offset++;
                }
                literal=Double.parseDouble(source.substring(start,offset));token="$literal";return;
            }
            if(Character.isLetter(ch)||ch=='_') {
                while(offset<source.length() && (Character.isLetterOrDigit(source.charAt(offset))||source.charAt(offset)=='_'||source.charAt(offset)=='.'))offset++;
                token=canonical(source.substring(start,offset));return;
            }
            if(offset<source.length() && Set.of("==","!=",">=","<=","&&","||","??","->").contains(source.substring(start,offset+1)))offset++;
            token=source.substring(start,offset);
        }
        boolean accept(String expected){if(!token.equals(expected))return false;next();return true;}
        void expect(String expected){if(!accept(expected))throw error("Expected "+expected);}
        Expression sequence(boolean block) {
            List<Expression> statements=new ArrayList<>();
            boolean complex=block;
            while(!token.isEmpty() && !(block && token.equals("}"))) {
                if(accept(";")){complex=true;continue;}
                if(accept("return")){Expression value=expr(0);statements.add(ctx->{throw new Return(value.evaluate(ctx));});}
                else statements.add(expr(0));
                if(accept(";"))complex=true;
                else if(!token.isEmpty() && !(block && token.equals("}")))throw error("Expected semicolon");
            }
            boolean implicitReturn=!complex&&statements.size()==1;
            return ctx->{Object value=0d;for(Expression e:statements)value=e.evaluate(ctx);return implicitReturn?value:0d;};
        }
        Expression expr(int precedence) {
            if(++depth>128)throw error("Molang nesting exceeds limit");
            Expression left=primary();
            while(accept("->")) {
                Expression owner=left,value=expr(10);
                left=ctx->owner.evaluate(ctx) instanceof Context target?value.evaluate(target):0d;
            }
            while (accept("[")) {
                Expression array = left, index = expr(0); expect("]");
                left = ctx -> {
                    Object value = array.evaluate(ctx);
                    if (!(value instanceof List<?> list) || list.isEmpty())
                        throw new IllegalArgumentException("Missing or empty Molang array");
                    double raw = number(index.evaluate(ctx));
                    if (!Double.isFinite(raw)) throw new IllegalArgumentException("Non-finite array index");
                    int i = (int)(Math.max(0, Math.floor(raw)) % list.size());
                    Object selected = list.get(i);
                    return ctx.evaluateBound(selected);
                };
            }
            for(;;){
                int p=priority(token);if(p<precedence)break;
                String operator=token;next();
                if(operator.equals("=")) {
                    if(!(left instanceof Reference r))throw error("Assignment requires variable");
                    Expression rhs=expr(p);left=ctx->ctx.put(r.name,rhs.evaluate(ctx));continue;
                }
                if(operator.equals("?")) {
                    Expression condition=left,yes=expr(0),no=ctx->0d;
                    if(accept(":"))no=expr(p);
                    Expression otherwise=no;left=ctx->truth(condition.evaluate(ctx))?yes.evaluate(ctx):otherwise.evaluate(ctx);continue;
                }
                Expression a=left,b=expr(p+1);left=ctx->binary(operator,a,b,ctx);
            }
            depth--;return left;
        }
        Expression primary(){
            if(accept("break"))return ctx->{throw new LoopFlow(true);};
            if(accept("continue"))return ctx->{throw new LoopFlow(false);};
            if(token.equals("$literal")){Object value=literal;next();return ctx->value;}
            if(accept("(")){Expression result=expr(0);expect(")");return result;}
            if(accept("{")){Expression result=sequence(true);expect("}");return result;}
            if(accept("!")){Expression value=expr(9);return ctx->truth(value.evaluate(ctx))?0d:1d;}
            if(accept("-")){Expression value=expr(9);return ctx->-number(value.evaluate(ctx));}
            if(accept("+"))return expr(9);
            String name=token;
            if(name.isEmpty()||!Character.isLetter(name.charAt(0)))throw error("Expected value");next();
            if(!accept("("))return new Reference(name);
            List<Expression> args=new ArrayList<>();
            if(!accept(")")){do{args.add(expr(0));}while(accept(","));expect(")");}
            if(name.equals("loop")) {
                if(args.size()!=2)throw error("loop requires count and body");
                return ctx-> {
                    double value=number(args.getFirst().evaluate(ctx));
                    if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid Molang loop count");
                    int count=(int)Math.clamp(value,0,1024);
                    for(int i=0;i<count;i++) {
                        ctx.loopStep();
                        try{args.get(1).evaluate(ctx);}catch(LoopFlow flow){if(flow.stop)break;}
                    }
                    return 0d;
                };
            }
            if(name.equals("for_each")) {
                if(args.size()!=3||!(args.getFirst() instanceof Reference reference)||!reference.name.startsWith("variable."))throw error("for_each requires variable, array and body");
                return ctx-> {
                    Object value=args.get(1).evaluate(ctx);
                    if(!(value instanceof List<?> list))throw new IllegalArgumentException("for_each requires an array");
                    for(int i=0;i<Math.min(1024,list.size());i++) {
                        ctx.loopStep();ctx.put(reference.name,list.get(i));
                        try{args.get(2).evaluate(ctx);}catch(LoopFlow flow){if(flow.stop)break;}
                    }
                    return 0d;
                };
            }
            return ctx->ctx.call(name,args.stream().map(e->e.evaluate(ctx)).toList());
        }
        int priority(String op){return switch(op){case"="->0;case"?"->1;case"??"->2;case"||"->3;case"&&"->4;case"==","!="->5;case"<",">","<=",">="->6;case"+","-"->7;case"*","/","%"->8;default->-1;};}
    }
    private record Reference(String name) implements Expression { public Object evaluate(Context ctx){return ctx.get(name);} }
    private static Object binary(String op,Expression a,Expression b,Context ctx){
        Object x=a.evaluate(ctx);
        if(op.equals("&&"))return truth(x)&&truth(b.evaluate(ctx))?1d:0d;
        if(op.equals("||"))return truth(x)||truth(b.evaluate(ctx))?1d:0d;
        if(op.equals("??")) {
            if(a instanceof Reference r && r.name.startsWith("variable.") && !ctx.variables.containsKey(r.name.substring(9)))return b.evaluate(ctx);
            if(a instanceof Reference r && r.name.startsWith("temp.") && !ctx.temporary.containsKey(r.name.substring(5)))return b.evaluate(ctx);
            return x!=null?x:b.evaluate(ctx);
        }
        Object y=b.evaluate(ctx);double u=number(x),v=number(y);
        return switch(op){case"+"->u+v;case"-"->u-v;case"*"->u*v;case"/"->v==0?0d:u/v;case"%"->v==0?0d:u%v;
            case"=="->Objects.equals(x,y)?1d:0d;case"!="->Objects.equals(x,y)?0d:1d;
            case"<"->u<v?1d:0d;case">"->u>v?1d:0d;case"<="->u<=v?1d:0d;case">="->u>=v?1d:0d;default->throw new IllegalArgumentException(op);};
    }
}
