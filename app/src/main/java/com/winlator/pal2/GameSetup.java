package com.winlator.pal2;

import android.content.Context;

import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.WineRegistryEditor;
import com.winlator.inputcontrols.InputControlsManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public abstract class GameSetup {
    public static final String CONTAINER_NAME = "PAL2";
    public static final int PROFILE_ID = 103;

    public static Container findContainer(ContainerManager containerManager) {
        for (Container container : containerManager.getContainers()) {
            if (CONTAINER_NAME.equals(container.getName())) return container;
        }
        return null;
    }

    public interface ContainerCallback {
        void onContainerReady(Container container);
    }

    public static void ensureContainerAsync(final Context context, final ContainerCallback callback) {
        // remove half-created containers (interrupted first run) that would crash the loader
        File homeDir = new File(com.winlator.xenvironment.RootFS.find(context).getRootDir(), "home");
        File[] dirs = homeDir.listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                if (d.isDirectory() && d.getName().startsWith("xuser-") && !new File(d, ".container").isFile()) {
                    com.winlator.core.FileUtils.delete(d);
                }
            }
        }
        final ContainerManager containerManager = new ContainerManager(context);
        Container existing = findContainer(containerManager);
        if (existing != null) {
            callback.onContainerReady(existing);
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            // 2x integer scale of the game's native 640x480. Measured on a OnePlus Ace 3: the frame
            // pipeline (X11 SHM + Java texture upload) is bandwidth-bound — 1920x1440 caps at 12fps,
            // 1280x960 doubles it to 24fps while staying integer-scaled and sharp (lanczos shader).
            data.put("screenSize", "1280x960");
            data.put("envVars", "MESA_EXTENSION_MAX_YEAR=2003 LANG=zh_TW.UTF-8 LC_ALL=zh_TW.UTF-8");
            data.put("graphicsDriver", "turnip,gladio");
            data.put("dxwrapper", "wined3d");
            data.put("dxwrapperConfig", "ddrawWrapper=cnc-ddraw");
            data.put("audioDriver", "alsa");
            data.put("audioDriverConfig", "latencyMillis=60");
            data.put("box64Preset", Box64Preset.PERFORMANCE);
            data.put("drives", "D:"+GameInstaller.getGameDir(context)+"E:"+AppUtils.INTERNAL_STORAGE);

            containerManager.createContainerAsync(data, (container) -> {
                if (container != null) applyTweaks(context, container);
                callback.onContainerReady(container);
            });
        }
        catch (JSONException e) {
            callback.onContainerReady(null);
        }
    }

    private static void applyTweaks(Context context, Container container) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            // Old DirectDraw-era game: run it against a Win98 profile for maximum compatibility.
            registryEditor.setStringValue("Software\\Wine\\AppDefaults\\Pal2.exe", "Version", "win98");
            registryEditor.setStringValue("Software\\Wine\\AppDefaults\\Pal2.exe", "Debug", "fixme-all");
        }

        // Register D: as a harddisk so GetDriveType() reports a fixed drive; force Big5 ANSI
        // codepage so the game's GDI font name (新細明體) and text decode correctly; map the
        // classic TW font names onto our compact-metrics replacement family.
        File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
            registryEditor.setStringValue("Software\\Wine\\Drives", "D:", "harddisk");
            registryEditor.setStringValue("System\\CurrentControlSet\\Control\\Nls\\CodePage", "ACP", "950");
            registryEditor.setStringValue("System\\CurrentControlSet\\Control\\Nls\\CodePage", "OEMCP", "950");
            registryEditor.setStringValue("Software\\Microsoft\\Windows NT\\CurrentVersion\\FontSubstitutes", "新細明體", "PMingLiU");
            registryEditor.setStringValue("Software\\Microsoft\\Windows NT\\CurrentVersion\\FontSubstitutes", "細明體", "MingLiU");
        }

        ensureInputControlsProfile(context);
    }

    // The game draws its dialogue via GDI and asks for Traditional Chinese fonts; without the real
    // faces Wine substitutes a mismatched-metrics font and every line gets its bottom clipped.
    public static void ensureCjkFonts(Context context, Container container) {
        File fontsDir = new File(container.getRootDir(), ".wine/drive_c/windows/Fonts");
        if (!fontsDir.isDirectory()) fontsDir.mkdirs();
        String[] fontNames = {"pmingliu-pal2.ttf", "mingliu-pal2.ttf", "msjh.ttc", "simsun.ttc", "mingliub.ttc"};
        for (String name : fontNames) {
            File dst = new File(fontsDir, name);
            if (dst.isFile()) continue;
            try (java.io.InputStream inStream = context.getAssets().open("pal2-fonts/"+name);
                 java.io.FileOutputStream outStream = new java.io.FileOutputStream(dst)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = inStream.read(buffer)) > 0) outStream.write(buffer, 0, length);
            }
            catch (Exception ignored) {}
        }
    }

    // Wine's ANSI codepage must be Big5 for the game's GDI font lookups (新細明體) and text;
    // the rootfs only ships en/pt/ru locales, so install a generated zh_TW.UTF-8 from assets.
    public static void ensureRootfsLocale(Context context) {
        File localeDir = new File(context.getFilesDir(), "rootfs/usr/lib/locale/zh_TW.UTF-8");
        if (localeDir.isDirectory()) {
            String[] files = localeDir.list();
            if (files != null && files.length >= 12) return;
        }
        localeDir.mkdirs();
        try {
            String[] names = context.getAssets().list("pal2-locale");
            if (names == null) return;
            for (String name : names) {
                try (java.io.InputStream inStream = context.getAssets().open("pal2-locale/"+name);
                     java.io.FileOutputStream outStream = new java.io.FileOutputStream(new File(localeDir, name))) {
                    byte[] buffer = new byte[65536];
                    int length;
                    while ((length = inStream.read(buffer)) > 0) outStream.write(buffer, 0, length);
                }
            }
        }
        catch (Exception ignored) {}
    }

    public static void ensureInputControlsProfile(Context context) {
        InputControlsManager inputControlsManager = new InputControlsManager(context);
        inputControlsManager.loadProfiles(true);
        if (inputControlsManager.getProfile(PROFILE_ID) != null) return;

        String profileJSON = "{\"id\": "+PROFILE_ID+", \"name\": \"PAL2\", \"cursorSpeed\": 1.0, \"elements\": ["
            // left cluster: movement + menu
            + dPad(0.14, 0.74, 1.4) + ","
            + element("BUTTON", "CIRCLE", 0.05, 0.22, "Esc", "KEY_ESC") + ","
            + element("BUTTON", "CIRCLE", 0.15, 0.10, "上页", "KEY_PG_UP") + ","
            + element("BUTTON", "CIRCLE", 0.28, 0.10, "下页", "KEY_PG_DOWN") + ","
            // right cluster: confirm / fast-forward
            + element("BUTTON", "CIRCLE", 0.88, 0.80, "确认", "KEY_ENTER") + ","
            + element("BUTTON", "CIRCLE", 0.95, 0.62, "空格", "KEY_SPACE") + ","
            + "{\"type\": \"BUTTON\", \"shape\": \"SQUARE\", \"bindings\": [\"KEY_CTRL_L\", \"NONE\", \"NONE\", \"NONE\"], \"scale\": 1, \"x\": 0.80, \"y\": 0.92, \"toggleSwitch\": true, \"text\": \"快进\", \"iconId\": 0}" + ","
            + element("BUTTON", "CIRCLE", 0.93, 0.12, "取消", "KEY_ESC")
            + "]}";

        try {
            File profileFile = com.winlator.inputcontrols.ControlsProfile.getProfileFile(context, PROFILE_ID);
            com.winlator.core.FileUtils.writeString(profileFile, profileJSON);
        }
        catch (Exception ignored) {}
    }

    private static String element(String type, String shape, double x, double y, String text, String binding) {
        return "{\"type\": \""+type+"\", \"shape\": \""+shape+"\", \"bindings\": [\""+binding+"\", \"NONE\", \"NONE\", \"NONE\"], "
            + "\"scale\": 1, \"x\": "+x+", \"y\": "+y+", \"toggleSwitch\": false, \"text\": \""+text+"\", \"iconId\": 0}";
    }

    private static String dPad(double x, double y, double scale) {
        return "{\"type\": \"STICK\", \"shape\": \"CIRCLE\", \"bindings\": [\"KEY_UP\", \"KEY_RIGHT\", \"KEY_DOWN\", \"KEY_LEFT\"], "
            + "\"scale\": "+scale+", \"x\": "+x+", \"y\": "+y+", \"toggleSwitch\": false, \"text\": \"\", \"iconId\": 0}";
    }
}
