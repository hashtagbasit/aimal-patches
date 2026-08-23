package app.aimal.extension.streaming;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Holds the app's ExoPlayer instance and changes its playback speed.
 *
 * The player is handed over by a one-instruction hook in the constructor of
 * every class implementing media3's ExoPlayer interface, so this works no
 * matter which of the app's own classes owns the player.
 *
 * Speed is applied by whichever of these succeeds first:
 *
 *  1. Reflection on media3's real method name. Both target apps keep
 *     `setPlaybackSpeed` even where they obfuscate the class around it.
 *  2. {@link #setSpeedNative}, whose body the patch can replace with a direct
 *     invoke of an obfuscated method, for a future build that renames it.
 */
public final class PlayerBridge {
    private static final String METHOD_NAME = "setPlaybackSpeed";

    /**
     * Weak so a finished player is collected normally. An Activity leak in a
     * patched streaming app shows up as an OOM on a long binge session.
     */
    private static WeakReference<Object> playerReference = new WeakReference<>(null);

    private static Method reflectiveSetSpeed;
    private static Class<?> resolvedFor;

    private PlayerBridge() {
    }

    /**
     * Called from the player's constructor. Must not throw: it runs while the
     * player is being built, and an exception here would take playback down
     * with it.
     */
    public static void onPlayerCreated(Object player) {
        try {
            playerReference = new WeakReference<>(player);
            Logger.d("Player captured: " + player.getClass().getName());

            float saved = Prefs.speed();
            if (saved != 1f) {
                // A new player always starts at 1x, so re-apply the user's
                // choice. Posted rather than called inline because the player
                // is not fully usable at this point in its own constructor.
                Controls.postToMainThread(new Runnable() {
                    @Override
                    public void run() {
                        setSpeed(Prefs.speed());
                    }
                });
            }
        } catch (Throwable t) {
            Logger.e("onPlayerCreated failed", t);
        }
    }

    static boolean hasPlayer() {
        return playerReference.get() != null;
    }

    /**
     * @return whether the speed was applied to a live player.
     */
    static boolean setSpeed(float speed) {
        Object player = playerReference.get();
        if (player == null) {
            Logger.d("No player to set speed on");
            return false;
        }

        Method method = resolve(player);
        if (method != null) {
            try {
                method.invoke(player, speed);
                return true;
            } catch (Throwable t) {
                Logger.e("Reflective " + METHOD_NAME + " failed", t);
            }
        }

        try {
            setSpeedNative(player, speed);
            return true;
        } catch (Throwable t) {
            Logger.e("Patched " + METHOD_NAME + " failed", t);
            return false;
        }
    }

    /**
     * Finds a usable {@code setPlaybackSpeed(float)}.
     *
     * The method has to be looked up on a type that is itself public. HBO Max's
     * player class is package-private final, so a Method obtained straight from
     * {@code player.getClass()} throws IllegalAccessException on invoke even
     * though the method itself is public. Binding to a public superclass or to
     * the media3 interface avoids that, and virtual dispatch still lands on the
     * real implementation.
     */
    private static Method resolve(Object player) {
        Class<?> type = player.getClass();
        if (resolvedFor == type) return reflectiveSetSpeed;

        resolvedFor = type;
        reflectiveSetSpeed = null;

        // Public class in the hierarchy - media3's BasePlayer, under whatever
        // name this build gave it.
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (!Modifier.isPublic(c.getModifiers())) continue;
            Method method = declaredSetSpeed(c);
            if (method != null) {
                reflectiveSetSpeed = method;
                Logger.d("Speed method bound to class " + c.getName());
                return method;
            }
        }

        // Otherwise the interface that declares it.
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> face : c.getInterfaces()) {
                if (!Modifier.isPublic(face.getModifiers())) continue;
                Method method = declaredSetSpeed(face);
                if (method != null) {
                    reflectiveSetSpeed = method;
                    Logger.d("Speed method bound to interface " + face.getName());
                    return method;
                }
            }
        }

        // Last resort: force access on the concrete class.
        Method method = declaredSetSpeed(type);
        if (method != null) {
            try {
                method.setAccessible(true);
                reflectiveSetSpeed = method;
                Logger.d("Speed method forced on " + type.getName());
                return method;
            } catch (Throwable ignored) {
                // Falls through to the patched hook.
            }
        }

        Logger.d("No reflective speed method on " + type.getName());
        return null;
    }

    private static Method declaredSetSpeed(Class<?> type) {
        try {
            return type.getDeclaredMethod(METHOD_NAME, float.class);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    /**
     * Body can be replaced by the patch with a check-cast and a direct invoke
     * of the app's own speed setter. Only reached if reflection failed.
     */
    public static void setSpeedNative(Object player, float speed) {
        throw new UnsupportedOperationException("Speed hook was not patched in");
    }
}
