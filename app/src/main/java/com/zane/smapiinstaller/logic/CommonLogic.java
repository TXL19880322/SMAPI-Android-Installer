package com.zane.smapiinstaller.logic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.zane.smapiinstaller.entity.ApkFilesManifest;
import com.zane.smapiinstaller.entity.ManifestEntry;
import com.zane.smapiinstaller.utils.FileUtils;

import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

/**
 * 通用逻辑
 */
public class CommonLogic {
    /**
     * 从View获取所属Activity
     * @param view context容器
     * @return Activity
     */
    public static Activity getActivityFromView(View view) {
        if (null != view) {
            Context context = view.getContext();
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) {
                    return (Activity) context;
                }
                context = ((ContextWrapper) context).getBaseContext();
            }
        }
        return null;
    }


    /**
     * 打开指定URL
     * @param context context
     * @param url     目标URL
     */
    public static void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent();
            intent.setData(Uri.parse(url));
            intent.setAction(Intent.ACTION_VIEW);
            context.startActivity(intent);
        }
        catch (ActivityNotFoundException ignored){
        }
    }

    /**
     * 复制文本到剪贴板
     * @param context 上下文
     * @param copyStr 文本
     * @return 是否复制成功
     */
    public static boolean copyToClipboard(Context context, String copyStr) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("Label", copyStr);
            cm.setPrimaryClip(mClipData);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 扫描全部兼容包
     * @param context context
     * @return 兼容包列表
     */
    public static List<ApkFilesManifest> findAllApkFileManifest(Context context) {
        ApkFilesManifest apkFilesManifest = com.zane.smapiinstaller.utils.FileUtils.getAssetJson(context, "apk_files_manifest.json", ApkFilesManifest.class);
        ArrayList<ApkFilesManifest> apkFilesManifests = Lists.newArrayList(apkFilesManifest);
        File compatFolder = new File(context.getFilesDir(), "compat");
        if (compatFolder.exists()) {
            for (File directory : compatFolder.listFiles(File::isDirectory)) {
                File manifestFile = new File(directory, "apk_files_manifest.json");
                if (manifestFile.exists()) {
                    ApkFilesManifest manifest = FileUtils.getFileJson(manifestFile, ApkFilesManifest.class);
                    if (manifest != null) {
                        apkFilesManifests.add(manifest);
                    }
                }
            }
        }
        Collections.sort(apkFilesManifests, (a, b) -> Long.compare(b.getMinBuildCode(), a.getMinBuildCode()));
        return apkFilesManifests;
    }

    /**
     * 提取SMAPI环境文件到内部存储对应位置
     * @param context  context
     * @param apkPath  安装包路径
     * @param checkMode 是否为校验模式
     * @return 操作是否成功
     */
    public static boolean unpackSmapiFiles(Context context, String apkPath, boolean checkMode) {
        List<ManifestEntry> manifestEntries = FileUtils.getAssetJson(context, "smapi_files_manifest.json", new TypeReference<List<ManifestEntry>>() { });
        if (manifestEntries == null)
            return false;
        File basePath = new File(Environment.getExternalStorageDirectory() + "/StardewValley/");
        if (!basePath.exists()) {
            if (!basePath.mkdir()) {
                return false;
            }
        }
        File noMedia = new File(basePath, ".nomedia");
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile();
            } catch (IOException ignored) {
            }
        }
        for (ManifestEntry entry : manifestEntries) {
            File targetFile = new File(basePath, entry.getTargetPath());
            switch (entry.getOrigin()) {
                case 0:
                    if (!checkMode || !targetFile.exists()) {
                        try (InputStream inputStream = context.getAssets().open(entry.getAssetPath())) {
                            if (!targetFile.getParentFile().exists()) {
                                if (!targetFile.getParentFile().mkdirs()) {
                                    return false;
                                }
                            }
                            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                                ByteStreams.copy(inputStream, outputStream);
                            }
                        } catch (IOException e) {
                            Log.e("COMMON", "Copy Error", e);
                        }
                    }
                    break;
                case 1:
                    if (!checkMode || !targetFile.exists()) {
                        ZipUtil.unpackEntry(new File(apkPath), entry.getAssetPath(), targetFile);
                    }
                    break;
            }
        }
        return true;
    }

    /**
     * 修改AndroidManifest.xml文件
     * @param bytes        AndroidManifest.xml文件字符数组
     * @param processLogic 处理逻辑
     * @return 修改后的AndroidManifest.xml文件字符数组
     * @throws IOException 异常
     */
    public static byte[] modifyManifest(byte[] bytes, Predicate<ManifestTagVisitor.AttrArgs> processLogic) throws IOException {
        AxmlReader reader = new AxmlReader(bytes);
        AxmlWriter writer = new AxmlWriter();
        reader.accept(new AxmlVisitor(writer) {
            @Override
            public NodeVisitor child(String ns, String name) {
                NodeVisitor child = super.child(ns, name);
                return new ManifestTagVisitor(child, processLogic);
            }
        });
        return writer.toByteArray();
    }

}
