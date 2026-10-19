import os, re, subprocess, zipfile, glob

jars = [l.strip() for l in open("build/cp.txt") if l.strip().endswith(".jar")]
index = {}
for j in jars:
    try:
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                if n.endswith(".class"):
                    index.setdefault(n[:-6].replace("/", "."), j)
    except Exception as e:
        print("bad jar", j, e)

print("=== JARS")
for j in jars:
    print(j)

print("=== CLASSES MATCHING")
pat = re.compile(r"(?i)vulkan|graphicsapi|graphicsbackend|backend|gpudevice|apipreference|restart")
for c in sorted(index):
    if "$" not in c and pat.search(c) and (c.startswith("net.minecraft") or c.startswith("com.mojang") or c.startswith("net.fabricmc")):
        print(c)

# Find sources jar
srcs = []
home = os.path.expanduser("~/.gradle")
for root in [home, ".gradle", "build"]:
    srcs += glob.glob(root + "/**/*sources*.jar", recursive=True)
print("=== SOURCE JARS")
for s in srcs:
    print(s)

def by_simple(name):
    return [c for c in index if c.split(".")[-1] == name]

def javap(cls):
    out = subprocess.run(["javap", "-p", "-cp", index[cls], cls], capture_output=True, text=True)
    return out.stdout + out.stderr

SIMPLE = ["Options", "Minecraft", "GuiGraphicsExtractor", "Window", "VideoSettingsScreen", "OptionsSubScreen",
          "Button", "Screen", "WorldOpenFlows", "CreateWorldScreen", "LevelSettings", "WorldOptions", "GpuDevice",
          "RenderSystem", "KeyboardHandler", "Camera", "GameRenderer", "Screenshot", "NativeImage", "ScreenEvents",
          "Screens", "Font", "SystemToast", "OptionInstance", "OptionsList", "WorldCreationContext", "WorldPresets",
          "Entity", "LocalPlayer", "TestWorldBuilder", "ClientGameTestContext", "TestSingleplayerContext",
          "FrameTimer", "LevelStorageSource", "GameRules", "PresetEditor", "WorldCreationUiState", "MultiLineLabel",
          "ConfirmScreen", "TitleScreen", "PauseScreen", "LevelLoadingScreen", "ReceivingLevelScreen",
          "GenericMessageScreen", "ClientLevel", "ServerLevel", "IntegratedServer", "MinecraftServer", "Difficulty",
          "GameType", "FeatureFlags", "WorldDataConfiguration", "DebugScreenOverlay", "GraphicsStatus",
          "ClientLifecycleEvents", "ClientTickEvents", "WorldRenderEvents", "HudElementRegistry", "StringWidget",
          "AbstractWidget", "Tooltip", "CycleButton", "PlayerInfo", "ChunkPos", "Heightmap"]
for name in SIMPLE:
    for cls in by_simple(name):
        if cls.startswith(("net.minecraft", "com.mojang", "net.fabricmc")):
            print("=== JAVAP", cls)
            print(javap(cls))

for cls in sorted(index):
    if "$" not in cls and pat.search(cls) and cls.startswith(("net.minecraft", "com.mojang")):
        print("=== JAVAP", cls)
        print(javap(cls))

# Extract selected decompiled sources
os.makedirs("sources", exist_ok=True)
want = re.compile(r"(?i)(Options|VideoSettingsScreen|Minecraft|Main|Window|.*Vulkan.*|.*GraphicsApi.*|.*Backend.*|WorldOpenFlows|CreateWorldScreen|DebugScreenOverlay|FrameTimer|Screenshot|OptionsSubScreen|GpuDevice|RenderSystem)\.java$")
for s in srcs:
    try:
        with zipfile.ZipFile(s) as z:
            for n in z.namelist():
                if want.search(n.split("/")[-1]) and (n.startswith("net/minecraft/client") or n.startswith("com/mojang/blaze3d")):
                    dest = os.path.join("sources", n)
                    os.makedirs(os.path.dirname(dest), exist_ok=True)
                    with open(dest, "wb") as f:
                        f.write(z.read(n))
    except Exception as e:
        print("bad src", s, e)
print("=== EXTRACTED SOURCES")
for root, _, files in os.walk("sources"):
    for f in files:
        print(os.path.join(root, f))

for root, _, files in os.walk("sources"):
    for f in files:
        p = os.path.join(root, f)
        if re.search(r"(?i)vulkan|graphicsapi|backend|VideoSettingsScreen|/Main\.java", p):
            print("=== SOURCE", p)
            print(open(p, errors="replace").read())
p = [os.path.join(r, f) for r, _, fs in os.walk("sources") for f in fs if f == "Options.java"]
for x in p:
    print("=== GREP", x)
    for i, line in enumerate(open(x, errors="replace")):
        if re.search(r"(?i)graphic|api|vulkan|backend|preference", line):
            print(i + 1, line.rstrip())
