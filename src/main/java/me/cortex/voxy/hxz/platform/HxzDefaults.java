package me.cortex.voxy.hxz.platform;

import java.nio.file.*;
import java.io.IOException;

final class HxzDefaults {
    static void install() {
        var directory=HxzPlatform.getInstance().getConfigDir();
        try {
            Files.createDirectories(directory);
            for(String name:new String[]{"vss-client-config.json","vss-server-config.json"}) {
                if(name.contains("client") && HxzPlatform.dedicatedServer()) continue;
                var path=directory.resolve(name);
                if(Files.exists(path)) continue;
                try(var input=HxzDefaults.class.getResourceAsStream("/hxz-defaults/"+name)) {
                    if(input==null) throw new IOException("Missing HXZ default: "+name);
                    try { Files.copy(input,path); } catch(FileAlreadyExistsException concurrent) { /* Keep user config. */ }
                }
            }
        } catch(IOException e) { throw new IllegalStateException("Unable to initialize HXZ configuration",e); }
    }
}
