# Lyrics Sky (Forge 1.20.1, client-side)

Commands (client-side, no server needed):
  /lyrics play <song name or YouTube URL>
  /lyrics stop
  /lyrics toggle
  /lyrics volume <0-100>

## Build
1. Download the Forge 1.20.1 MDK (47.3.x) from files.minecraftforge.net, copy its gradle wrapper
   (gradlew, gradlew.bat, gradle/) into this folder.
2. ./gradlew shadowJar   (needs JDK 17)
3. Take build/libs/LyricsSky-forge-1.20.1-1.0.0.jar -> your .minecraft/mods folder.

## No local setup? Build on GitHub
Push this folder to a new GitHub repo -> Actions tab -> "Build mod jar" -> download the "LyricsSky-jar" artifact.
