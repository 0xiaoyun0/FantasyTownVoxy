package dev.xantha.vss.client;
import net.minecraft.client.gui.screens.Screen;
/** The merged mod registers one Sodium entry through VoxyConfigScreenPages. */
public final class VSSVoxyOptionsIntegration {
    public static boolean isSodiumPresent(){return true;}
    public static Screen createSodiumConfigScreen(Screen parent){return me.cortex.voxy.hxz.client.SodiumPanel.createScreen(parent);}
    public static boolean registerSodium08ConfigBridge(){return true;}
    public static boolean registerSodiumOptionsApiBridge(){return false;}
    public static void addPage(Object screen){}
    public static void addPage(java.util.List<?> pages){}
}
