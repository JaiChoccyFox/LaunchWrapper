package org.mcphackers.launchwrapper.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * use this for whatever you want
 *
 * @author Moresteck
 */
public class ImageUtils {
	public final BufferedImage bufImg;

	public ImageUtils(BufferedImage img) {
		this.bufImg = img;
	}

	public ImageUtils(InputStream stream) throws IOException {
		this.bufImg = ImageIO.read(stream);
	}

	public ImageUtils crop(int x, int y, int width, int height) {
		int[][] pixels = this.getPixelColors();
		int bufWidth = this.bufImg.getWidth();
		int bufHeight = this.bufImg.getHeight();

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < bufWidth; i++) {
			for (int j = 0; j < bufHeight; j++) {
				if (j >= y && j < y + height && i >= x && i < x + width) {
					img.setRGB(i - x, j - y, pixels[i][j]);
				}
			}
		}
		return new ImageUtils(img);
	}

	public ImageUtils setArea(int x, int y, BufferedImage img) {
		return this.setArea(x, y, img, true);
	}

	public static int mergeColor(int source, int target) {
		double alpha = ((byte)(target >> 24) & 0xFF) / 255D;
		int r = (byte)(source >> 16) & 0xFF;
		int g = (byte)(source >> 8) & 0xFF;
		int b = (byte)(source) & 0xFF;
		int rx = (byte)(target >> 16) & 0xFF;
		int gx = (byte)(target >> 8) & 0xFF;
		int bx = (byte)(target) & 0xFF;
		int out_r = (int)(r + (rx - r) * alpha);
		int out_g = (int)(g + (gx - g) * alpha);
		int out_b = (int)(b + (bx - b) * alpha);
		return 0xFF << 24 | out_r << 16 | out_g << 8 | out_b << 0;
	}

	public ImageUtils clearArea(int x, int y, int w, int h) {
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < h; j++) {
				this.bufImg.setRGB(x + i, y + j, 0);
			}
		}
		return this;
	}

	public ImageUtils setArea(int x, int y, BufferedImage img, boolean forceTransparent) {
		int width = this.bufImg.getWidth();
		int height = this.bufImg.getHeight();
		int[][] pixels = new int[width][height];

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				int pixel = this.bufImg.getRGB(i, j);
				if (j >= y && j < y + img.getHeight() && i >= x && i < x + img.getWidth()) {
					int toset = img.getRGB(i - x, j - y);
					if (!forceTransparent) {
						if ((toset >> 24) != 0x00) {
							pixel = mergeColor(pixel, toset);
						}
					} else {
						pixel = toset;
					}
				}
				pixels[i][j] = pixel;
			}
		}

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {

				this.bufImg.setRGB(i, j, pixels[i][j]);
			}
		}
		return this;
	}

	public ImageUtils flip(boolean flipX, boolean flipY) {
		int[][] pixels = this.getPixelColors();
		int width = this.bufImg.getWidth();
		int height = this.bufImg.getHeight();

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				this.bufImg.setRGB(flipX ? width - 1 - i : i, flipY ? height - 1 - j : j, pixels[i][j]);
			}
		}
		return this;
	}

	public int[][] getPixelColors() {
		int width = this.bufImg.getWidth();
		int height = this.bufImg.getHeight();
		int[][] pixels = new int[width][height];

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				pixels[i][j] = this.bufImg.getRGB(i, j);
			}
		}
		return pixels;
	}

	public byte[] getInByteForm() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(this.bufImg, "PNG", baos);
		return baos.toByteArray();
	}

	public BufferedImage getImage() {
		return this.bufImg;
	}
}
