package com.lvonasek.pilauncher.platforms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

public class VRPlatform extends AbstractPlatform {

    private static final String ICONS1_URL = "https://github.com/vKolerts/quest_icons/raw/master/450/";
    private static final String ICONS2_URL = "https://raw.githubusercontent.com/lvonasek/binary/master/QuestPiLauncher/icons/";

    @Override
    public ArrayList<ApplicationInfo> getInstalledApps(Context context) {
        ArrayList<ApplicationInfo> output = new ArrayList<>();
        if (!isSupported(context)) {
            return output;
        }

        PackageManager pm = context.getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (isVirtualRealityApp(app)) {
                output.add(app);
            }
        }
        return output;
    }

    @Override
    public boolean isSupported(Context context) {
        return isMagicLeapHeadset() || isOculusHeadset() || isPicoHeadset();
    }

    @Override
    public void loadIcon(Activity activity, ImageView icon, ApplicationInfo app, String name) {
        icon.setImageDrawable(app.loadIcon(activity.getPackageManager()));

        String pkg = app.packageName;
        if (iconCache.containsKey(pkg)) {
            icon.setImageDrawable(iconCache.get(pkg));
            return;
        }

        final File file = pkg2path(activity, pkg);
        if (file.exists()) {
            if (AbstractPlatform.updateIcon(icon, file, pkg)) {
                return;
            }
        }
        downloadIcon(activity, pkg, name, () -> AbstractPlatform.updateIcon(icon, file, pkg));
    }

    @Override
    public void runApp(Context context, ApplicationInfo app, boolean multiwindow) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
        context.getApplicationContext().startActivity(launchIntent);
    }

    private void downloadIcon(final Activity context, String pkg, String name, final Runnable callback) {
        final File file = pkg2path(context, pkg);
        new Thread(() -> {
            try {
                String autogen = null;
                if (ignoredIcons.contains(file.getName())) {
                    //ignored icon
                } else if (downloadFile(ICONS1_URL + pkg + ".jpg", file)) {
                    context.runOnUiThread(callback);
                } else if (downloadFile(ICONS2_URL + pkg.toLowerCase(Locale.US) + ".jpg", file)) {
                    context.runOnUiThread(callback);
                } else {
                    int count = 0;
                    File info = new File(context.getApplicationInfo().dataDir, "applab.info");
                    if (downloadFile(ICONS2_URL + "applab.info", info)) {
                        try {
                            FileInputStream fis = new FileInputStream(info);
                            Scanner sc = new Scanner(fis);
                            while (sc.hasNext()) {
                                String line = sc.nextLine();
                                if (line.contains(name)) {
                                    Scanner lsc = new Scanner(line);
                                    autogen = lsc.next();
                                    lsc.close();
                                    count++;
                                }
                            }
                            sc.close();
                            fis.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (count == 0) {
                        Log.d("Missing icon", file.getName());
                        ignoredIcons.add(file.getName());
                    } else if (count == 1) {
                        if (downloadFile(ICONS2_URL + autogen + ".jpg", file)) {
                            context.runOnUiThread(callback);
                        } else {
                            Log.d("Missing icon", file.getName());
                            ignoredIcons.add(file.getName());
                        }
                    } else if (count >= 2) {
                        Log.d("Too many icons", file.getName());
                        ignoredIcons.add(file.getName());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
