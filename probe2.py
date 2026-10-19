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

def javap(cls, flt=None):
    if cls not in index:
        print("=== MISSING", cls)
        return
    out = subprocess.run(["javap", "-p", "-cp", index[cls], cls], capture_output=True, text=True).stdout
    print("=== JAVAP", cls)
    for line in out.splitlines():
        if "lambda$" in line:
            continue
        if flt and not re.search(flt, line) and not line.startswith(("public class", "public interface", "public abstract class", "public final class", "public enum", "public record", "class ")):
            continue
        print(line)

def simple(name):
    return sorted(c for c in index if c.split(".")[-1] == name and c.startswith(("net.minecraft", "com.mojang", "net.fabricmc")))

FULL = ["Options", "Minecraft", "GuiGraphicsExtractor", "Window", "Screen", "Button", "Button$Builder", "OptionsSubScreen",
        "KeyboardHandler", "Screenshot", "NativeImage", "ScreenEvents", "Screens", "Font", "SystemToast", "ToastManager",
        "Gui", "DeviceInfo", "RenderSystem", "GameRules", "ConfirmScreen", "GenericMessageScreen", "LevelLoadingScreen",
        "Abilities", "IntegratedServer", "WorldOpenFlows", "DisconnectedScreen", "ScreenMouseEvents", "ScreenKeyboardEvents",
        "CommonComponents", "GuiGraphics", "MouseHandler", "Camera", "OptionsList", "FrameTimer", "Util", "ScreenRectangle",
        "ClientPacketListener", "LevelSettings", "WorldOptions", "GameType", "AlertScreen", "ProgressScreen", "TitleScreen",
        "MultiLineTextWidget", "StringWidget", "ClientLevel$ClientLevelData", "KeyEvent", "MouseButtonEvent", "Tooltip",
        "LinearLayout", "HeaderAndFooterLayout", "ClientGameTestContext", "TestWorldBuilder", "WorldCreationUiState"]
for name in FULL:
    for cls in simple(name):
        javap(cls)

FILTERED = {
    "LocalPlayer": r"(?i)set|snap|abilit|pos|rot|input|spectator",
    "Entity": r"(?i)snapTo|setPos|setYRot|setXRot|setOld|moveTo|absSnap|setDeltaMovement|teleport|getYRot|getXRot|getX\(|getY\(|getZ\(|noPhysics|setNoGravity|position\(",
    "ServerPlayer": r"(?i)setGameMode|teleport|gameMode",
    "MinecraftServer": r"(?i)getCommands|createCommandSourceStack|getPlayerList|overworld|getWorldData|getGameRules|halt|isPaused|getWorldPath|getRespawn|spawn",
    "Commands": r"(?i)perform|dispatch",
    "ServerLevel": r"(?i)setDayTime|setWeather|getGameRules|spawn|respawn|getLevelData|setClearWeather|resetWeather",
    "ClientLevel": r"(?i)spawn|getDayTime|getLevelData|respawn",
    "Level": r"(?i)spawn|respawn|getLevelData|getHeight|getMinY|getMaxY",
    "PlayerList": r"(?i)getPlayer\(|getPlayers",
    "LevelData": r".",
    "WritableLevelData": r".",
    "ServerLevelData": r"(?i)spawn|respawn|time|weather|raining|thunder",
    "LevelStorageSource$LevelStorageAccess": r"(?i)delete|close|getLevelId|getLevelPath",
    "GameRenderer": r"(?i)camera|fov|render|getMainCamera|pick",
    "LevelRenderer": r"(?i)allChanged|countRendered|getSectionStatistics|chunk|section",
    "WorldPresets": r".",
}
for name, flt in FILTERED.items():
    for cls in simple(name):
        javap(cls, flt)

for cls in sorted(index):
    if "$" not in cls and re.search(r"(?i)debug.*(gpu|system|renderer)|(gpu|system).*debug|DebugEntry(System|Gpu|Memory)", cls) and cls.startswith("net.minecraft"):
        javap(cls)

srcs = []
for root in [os.path.expanduser("~/.gradle"), ".gradle", "build"]:
    srcs += glob.glob(root + "/**/*sources*.jar", recursive=True)
srcs = [s for s in srcs if "minecraft" in s]
print("=== SOURCE JARS", srcs)
files = {}
for s in srcs:
    with zipfile.ZipFile(s) as z:
        for n in z.namelist():
            if n.endswith(".java") and n not in files:
                files[n] = z.read(n).decode("utf-8", "replace")

def show(path):
    for n, text in files.items():
        if n.endswith("/" + path):
            print("=== SOURCE", n)
            print(text)

def grep(path, pattern, context=0):
    for n, text in files.items():
        if n.endswith("/" + path):
            print("=== GREP", n, pattern)
            lines = text.splitlines()
            keep = set()
            for i, line in enumerate(lines):
                if re.search(pattern, line):
                    keep.update(range(max(0, i - context), min(len(lines), i + context + 1)))
            last = -2
            for i in sorted(keep):
                if i != last + 1:
                    print("--")
                print(i + 1, lines[i])
                last = i

for f in ["WorldOpenFlows.java", "InitialWorldCreationOptions.java", "DeviceInfo.java", "Screenshot.java"]:
    show(f)
grep("Minecraft.java", r"(?i)backend|vulkan|getDeviceInfo|GpuDevice|disconnect|void stop|isWindowActive|getOverlay|overlay|levelExtractor|getFps|frameTime|clearClientLevel|createWorldOpenFlows|getLevelSource|void runTick|gameDirectory|pause|public .*Screen|setScreen|isRunning|running =", 1)
grep("Window.java", r"(?i)backend|device|vulkan|refresh|getWidth|getHeight|isMinimized|getScreenWidth|getGuiScaled|isFocused|public ", 0)
grep("RenderSystem.java", r"(?i)getDevice|backend|api|public static", 0)
grep("CreateWorldScreen.java", r"(?i)createFreshLevel|openFresh|WorldOptions|seed|LevelSettings|createNewWorld|createLevelSettings", 2)
grep("Gui.java", r"(?i)setScreen|public ", 0)
print("=== WHO CALLS getDeviceInfo / backend names")
for n, text in files.items():
    for i, line in enumerate(text.splitlines()):
        if re.search(r"getDeviceInfo\(\)|getBackendName|\.getName\(\).*[Bb]ackend|backendName|getRenderer\(\)|driverVersion|getVendor\(\)", line):
            print(n, i + 1, line.strip())
for n in files:
    if re.search(r"(?i)debug/.*(gpu|system)", n):
        show(n.split("/")[-1])
