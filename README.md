# Auto Crossbow Loader (Fabric, client-side, MC 1.21.11)

## Build
Requires JDK 21 and Gradle 9.2+ (or open the folder in IntelliJ IDEA and let it import).

    gradle build

The jar is written to `build/libs/auto-crossbow-loader-1.0.0.jar` (use this one, not `-sources`).
If Gradle cannot resolve `yarn_mappings` or `fabric_version`, copy the current values for
1.21.11 from https://fabricmc.net/develop into `gradle.properties`.

## Install
1. Install Fabric Loader 0.19.5+ for Minecraft 1.21.11.
2. Put Fabric API (1.21.11 build) and the mod jar in `.minecraft/mods`.
3. Launch, then check Options > Controls > Key Binds > "Auto Crossbow Loader".

## Use
- `#` opens the Alpha PvP menu (default key: UK `#`, GLFW BACKSLASH). `]` stops the crossbow loader instantly. Both rebindable.
- Keep arrows or fireworks in your inventory and an unloaded crossbow in the hotbar.
- Tuning constants are at the top of `LoaderController.java`.

Many servers prohibit this kind of automation. Use it in singleplayer or where it is allowed.
