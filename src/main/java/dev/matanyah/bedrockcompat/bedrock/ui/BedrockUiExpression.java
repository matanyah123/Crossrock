package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;

/** Bounded scalar JSON UI expressions; deliberately separate from Molang. Missing data fails closed. */
public final class BedrockUiExpression {
    private interface Expr { JsonPrimitive eval(Function<String,JsonElement> context); }
    private static final Map<String,Expr> CACHE=new LinkedHashMap<>();
    public static JsonElement evaluate(String text,Function<String,JsonElement> context) {
        if(text.length()>4096)throw new IllegalArgumentException("UI expression length limit");
        Expr expression;
        synchronized(CACHE){expression=CACHE.get(text);if(expression==null){expression=new Parser(text).parse();if(CACHE.size()>=1024)CACHE.clear();CACHE.put(text,expression);}}
        return expression.eval(context);
    }
    public static boolean isExpression(String text){return text.startsWith("(");}
    private static double number(JsonPrimitive value){if(!value.isNumber())throw new IllegalArgumentException("UI expression expected number");return finite(value.getAsDouble());}
    private static double finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite UI expression");return value;}
    public static boolean bool(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("UI expression expected boolean");return value.getAsBoolean();}
    private static JsonPrimitive binary(String op,JsonPrimitive a,JsonPrimitive b){
        return switch(op){
            case "=","=="->new JsonPrimitive(equal(a,b));case "!="->new JsonPrimitive(!equal(a,b));
            case "+"->a.isString()&&b.isString()?concatenate(a.getAsString(),b.getAsString()):new JsonPrimitive(finite(number(a)+number(b)));
            // Literal removal also supports containment predicates; no regex interpretation.
            case "-"->a.isString()&&b.isString()?new JsonPrimitive(a.getAsString().replace(b.getAsString(),"")):new JsonPrimitive(finite(number(a)-number(b)));case "*"->new JsonPrimitive(finite(number(a)*number(b)));case "/"->new JsonPrimitive(finite(number(a)/number(b)));
            case "<"->new JsonPrimitive(number(a)<number(b));case ">"->new JsonPrimitive(number(a)>number(b));case "<="->new JsonPrimitive(number(a)<=number(b));case ">="->new JsonPrimitive(number(a)>=number(b));
            default->throw new IllegalArgumentException("Unsupported UI operator: "+op);
        };
    }
    private static JsonPrimitive concatenate(String a,String b){
        if((long)a.length()+b.length()>65536)throw new IllegalArgumentException("UI expression string length limit");
        return new JsonPrimitive(a+b);
    }
    private static boolean equal(JsonPrimitive a,JsonPrimitive b){return a.isNumber()&&b.isNumber()?number(a)==number(b):a.isBoolean()&&b.isBoolean()?a.getAsBoolean()==b.getAsBoolean():a.isString()&&b.isString()&&a.getAsString().equals(b.getAsString());}
    private static final class Parser {
        final String source;int offset,nodes,depth;String token;JsonPrimitive literal;
        Parser(String source){this.source=source;next();}
        Expr parse(){Expr result=expression(0);if(!token.isEmpty())throw error("Unexpected token");return result;}
        Expr expression(int min){
            if(++depth>48||++nodes>512)throw error("Complexity limit");
            Expr left;
            if(token.equals("(") ){next();left=expression(0);expect(")");}
            else if(Set.of("not","!","-","+").contains(token)){String op=token;next();Expr value=expression(6);left=c->{var v=value.eval(c);return op.equals("not")||op.equals("!")?new JsonPrimitive(!bool(v)):new JsonPrimitive(finite((op.equals("-")?-1:1)*number(v)));};}
            else if(literal!=null){var value=literal;left=c->value;next();}
            else if(token.startsWith("$")||token.startsWith("#")){String name=token;next();left=c->{var v=c.apply(name);if(v==null||!v.isJsonPrimitive())throw error("Unavailable scalar "+name);return v.getAsJsonPrimitive();};}
            else throw error("Expected value");
            while(precedence(token)>=min&&precedence(token)>0){String op=token;int prec=precedence(op);next();Expr right=expression(prec+1),previous=left;left=c->{var a=previous.eval(c);if(op.equals("and")||op.equals("&&"))return new JsonPrimitive(bool(a)&&bool(right.eval(c)));if(op.equals("or")||op.equals("||"))return new JsonPrimitive(bool(a)||bool(right.eval(c)));return binary(op,a,right.eval(c));};}
            depth--;return left;
        }
        int precedence(String op){return switch(op){case "or","||"->1;case "and","&&"->2;case "=","==","!=","<",">","<=",">="->3;case "+","-"->4;case "*","/"->5;default->0;};}
        void expect(String value){if(!token.equals(value))throw error("Expected "+value);next();}
        void next(){
            literal=null;while(offset<source.length()&&Character.isWhitespace(source.charAt(offset)))offset++;
            if(offset==source.length()){token="";return;}int start=offset;char ch=source.charAt(offset++);
            if(ch=='\''||ch=='"'){var s=new StringBuilder();boolean closed=false;while(offset<source.length()){char c=source.charAt(offset++);if(c==ch){closed=true;break;}if(c=='\\'){if(offset==source.length())throw error("Unfinished escape");c=source.charAt(offset++);if(c!=ch&&c!='\\')throw error("Unsupported escape");}s.append(c);}if(!closed)throw error("Unfinished string");token=source.substring(start,offset);literal=new JsonPrimitive(s.toString());return;}
            if(Character.isDigit(ch)||ch=='.'){while(offset<source.length()&&(Character.isDigit(source.charAt(offset))||source.charAt(offset)=='.'))offset++;token=source.substring(start,offset);try{literal=new JsonPrimitive(finite(Double.parseDouble(token)));}catch(NumberFormatException e){throw error("Invalid number");}return;}
            if(Character.isLetter(ch)||ch=='$'||ch=='#'||ch=='_'){while(offset<source.length()&&(Character.isLetterOrDigit(source.charAt(offset))||("_.$#".indexOf(source.charAt(offset))>=0||((ch=='$'||ch=='#')&&source.charAt(offset)==':'))))offset++;token=source.substring(start,offset);if(token.equals("true")||token.equals("false"))literal=new JsonPrimitive(Boolean.parseBoolean(token));return;}
            token=String.valueOf(ch);if(offset<source.length()&&Set.of("==","!=","<=",">=","&&","||").contains(token+source.charAt(offset)))token+=source.charAt(offset++);
        }
        IllegalArgumentException error(String message){return new IllegalArgumentException(message+" at UI expression offset "+offset);}
    }
    private BedrockUiExpression(){}
}
