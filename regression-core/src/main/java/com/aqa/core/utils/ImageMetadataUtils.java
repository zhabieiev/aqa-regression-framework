package com.aqa.core.utils;

import com.aqa.core.models.ImageMetadata;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static java.lang.String.format;

public class ImageMetadataUtils {

    private ImageMetadataUtils() {
    }

    public static ImageMetadata getMetadata(String imageUrl) {
        BufferedImage image = loadImage(imageUrl);
        ImageMetadata metadata = new ImageMetadata();
        metadata.setWidth(image.getWidth());
        metadata.setHeight(image.getHeight());
        return metadata;
    }

    private static BufferedImage loadImage(String imageUrl) {
        try {
            URI uri = new URI(imageUrl);
            return ImageIO.read(uri.toURL());
        } catch (IOException | URISyntaxException e) {
            throw new IllegalArgumentException(format("Failed to load image from URL: %s", imageUrl), e);
        }
    }
}
