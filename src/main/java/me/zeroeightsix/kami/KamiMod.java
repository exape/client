package me.zeroeightsix.kami;

import com.google.common.base.Converter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.command.CommandManager;
import me.zeroeightsix.kami.event.ForgeEventProcessor;
import me.zeroeightsix.kami.event.KamiEventBus;
import me.zeroeightsix.kami.gui.kami.KamiGUI;
import me.zeroeightsix.kami.manager.ManagerLoader;
import me.zeroeightsix.kami.manager.managers.FileInstanceManager;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.module.modules.client.CommandConfig;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.setting.SettingsRegister;
import me.zeroeightsix.kami.util.ConfigUtils;
import me.zeroeightsix.kami.util.graphics.font.KamiFontRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Created by 086 on 7/11/2017.
 * Updated by l1ving on 25/03/19
 * Updated by Dewy on 09/04/2020
 */
@Mod(
        modid = KamiMod.MODID,
        name = KamiMod.MODNAME,
        version = KamiMod.VER_FULL_BETA
)
public class KamiMod {

    public static final String MODNAME = "KAMI Blue";
    public static final String MODID = "kamiblue";
    public static final String VER_FULL_BETA = "v1.1.7-beta"; // this is changed to v1.x.x-commit for debugging by automatic builds
    public static final String VER_SMALL = "v1.1.7-beta"; // shown to the user, unchanged
    public static final String VER_STABLE = "v1.1.6"; // used for update checking

    public static final String APP_ID = "638403216278683661";

    public static final String DOWNLOADS_API = "https://kamiblue.org/api/v1/downloads.json";
    public static final String DONATORS_JSON = "https://raw.githubusercontent.com/kami-blue/assets/assets/assets/donators.json";
    public static final String CAPES_JSON = "https://raw.githubusercontent.com/kami-blue/assets/assets/assets/capes.json";
    public static final String GITHUB_LINK = "https://github.com/kami-blue/";
    public static final String WEBSITE_LINK = "https://kamiblue.org";

    public static final String KAMI_KANJI = "\u30ab\u30df\u30d6\u30eb";
    public static final char color = '\u00A7';
    public static final char separator = '|';

    public static final String DIRECTORY = "kamiblue/";
    public static final Logger log = LogManager.getLogger("KAMI Blue");

    public static Thread MAIN_THREAD;

    public static String latest; // latest version (null if no internet or exception occurred)
    public static boolean isLatest;
    public static boolean hasAskedToUpdate = false;

    @Mod.Instance
    private static KamiMod INSTANCE;

    private KamiGUI guiManager;
    public CommandManager commandManager;
    public Setting<JsonObject> guiStateSetting = Settings.custom("gui", new JsonObject(), new Converter<JsonObject, JsonObject>() {
        @Override
        protected JsonObject doForward(@Nullable JsonObject jsonObject) {
            return jsonObject;
        }

        @Override
        protected JsonObject doBackward(@Nullable JsonObject jsonObject) {
            return jsonObject;
        }
    }).buildAndRegister("");

    @SuppressWarnings("ResultOfMethodCallIgnored") // Java meme
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        final File directory = new File(DIRECTORY);
        if (!directory.exists()) directory.mkdir();

        MAIN_THREAD = Thread.currentThread();
        updateCheck();
        ModuleManager.preLoad();
        ManagerLoader.preLoad();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (CommandConfig.INSTANCE.getCustomTitle().getValue()) {
            Display.setTitle(MODNAME + " " + KAMI_KANJI + " " + VER_SMALL);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        log.info("Initializing " + MODNAME + " " + VER_FULL_BETA);

        ModuleManager.load();
        ManagerLoader.load();

        MinecraftForge.EVENT_BUS.register(ForgeEventProcessor.INSTANCE);

        guiManager = new KamiGUI();
        guiManager.initializeGUI();
        commandManager = new CommandManager();

        FileInstanceManager.fixEmptyFiles();

        /* Custom static Settings, which can't register normally if they're static */
        SettingsRegister.register("commandPrefix", Command.commandPrefix);
        ConfigUtils.INSTANCE.loadAll();

        // After settings loaded, we want to let the enabled modules know they've been enabled (since the setting is done through reflection)
        for (Module module : ModuleManager.getModules()) {
            if (module.getAlwaysListening()) {
                KamiEventBus.INSTANCE.subscribe(module);
            }
            if (module.isEnabled()) module.enable();
        }

        // Need to reload the font after the settings were loaded
        KamiFontRenderer.INSTANCE.reloadFonts();

        log.info(MODNAME + " Mod initialized!");
    }

    public static KamiMod getInstance() {
        return INSTANCE;
    }

    public KamiGUI getGuiManager() {
        return this.guiManager;
    }

    public void setGuiManager(KamiGUI guiManager) {
        this.guiManager = guiManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public void updateCheck() {
        try {
            KamiMod.log.info("Attempting KAMI Blue update check...");

            JsonParser parser = new JsonParser();
            String latestVersion = parser.parse(IOUtils.toString(new URL(DOWNLOADS_API), Charset.defaultCharset())).getAsJsonObject().getAsJsonObject("stable").get("name").getAsString();

            isLatest = latestVersion.equals(VER_STABLE);
            latest = latestVersion;

            if (!isLatest) {
                KamiMod.log.warn("You are running an outdated version of KAMI Blue.\nCurrent: " + VER_STABLE + "\nLatest: " + latestVersion);

                return;
            }

            KamiMod.log.info("Your KAMI Blue (" + VER_STABLE + ") is up-to-date with the latest stable release.");
        } catch (IOException e) {
            latest = null;

            KamiMod.log.error("Oes noes! An exception was thrown during the update check.");
            e.printStackTrace();
        }
    }
}
