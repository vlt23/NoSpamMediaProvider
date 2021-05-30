/*
 * This file is part of NoSpamMediaProvider.
 *
 * NoSpamMediaProvider is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NoSpamMediaProvider is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NoSpamMediaProvider.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 vlt23
 */

package com.vlt23.nospammediaprovider;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class NoSpamMediaProvider implements IXposedHookLoadPackage {

    private static final String TAG = "NoSpamMediaProvider";
    private static final String packageName = "com.android.providers.media.module";
    private static final String className = "com.android.providers.media.util.FileUtils";
    private static final String CAMERA_RELATIVE_PATH = String.format("%s/%s/", Environment.DIRECTORY_DCIM, "Camera");
    private final boolean DEBUG = BuildConfig.DEBUG;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(packageName)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "We are in MediaProvider!");
        }

        // https://cs.android.com/android/platform/superproject/+/master:packages/providers/MediaProvider/src/com/android/providers/media/util/FileUtils.java;drc=d2efafead208107e73770f2f6a12c74b1a6ca410;l=1268
        Class<?> classFileUtils = findClass(className, lpparam.classLoader);
        findAndHookMethod(className, lpparam.classLoader, "isDirectoryHidden", "java.io.File",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        File dir = (File) param.args[0];

                        final String name = dir.getName();
                        if (name.startsWith(".")) {
                            return true;
                        }

                        final File nomedia = new File(dir, ".nomedia");

                        // check for .nomedia presence
                        if (!nomedia.exists()) {
                            return false;
                        }

                        // Handle top-level default directories. These directories should always be visible,
                        // regardless of .nomedia presence.
                        String extractRelativePath = (String) XposedHelpers.callStaticMethod(classFileUtils,
                                "extractRelativePath", dir.getAbsolutePath());
                        final String[] relativePath = (String[]) XposedHelpers.callStaticMethod(classFileUtils,
                                "sanitizePath", extractRelativePath);
                        final boolean isTopLevelDir = relativePath.length == 1 && TextUtils.isEmpty(relativePath[0]);
                        boolean isDefaultDirectoryName = (boolean) XposedHelpers.callStaticMethod(classFileUtils,
                                "isDefaultDirectoryName", name);
                        if (isTopLevelDir && isDefaultDirectoryName) {
                            nomedia.delete();
                            return false;
                        }

                        // DCIM/Camera should always be visible regardless of .nomedia presence.
                        String extractRelativePathForDirectory = (String) XposedHelpers.callStaticMethod(classFileUtils,
                                "extractRelativePathForDirectory", dir.getAbsolutePath());
                        if (CAMERA_RELATIVE_PATH.equalsIgnoreCase(extractRelativePathForDirectory)) {
                            nomedia.delete();
                            return false;
                        }

                        // .nomedia is present which makes this directory as hidden directory
//                        Logging.logPersistent("Observed non-standard " + nomedia);
                        return true;
                    }
                });
    }

}
