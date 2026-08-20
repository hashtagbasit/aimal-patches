package app.aimal.extension.ary.downloads;

import android.app.Activity;
import android.content.Context;
import android.view.MenuItem;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.List;

/**
 * Bridges the new bottom-navigation entry to {@link DownloadsFragment}.
 *
 * MainPage dispatches tabs from a switch on menu item id and swaps fragments
 * into R.id.fragment_container. This mirrors that behaviour for the injected
 * "Downloads" item, resolving both ids by name at runtime so the patch never
 * has to hardcode a generated resource id.
 */
public final class DownloadsTab {

    /** id added to aryzap_bottom_navigation_menu.xml by the resource patch. */
    private static final String MENU_ITEM_NAME = "aryDownloadsTab";
    private static final String CONTAINER_NAME = "fragment_container";
    private static final String HOME_CONTENT_NAME = "home_content";

    private DownloadsTab() {
    }

    /**
     * Injected at the top of MainPage's navigation listener.
     *
     * @return true when this was the Downloads tab and it has been shown, which
     *         tells the caller to stop before the app's own switch runs.
     */
    public static boolean handle(Activity activity, MenuItem menuItem) {
        if (activity == null || menuItem == null) {
            return false;
        }
        try {
            int expected = idOf(activity, MENU_ITEM_NAME);
            if (expected == 0 || menuItem.getItemId() != expected) {
                return false;
            }
            show(activity);
            return true;
        } catch (Throwable t) {
            Logger.e("Downloads tab dispatch failed", t);
            return false;
        }
    }

    private static void show(Activity activity) {
        if (!(activity instanceof FragmentActivity)) {
            Logger.d("Host is not a FragmentActivity; cannot show Downloads");
            return;
        }
        FragmentActivity host = (FragmentActivity) activity;
        FragmentManager manager = host.getSupportFragmentManager();

        // Mirror showFragment(): hide whatever is currently visible first.
        List<Fragment> current = manager.getFragments();
        for (Fragment fragment : current) {
            if (fragment != null && fragment.isVisible()) {
                manager.beginTransaction().hide(fragment).commit();
            }
        }

        Fragment existing = manager.findFragmentByTag(DownloadsFragment.TAG);
        FragmentTransaction transaction = manager.beginTransaction();
        if (existing != null) {
            transaction.show(existing);
        } else {
            int containerId = idOf(host, CONTAINER_NAME);
            if (containerId == 0) {
                Logger.d("fragment_container not found; cannot show Downloads");
                return;
            }
            transaction.add(containerId, new DownloadsFragment(), DownloadsFragment.TAG);
        }
        transaction.commit();

        setVisibility(host, CONTAINER_NAME, View.VISIBLE);
        setVisibility(host, HOME_CONTENT_NAME, View.GONE);
    }

    private static void setVisibility(Activity activity, String name, int visibility) {
        int id = idOf(activity, name);
        if (id == 0) {
            return;
        }
        View view = activity.findViewById(id);
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    private static int idOf(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }
}
