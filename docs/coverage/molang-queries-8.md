# Molang queries — part 8

[Back to coverage summary](../../BEDROCK_RP_COVERAGE.md)

| Feature | Status / validation | Limitations | Implementation / tests | Official documentation |
|---|---|---|---|---|
| `query.wing_flap_speed` | Unsupported / code_inspected_only | No production query dispatch/fact provider found in the inspected runtime. Parsing query syntax is not execution support. Not implemented by this pass. This is an outstanding adapter/API gap, not automatically a BP dependency. An optional provider interface is not counted as a functional implementation. | [EntityQueryContext](/Users/matanyah/Documents/ChatGPT/MC-Java/src/main/java/dev/matanyah/bedrockcompat/bedrock/entity/EntityQueryContext.java:23) | [Official contract](https://github.com/MicrosoftDocs/minecraft-creator/blob/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Reference/Content/MolangReference/Examples/MolangConcepts/QueryFunctions/query_wing_flap_speed.md) |
| `query.yaw_speed` | Partial / code_inspected_only | Java-derived/scoped adapter exists, but argument, unit, lifetime, entity/context availability and Bedrock edge-case parity are not certified. | [NativeEntityFacts](/Users/matanyah/Documents/ChatGPT/MC-Java/src/client/java/dev/matanyah/bedrockcompat/client/entity/NativeEntityFacts.java:21) | [Official contract](https://github.com/MicrosoftDocs/minecraft-creator/blob/4186dc4ec84c8980fc25d89c26e1414aa6c2fde2/creator/Reference/Content/MolangReference/Examples/MolangConcepts/QueryFunctions/query_yaw_speed.md) |
