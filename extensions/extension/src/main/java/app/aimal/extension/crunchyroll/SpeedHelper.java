package app.aimal.extension.crunchyroll;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public final class SpeedHelper {

    private static final List<Float> SPEEDS = Arrays.asList(
        2.0f, 1.75f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f
    );

    public static void replaceSpeedList(Object viewModel) {
        try {
            // Find the MutableLiveData<List<Float>> field
            // and replace its value with our extended speed list
            for (Field field : viewModel.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(viewModel);
                if (value == null) continue;

                // Check if this field holds a LiveData containing a List of Floats
                // by checking if its current value is a List with 3 Float elements
                // (the original [1.0, 0.75, 0.5])
                try {
                    // LiveData.getValue() - try common obfuscated names
                    Object listValue = null;
                    for (String name : new String[]{"getValue", "a", "b", "c", "d", "e", "f", "g"}) {
                        try {
                            java.lang.reflect.Method getter = value.getClass().getMethod(name);
                            Object result = getter.invoke(value);
                            if (result instanceof List) {
                                listValue = result;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }

                    if (listValue == null) {
                        // Try getting the internal field directly (LiveData stores in field 'a')
                        for (Field f : value.getClass().getSuperclass().getDeclaredFields()) {
                            f.setAccessible(true);
                            Object inner = f.get(value);
                            if (inner instanceof List) {
                                listValue = inner;
                                break;
                            }
                        }
                    }

                    if (listValue instanceof List) {
                        List<?> list = (List<?>) listValue;
                        if (list.size() == 3
                                && list.get(0) instanceof Float
                                && ((Float) list.get(0)) == 1.0f
                                && ((Float) list.get(1)) == 0.75f
                                && ((Float) list.get(2)) == 0.5f) {

                            // Found it! Replace with our speeds
                            // Set the internal value field directly
                            for (Field f : value.getClass().getSuperclass().getDeclaredFields()) {
                                f.setAccessible(true);
                                Object inner = f.get(value);
                                if (inner instanceof List) {
                                    f.set(value, SPEEDS);
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    public static boolean isSpeedControlEnabled() {
        return true;
    }
}
