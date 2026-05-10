package app.aimal.extension.crunchyroll;

import java.util.Arrays;
import java.util.List;

public final class SpeedHelper {

    private static final List<Float> SPEEDS = Arrays.asList(
        2.0f, 1.75f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f
    );

    public static Object getSpeedLiveData() {
        try {
            Class<?> lClass = Class.forName("androidx.lifecycle.L");
            Object liveData = lClass.getDeclaredConstructor().newInstance();

            // MutableLiveData.setValue(Object)
            // In obfuscated CR, setValue is inherited from LiveData (H)
            // Use reflection to find and call it
            Class<?> hClass = Class.forName("androidx.lifecycle.H");
            java.lang.reflect.Method setValue = null;

            // Try common obfuscated method names for setValue
            for (String name : new String[]{"setValue", "g", "h", "i", "j", "k"}) {
                try {
                    setValue = hClass.getDeclaredMethod(name, Object.class);
                    setValue.setAccessible(true);
                    break;
                } catch (NoSuchMethodException e) {
                    // try next
                }
            }

            if (setValue == null) {
                // Fallback: find method that takes Object param
                for (java.lang.reflect.Method m : hClass.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && params[0] == Object.class) {
                        m.setAccessible(true);
                        setValue = m;
                        break;
                    }
                }
            }

            if (setValue != null) {
                setValue.invoke(liveData, SPEEDS);
            }

            return liveData;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isSpeedControlEnabled() {
        return true;
    }
}
