package com.ektrepha.caremanager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.ektrepha.exception.CmValidationException;

/**
 * Resizes an uploaded Care Manager photo to a fixed square (center-cropped, then scaled) using
 * the JDK's own {@code java.awt.image}/{@code javax.imageio} — no new dependency. No image
 * resizing exists anywhere else in this codebase to reuse.
 */
public final class ImageResizer {

	private ImageResizer() {
	}

	public static byte[] toSquareJpeg(byte[] original, int size) {
		BufferedImage source;
		try {
			source = ImageIO.read(new ByteArrayInputStream(original));
		} catch (IOException ex) {
			throw new CmValidationException("Could not read image file");
		}
		if (source == null) {
			throw new CmValidationException("Could not read image file");
		}

		int cropSize = Math.min(source.getWidth(), source.getHeight());
		int x = (source.getWidth() - cropSize) / 2;
		int y = (source.getHeight() - cropSize) / 2;
		BufferedImage cropped = source.getSubimage(x, y, cropSize, cropSize);

		BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = resized.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, size, size);
			g.drawImage(cropped, 0, 0, size, size, null);
		} finally {
			g.dispose();
		}

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(resized, "jpg", out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to encode resized image", ex);
		}
	}

}
