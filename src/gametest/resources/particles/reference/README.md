# Mojang particle regression fixtures

Source: [Mojang/bedrock-samples](https://github.com/Mojang/bedrock-samples/tree/46ba6ea985fb5a92d79a9419198f10dda14c199d/resource_pack/particles), revision `46ba6ea985fb5a92d79a9419198f10dda14c199d`.

`basic_flame.json` and `basic_smoke.json` preserve the reference definitions. The client GameTest remaps only their identifiers and texture resource to its generated test sprite; component bodies are unchanged. Flame is driven as a standalone manual emission; smoke is driven through an entity animation and a particle expiration child event. These are test resources, not assets bundled in the production mod. See LICENSE.md for the upstream license (Mojang EULA, not MIT).

State assertions exercise execution and attachment. Screenshots are diagnostic captures, not a pixel-level Bedrock comparison. The remapped test sprite does not validate the original texture's visual appearance.
