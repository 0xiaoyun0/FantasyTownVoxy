package me.cortex.voxy.hxz.core;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Length-delimited hashes avoid ambiguous concatenations and keep filenames bounded. */
public final class TerrainFingerprint {
    private TerrainFingerprint(){}
    public static String digest(String metadata,byte[] generator){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] bytes=metadata.getBytes(StandardCharsets.UTF_8);
            md.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());md.update(bytes);
            md.update(generator);return HexFormat.of().formatHex(md.digest());
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
