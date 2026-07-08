package com.workspace.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.workspace.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@RestController
@RequestMapping("/upload")
public class UploadController {

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        String newFilename = IdUtil.simpleUUID() + ext;
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File destFile = new File(dir, newFilename);

        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
                compressImage(image, destFile, ext);
            } else {
                file.transferTo(destFile);
            }
        } catch (Exception e) {
            file.transferTo(destFile);
        }

        Map<String, String> result = new HashMap<>();
        result.put("fileName", newFilename);
        result.put("url", "/api" + urlPrefix + "/" + newFilename);
        result.put("size", String.valueOf(destFile.length()));
        return Result.success(result);
    }

    private void compressImage(BufferedImage image, File destFile, String ext) throws IOException {
        String formatName = ext.replace(".", "").toLowerCase();
        if ("png".equals(formatName)) {
            formatName = "png";
        } else {
            formatName = "jpg";
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext()) {
            ImageIO.write(image, formatName, destFile);
            return;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if ("jpg".equals(formatName)) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f);
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(new FileOutputStream(destFile))) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
