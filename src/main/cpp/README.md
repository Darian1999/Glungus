# glungus native lol

so this is the experimental C++ version of RaycastUtil idk

its `src/main/java/org/xiaojian999/superpowers/util/RaycastUtil.java` but in C++ via JNI lol

## what it does

- C++ does the dumb vector math stuff (normalize, maxEnd, squared distance n whatever)
- java still does the actual minecraft raycast btw, world.raycast and ProjectileUtil thing
- if the native lib isnt there it just falls back to java lol you dont even need to build this

## layout

- `RaycastUtil.h` - pure Vec3 math + JNI declarations lol
- `RaycastUtil.cpp` - does the math and JNI exports go brrr
- `CMakeLists.txt` - cmake stuff idk
- `README.md` - you are here lol

## what is native-accelerated n stuff

- **hasEntityCloser** - squared distance compare, uses C++ branchless thing lol
- **blockOrMax** - picks block pos or maxEnd idk
- **computeMaxEnd / normalize / squaredDistance** - vector/quat stuff, like GlungFastMath but in C++ lol
- **raycast / blockRaycast** - still java btw, only the vector part is native (voxel traversal is too jank to port lol)

full world raycast stays in java btw, didnt wanna JNI the whole ServerWorld lol

## how to build

needs:
- cmake 3.15+
- C++17 toolchain
- JDK for jni.h lol

just do

```
cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native -j
```

and grab the lib from `build/native/lib/` lol

- linux: `libglungus_raycast.so`
- windows: `glungus_raycast.dll`
- mac: `libglungus_raycast.dylib` lol i dont have a mac to test

if cmake complains just try again one more try lol

## how to run with it

you dont need it btw, mod works fine without it

but if you built it just do

```
cp build/native/lib/libglungus_raycast.so run/
./gradlew runClient -Djava.library.path=run
```

or

```
./gradlew runClient -Djava.library.path=build/native/lib
```

`RaycastUtil.isNativeAvailable()` will be true if it loaded lol, if not it just silently uses java

## jar packaging (optional, kinda jank)

if you wanna bundle it in the jar lol

```
mkdir -p src/main/resources/native/linux-x64
cp build/native/lib/libglungus_raycast.so src/main/resources/native/linux-x64/
```

but `RaycastUtil.java` uses `System.loadLibrary` atm so you gotta add a extractor in `Glungus.java` to pull it to a temp file lol i didnt do that yet

## benchmarking lol

kept `raycastJavaOnly` as pure java reference for testing

```java
for (int i=0;i<1_000_000;i++) RaycastUtil.computeMaxEnd(start, dir, 32.0);
```

native only wins at high batch sizes btw, JNI crossing is like 20-50ns + array alloc so single calls are actually slower lol

1.1.2 math library speed boost but for raycast this time lol, still kinda jank

## dev notes

- lib name is `glungus_raycast` dont forget lol
- JNI names are `Java_org_xiaojian999_superpowers_util_RaycastUtil_*` 
- version is still in `gradle.properties` dont forget to update it lol (forgot to update gradle.prop some commits back lol)
- to regenerate JNI header if you change java:

```
javac -h src/main/cpp -cp $(./gradlew -q printClasspath) src/main/java/org/xiaojian999/superpowers/util/RaycastUtil.java
```

but idk just check `RaycastUtil.h` lol

## safety n stuff

- `NewDoubleArray` OOM check lol returns null and java checks it
- normalize guards zero-vector -> {0,0,0} so no NaN lol
- uses `SetDoubleArrayRegion` copy not `GetPrimitiveArrayCritical` so its safe but slower idk
- `NATIVE_LOADED` probes `nativeIsAvailable()` so if symbols mismatch it just falls back lol

tweaked it for performance but idk its still kinda jank

## limitations / wip stuff

- [ ] no `world.raycast` in C++ lol - would need to replicate voxel traversal + block state via JNI and thats expensive lol
- [ ] `Predicate<Entity>` filter still java-only idk about interop
- [ ] not wired to Fabric lifecycle, manual `java.library.path` only lol
- single-player only btw, doesnt run on dedicated servers lol i tried (same as rest of glungus)
- experimental btw, might be slower than java for single calls lol i just kept adding stuff 1.1.0 style

## credits

made by xiaojian999

i just fixed too many bugs earlier in development so if its broken idk man open an issue lol
