package com.nchu.imagecompress.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EXIF 元数据读取工具 — 基于 metadata-extractor 库。
 *
 * <p>所有方法均异常安全：文件不存在、格式不支持、EXIF 缺失时返回空数据，
 * 绝不抛出异常。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-17
 */
public final class ImageExifUtil {

    private ImageExifUtil() {}

    /**
     * 从图片文件读取 EXIF 元数据。
     *
     * @param file 图片文件
     * @return ExifData 对象（EXIF 缺失时所有字段为 null）
     */
    public static ExifData readExif(File file) {
        ExifData data = new ExifData();
        if (file == null || !file.exists() || !file.isFile()) {
            return data;
        }

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);

            // --- IFD0（相机信息） ---
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                data.make = ifd0.getString(ExifIFD0Directory.TAG_MAKE);
                data.model = ifd0.getString(ExifIFD0Directory.TAG_MODEL);
                data.software = ifd0.getString(ExifIFD0Directory.TAG_SOFTWARE);
                data.copyright = ifd0.getString(ExifIFD0Directory.TAG_COPYRIGHT);

                // 图像尺寸（来自 EXIF IFD0）
                try {
                    Integer w = ifd0.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH);
                    Integer h = ifd0.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT);
                    if (w != null && w > 0) data.imageWidth = w;
                    if (h != null && h > 0) data.imageHeight = h;
                } catch (Exception ignored) {}
            }

            // --- SubIFD（拍摄参数） ---
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                // 拍摄日期
                Date dateOriginal = subIfd.getDateOriginal();
                if (dateOriginal != null) {
                    data.dateTaken = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(dateOriginal);
                }

                // 曝光时间 → "1/250 sec"
                try {
                    data.exposureTime = subIfd.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                } catch (Exception ignored) {}

                // 光圈 → "f/4.0"
                try {
                    data.fNumber = subIfd.getString(ExifSubIFDDirectory.TAG_FNUMBER);
                } catch (Exception ignored) {}

                // ISO
                try {
                    Integer iso = subIfd.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
                    if (iso != null && iso > 0) data.iso = String.valueOf(iso);
                } catch (Exception ignored) {}

                // 焦距 → "50mm"
                try {
                    data.focalLength = subIfd.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                } catch (Exception ignored) {}

                // 闪光灯 → 位掩码翻译
                try {
                    Integer flash = subIfd.getInteger(ExifSubIFDDirectory.TAG_FLASH);
                    if (flash != null) {
                        data.flash = translateFlash(flash);
                    }
                } catch (Exception ignored) {}

                // EXIF 内的图像尺寸（如果 IFD0 没有）
                if (data.imageWidth <= 0) {
                    try {
                        Integer w = subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                        if (w != null && w > 0) data.imageWidth = w;
                    } catch (Exception ignored) {}
                }
                if (data.imageHeight <= 0) {
                    try {
                        Integer h = subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
                        if (h != null && h > 0) data.imageHeight = h;
                    } catch (Exception ignored) {}
                }
            }

            // --- GPS ---
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null) {
                try {
                    String lat = gps.getString(GpsDirectory.TAG_LATITUDE);
                    String lon = gps.getString(GpsDirectory.TAG_LONGITUDE);
                    if (lat != null && lon != null) {
                        data.gpsLatitude = lat;
                        data.gpsLongitude = lon;
                    }
                } catch (Exception ignored) {}

                try {
                    String alt = gps.getString(GpsDirectory.TAG_ALTITUDE);
                    if (alt != null) data.gpsAltitude = alt;
                } catch (Exception ignored) {}
            }

        } catch (ImageProcessingException | IOException e) {
            // 文件不是图片或格式不支持 — 返回空数据
            LogUtil.info("[ImageExifUtil] 无法读取 EXIF: " + file.getName() + " — " + e.getMessage());
        } catch (Exception e) {
            LogUtil.warning("[ImageExifUtil] 读取 EXIF 异常: " + file.getName() + " — " + e.getMessage());
        }

        return data;
    }

    /**
     * 将闪光灯位掩码翻译为中文描述。
     */
    private static String translateFlash(int flash) {
        // bit 0: 0=未触发, 1=触发
        boolean fired = (flash & 0x1) != 0;
        // bit 1-2: 返回检测
        int ret = (flash >> 1) & 0x3;
        // bit 3: 闪光灯模式
        // bit 4: 闪光灯就绪
        // bit 5: 防红眼

        if (!fired) return "未触发";

        StringBuilder sb = new StringBuilder("已触发");
        if (ret == 2) sb.append("（未检测到返回）");
        else if (ret == 3) sb.append("（返回正常）");
        if ((flash & 0x20) != 0) sb.append("，防红眼");

        return sb.toString();
    }

    /**
     * EXIF 数据容器 — 所有字段可为 null。
     */
    public static class ExifData {

        /** 相机品牌 */
        public String make;
        /** 相机型号 */
        public String model;
        /** 拍摄日期（已格式化） */
        public String dateTaken;
        /** 曝光时间，如 "1/250 sec" */
        public String exposureTime;
        /** 光圈值，如 "f/4.0" */
        public String fNumber;
        /** ISO 感光度 */
        public String iso;
        /** 焦距，如 "50mm" */
        public String focalLength;
        /** 闪光灯状态 */
        public String flash;
        /** GPS 纬度 */
        public String gpsLatitude;
        /** GPS 经度 */
        public String gpsLongitude;
        /** GPS 海拔 */
        public String gpsAltitude;
        /** 处理软件 */
        public String software;
        /** 版权信息 */
        public String copyright;
        /** EXIF 中的图像宽度 */
        public int imageWidth;
        /** EXIF 中的图像高度 */
        public int imageHeight;

        /** 至少有一个非空字段 */
        public boolean hasAnyData() {
            return make != null || model != null || dateTaken != null
                    || exposureTime != null || fNumber != null || iso != null
                    || focalLength != null || flash != null
                    || gpsLatitude != null || software != null || copyright != null;
        }

        /**
         * 将非空字段按展示顺序返回为 key-value 映射。
         * 调用方可直接遍历渲染 UI。
         */
        public Map<String, String> toDisplayMap() {
            Map<String, String> map = new LinkedHashMap<String, String>();

            putIfNotNull(map, "相机品牌", make);
            putIfNotNull(map, "相机型号", model);
            putIfNotNull(map, "拍摄时间", dateTaken);
            putIfNotNull(map, "曝光时间", formatExposure(exposureTime));
            putIfNotNull(map, "光圈值", fNumber != null ? "f/" + fNumber : null);
            putIfNotNull(map, "ISO", iso);
            putIfNotNull(map, "焦距", focalLength);
            putIfNotNull(map, "闪光灯", flash);
            if (imageWidth > 0 && imageHeight > 0) {
                map.put("图像尺寸", imageWidth + " × " + imageHeight + " px");
            }
            if (gpsLatitude != null && gpsLongitude != null) {
                map.put("GPS 位置", gpsLatitude + ", " + gpsLongitude);
            }
            putIfNotNull(map, "处理软件", software);
            putIfNotNull(map, "版权信息", copyright);

            return map;
        }

        private static void putIfNotNull(Map<String, String> map, String key, String value) {
            if (value != null && !value.isEmpty()) {
                map.put(key, value);
            }
        }

        /**
         * 格式化曝光时间：metadata-extractor 返回 "1/250 sec" 格式，直接返回即可。
         */
        private static String formatExposure(String raw) {
            if (raw == null) return null;
            // metadata-extractor 已经返回了可读格式，如 "1/250 sec"，直接使用
            return raw;
        }
    }
}
