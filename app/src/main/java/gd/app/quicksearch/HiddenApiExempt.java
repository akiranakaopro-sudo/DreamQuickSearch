package gd.app.quicksearch;

import android.util.Log;
import java.lang.reflect.Method;

/** Mirrors Dialer ManagedCursorGuard.exemptHiddenApis for wallpaper/blur reflection. */
final class HiddenApiExempt {
    private static final String TAG = "HiddenApiExempt";
    private static volatile boolean sExempted;

    private HiddenApiExempt() {}

    static void apply() {
        if (sExempted) {
            return;
        }
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod(
                    "getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeClass =
                    (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(
                    vmRuntimeClass, "getRuntime", new Class[0]);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(
                    vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Object vmRuntime = getRuntime.invoke(null);
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{"L"}});
            sExempted = true;
        } catch (Throwable t) {
            Log.w(TAG, "hidden-api exemption failed", t);
        }
    }
}
