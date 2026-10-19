package hxz.smoke;

import dev.xantha.vss.client.prediction.PredictionTileManager;
import java.lang.invoke.MethodHandles;

/** Defines the contract in its target module/package without creating a split-package probe jar. */
public final class PredictionPressureProbe {
    public static void verify() throws Exception {
        Class<?> contract;
        try(var input=PredictionPressureProbe.class.getResourceAsStream("/hxz-test/pressure.bin")) {
            if(input==null)throw new IllegalStateException("Missing native pressure contract resource");
            contract=MethodHandles.privateLookupIn(PredictionTileManager.class,MethodHandles.lookup())
                    .defineClass(input.readAllBytes());
        }
        try {contract.getMethod("verify").invoke(null);}
        catch(java.lang.reflect.InvocationTargetException failure) {
            if(failure.getCause() instanceof Exception cause)throw cause;
            if(failure.getCause() instanceof Error cause)throw cause;
            throw failure;
        }
    }
}
