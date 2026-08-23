package app.aimal.extension.streaming;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Reshapes the video.
 *
 * Two mechanisms, preferred in this order:
 *
 *  1. media3's own {@code AspectRatioFrameLayout.setResizeMode(int)}, reached
 *     by reflection on the first ancestor of the video surface that declares
 *     it. Both HBO Max and Disney+ keep that method name, and letting media3
 *     do the resize means the app's own measurement stays consistent - this is
 *     the same approach the Crunchyroll patch in this bundle uses.
 *
 *  2. A view transform, for the fixed zoom steps and as a fallback when no
 *     AspectRatioFrameLayout is in the tree. media3 letterboxes *outside* the
 *     video surface, so the transform goes on the outermost letterboxed
 *     ancestor - scaling the surface alone would just be clipped by its
 *     container - and clipping is lifted above it.
 *
 * Neither touches the frames, so DRM-protected output is unaffected.
 */
final class AspectRatio {
    static final int FIT = 0;
    static final int STRETCH = 1;
    static final int CROP = 2;
    static final int ZOOM_115 = 3;
    static final int ZOOM_130 = 4;

    private static final int MODE_COUNT = 5;

    /** androidx.media3.ui.AspectRatioFrameLayout resize modes. */
    private static final int RESIZE_FIT = 0;
    private static final int RESIZE_FILL = 3;
    private static final int RESIZE_ZOOM = 4;

    /** Pixels of slack before a view counts as letterboxed against its root. */
    private static final int LETTERBOX_TOLERANCE = 2;

    /** So a previous target can be reset when the player rebuilds its views. */
    private static WeakReference<View> scaledReference = new WeakReference<>(null);

    private AspectRatio() {
    }

    static int next(int mode) {
        return (mode + 1) % MODE_COUNT;
    }

    static String label(int mode) {
        switch (mode) {
            case STRETCH:
                return "STRETCH";
            case CROP:
                return "CROP";
            case ZOOM_115:
                return "ZOOM 1.15";
            case ZOOM_130:
                return "ZOOM 1.30";
            case FIT:
            default:
                return "FIT";
        }
    }

    /**
     * Idempotent - it is re-run on every layout pass of the surface, because
     * the apps reset their own resize mode on rotation, on entering fullscreen
     * and on track changes.
     */
    static void apply(View video, int mode) {
        if (video == null) return;

        try {
            View resizer = findResizeHost(video);

            if (resizer != null) {
                setResizeMode(resizer, resizeModeFor(mode));
                // The zoom steps sit on top of whatever media3 just did.
                float zoom = zoomFactor(mode);
                transform(video, zoom, zoom);
            } else {
                emulate(video, mode);
            }
        } catch (Throwable t) {
            Logger.e("Failed to apply aspect mode", t);
        }
    }

    // media3 path ------------------------------------------------------------

    private static int resizeModeFor(int mode) {
        switch (mode) {
            case STRETCH:
                return RESIZE_FILL;
            case CROP:
                return RESIZE_ZOOM;
            default:
                return RESIZE_FIT;
        }
    }

    private static float zoomFactor(int mode) {
        switch (mode) {
            case ZOOM_115:
                return 1.15f;
            case ZOOM_130:
                return 1.30f;
            default:
                return 1f;
        }
    }

    /**
     * The nearest ancestor declaring {@code setResizeMode(int)} - in practice
     * media3's AspectRatioFrameLayout, or a PlayerView wrapping one.
     */
    private static View findResizeHost(View video) {
        View walk = video;
        while (walk != null) {
            if (resizeMethod(walk.getClass()) != null) return walk;
            walk = walk.getParent() instanceof View ? (View) walk.getParent() : null;
        }
        return null;
    }

    private static Method resizeMethod(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod("setResizeMode", int.class);
            } catch (NoSuchMethodException absent) {
                // Keep walking up.
            }
        }
        return null;
    }

    private static void setResizeMode(View host, int resizeMode) {
        Method method = resizeMethod(host.getClass());
        if (method == null) return;
        try {
            method.setAccessible(true);
            method.invoke(host, resizeMode);
            Logger.d("setResizeMode(" + resizeMode + ") on " + host.getClass().getName());
        } catch (Throwable t) {
            Logger.e("setResizeMode failed", t);
        }
    }

    // Transform path ---------------------------------------------------------

    /**
     * Used when the app has no AspectRatioFrameLayout to drive: works out the
     * letterboxing from the view geometry and removes it by scaling.
     */
    private static void emulate(View video, int mode) {
        View root = rootOf(video);
        if (root == null) return;

        View target = letterboxedAncestor(video, root);

        float scaleX = zoomFactor(mode);
        float scaleY = scaleX;

        if (mode == STRETCH || mode == CROP) {
            float targetWidth = target.getWidth();
            float targetHeight = target.getHeight();
            float rootWidth = root.getWidth();
            float rootHeight = root.getHeight();

            if (targetWidth > 0 && targetHeight > 0 && rootWidth > 0 && rootHeight > 0) {
                float ratioX = rootWidth / targetWidth;
                float ratioY = rootHeight / targetHeight;

                if (mode == STRETCH) {
                    // Fill both axes exactly, distorting the picture.
                    scaleX = ratioX;
                    scaleY = ratioY;
                } else {
                    // Fill the screen keeping the picture's shape, cropping
                    // the overflow.
                    scaleX = scaleY = Math.max(ratioX, ratioY);
                }
            }
        }

        transform(target, scaleX, scaleY);
    }

    private static void transform(View target, float scaleX, float scaleY) {
        // A target that is no longer the one being scaled would otherwise keep
        // its old transform forever.
        View previous = scaledReference.get();
        if (previous != null && previous != target) {
            previous.setScaleX(1f);
            previous.setScaleY(1f);
        }
        scaledReference = new WeakReference<>(target);

        boolean transformed = scaleX != 1f || scaleY != 1f;
        setClippingAbove(target, rootOf(target), !transformed);

        // Scale about the centre so any crop is even on both sides.
        target.setPivotX(target.getWidth() / 2f);
        target.setPivotY(target.getHeight() / 2f);
        target.setScaleX(scaleX);
        target.setScaleY(scaleY);
    }

    /**
     * The outermost ancestor still letterboxed against the screen - the one
     * carrying the black bars. Falls back to the video view itself when the
     * player already fills the screen.
     */
    private static View letterboxedAncestor(View video, View root) {
        View target = video;
        View walk = video;

        while (walk != root && walk.getParent() instanceof ViewGroup) {
            View parent = (View) walk.getParent();
            if (parent == root) break;
            if (isLetterboxed(parent, root)) target = parent;
            walk = parent;
        }

        return target;
    }

    private static boolean isLetterboxed(View view, View root) {
        return view.getWidth() > 0
                && view.getHeight() > 0
                && (view.getWidth() < root.getWidth() - LETTERBOX_TOLERANCE
                || view.getHeight() < root.getHeight() - LETTERBOX_TOLERANCE);
    }

    /**
     * A scaled view is clipped by every ancestor that clips its children, so
     * clipping is lifted between it and the screen - and put back when the
     * transform is removed.
     */
    private static void setClippingAbove(View target, View root, boolean clip) {
        View walk = target;
        while (walk != root && walk.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) walk.getParent();
            parent.setClipChildren(clip);
            parent.setClipToPadding(clip);
            if (parent == root) break;
            walk = parent;
        }
    }

    private static View rootOf(View view) {
        if (view == null) return null;
        View rootView = view.getRootView();
        if (rootView == null) return null;
        View content = rootView.findViewById(android.R.id.content);
        return content != null ? content : rootView;
    }
}
