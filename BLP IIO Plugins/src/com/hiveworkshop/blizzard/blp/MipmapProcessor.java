package com.hiveworkshop.blizzard.blp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Consumer;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.hiveworkshop.lang.LocalizedFormatedString;

/**
 * A class that is responsible for processing between mipmap data and
 * BufferedImage.
 * <p>
 * Implementations of this class are responsible for the types of images that
 * can be processed. A single instance is responsible for processing all mipmaps
 * of the same BLP file.
 * 
 * @author Imperial Good
 */
abstract class MipmapProcessor {

	/**
	 * Encodes an image into mipmap data.
	 * <p>
	 * The input image should be of a type contained in ImageTypeSpecifier.
	 * Other image types might have a best effort attempt to encode but there is
	 * no guarantee of meaningful success.
	 * <p>
	 * It is assumed the input image is the correct size. No clipping or
	 * subsampling is performed.
	 * 
	 * @param img
	 *            input image to encode.
	 * @param param
	 *            image write parameter to control encode behaviour.
	 * @param handler
	 *            warning handler.
	 * @return encoded mipmap data.
	 * @throws IIOException
	 *             in an image cannot be encoded.
	 */
	public abstract byte[] encodeMipmap(BufferedImage img,
			ImageWriteParam param, Consumer<LocalizedFormatedString> handler)
			throws IIOException;

	/**
	 * Decodes mipmap data into an image.
	 * <p>
	 * The image produced has very strict requirements. It must be exactly the
	 * dimensions of width and height. It must also be in the format of one of
	 * the ImageTypeSpecifier advertised by the class.
	 * <p>
	 * There is no guarantee that mmData contains exactly the data needed to
	 * fully produce an image. An attempt should be made to produce an image
	 * from as much of the data as possible. Missing pixel data must be assigned
	 * band values of 0.
	 * <p>
	 * No clipping or subsampling is performed.
	 * 
	 * @param mmData
	 *            the mipmap data to decode.
	 * @param param
	 *            image read parameter to control decode behavior.
	 * @param width
	 *            the width of the decoded image in pixels.
	 * @param height
	 *            the height of the decoded image in pixels.
	 * @param handler
	 *            warning handler.
	 * @return the decoded mipmap image.
	 * @throws IIOException
	 *             if an image cannot be produced.
	 */
	public abstract BufferedImage decodeMipmap(byte[] mmData,
			ImageReadParam param, int width, int height,
			Consumer<LocalizedFormatedString> handler) throws IIOException;

	/**
	 * Am iterator of the image types supported by this processor.
	 * <p>
	 * The types in the iterator can be used to both encode and decode mipmaps.
	 * @param width
	 *            the width of the image in pixels.
	 * @param height
	 *            the height of the image in pixels.
	 * @return iterator of supported image types.
	 */
	public abstract Iterator<ImageTypeSpecifier> getSupportedImageTypes(int width, int height);

	public abstract void readObject(ImageInputStream src) throws IOException;

	public abstract void writeObject(ImageOutputStream dst) throws IOException;
}
