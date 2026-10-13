package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Resource-owned item selection, separate from the owning entity's definition. */
public record BedrockAttachable(BedrockClientEntity model, Map<String,Molang.Expression> items,
                               List<Molang.Expression> parentSetup) {
    public static BedrockAttachable parse(JsonObject source) {
        var model=BedrockClientEntity.parse(source);
        var description=source.getAsJsonObject("description");
        Map<String,Molang.Expression> items=new LinkedHashMap<>();
        var item=description.get("item");
        if(item==null)items.put(model.identifier(),Molang.compile("1"));
        else if(item.isJsonPrimitive())items.put(item.getAsString(),Molang.compile("1"));
        else for(var entry:item.getAsJsonObject().entrySet())items.put(entry.getKey(),Molang.compile(entry.getValue().getAsString()));
        List<Molang.Expression> setup=new ArrayList<>();
        var scripts=description.getAsJsonObject("scripts");
        if(scripts!=null&&scripts.has("parent_setup")) {
            var value=scripts.get("parent_setup");
            if(value.isJsonArray())for(var expression:value.getAsJsonArray())setup.add(Molang.compile(expression.getAsString()));
            else setup.add(Molang.compile(value.getAsString()));
        }
        return new BedrockAttachable(model,Collections.unmodifiableMap(items),List.copyOf(setup));
    }
    public boolean matches(String item,Molang.Queries queries,Map<String,Object> contextValues) {
        return matches(item,queries,contextValues,null);
    }
    public boolean matches(String item,Molang.Queries queries,Map<String,Object> contextValues,Molang.Context owner) {
        var expression=items.get(item);
        if(expression==null)expression=items.get(BedrockItemIds.bedrock(item));
        if(expression==null)return false;
        var context=new Molang.Context(queries);
        context.bind(contextValues);
        context.setEntityContext("owning_entity",owner);
        return Molang.truth(expression.evaluate(context));
    }
}
