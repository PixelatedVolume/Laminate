CASCADING ITEM DEFINITIONS
==========================


Purpose
-------
This mod gives conditional item definitions the option to fall back to the
definition provided by a lower-priority resource pack.  This mimics the
behavior of the original Custom Item Textures (CIT) mod, which allowed
replacement rules from multiple packs to be applied to the same item.  Rather
than CIT's custom priority numbers, the pack order determines priority.


Installation
------------
Add the mod JAR to your Fabric mods folder.


Usage
-----
This mod adds a feature to item model definitions which can be used by
resource pack designers.  It is always active when the mod is installed.

Recall the JSON files found at `assets/minecraft/item` are *item definitions*;
the *item models* used for rendering are under `assets/minecraft/models/item`.

To indicate that an item definition should use the next-highest-priority
pack's definition as its fallback, create a field "definition_fall_through"
as a sister of your "fallback" field, and set it to `true`.  The fallback thus
annotated will be replaced by the contents of the next pack's definition for
that item.

Packs still ought to provide a valid vanilla fallback model.
