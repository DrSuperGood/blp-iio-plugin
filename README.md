# BLP IIO Plugins
Java Image IO plugins for BLP texture files used in games like Warcraft III and World of Warcraft.

The BLP file format, extension ".blp", was created by Blizzard Entertainment for games like Warcraft III and World of Warcraft to hold texture assets used for the graphics. There exists 3 versions of the format, BLP0 being used by the Warcraft III Reign of Chaos beta, BLP1 by all released versions of Warcraft III and BLP2 by World of Warcraft.

Being a non-standard image format no official specification exists. Instead fans of the games reverse engineered the format and wrote a variety of tools and libraries to read or write BLP files. However many of these were based on an incomplete and largely wrong reference resulting in strange, suboptimal or buggy behavior when interacting with BLP files or using produced BLP files in the intended games. Despite the huge popularity of Warcraft III the true mechanics of the format remained a mystery for over a decade.

Thanks to research done by a user at hiveworkshop.com going by the name of DrSuperGood/ImperialGood a more correct specification was created. From this a highly compatible BLP Image IO plugin was created for the Java programming language as a working reference. From this more accurate plugins or libraries targeting other languages can be easily created.

Although other BLP library implementations existed for the Java programming language, most of them had serious flaws. None were in the form of an Image IO plugin, meaning that the standard Image IO API could not be used. They also fail to comply to the format standards. On top of that some had very heavy dependencies such as relying on other huge Image IO libraries for functionality.

Provided is a light weight highly compliant IIO plugin for a ImageReader to read from BLP files. BLP0 files can be read by providing either a File or Path object to the .blp file. BLP1 files can be read like most standard image formats. BLP files may contain multiple images, each image representing a mipmap level. To use all one has to is build a JAR using Eclipse and place it in the path of a Java program and the plugin will automatically be used. Since it is based on the IIO API of standard Java even existing Java programs can take advantage of the plugin without needing to be rebuilt.

The following standard Java will create a BufferedImage from a BLP file... 
 
	File blpFile = new File(...);
	BufferedImage img = ImageIO.read(blpFile);
  
The produced BufferedImage is intended for accurate reproduction of BLP file content and not performance. It is recommended to convert to a native BufferedImage format if performing any serious processing or when speed is important. The BufferedImage is always in a linear RGB color space, matching how the textures are loaded and processed. Conversion from a linear RGB to sRGB is up to the programmer.

A Writer is also provided which can produce a BLP file from RenderedImage. The writer is only capable of writing out to fresh BLP files and will throw an exception if trying to modify an existing BLP file. By default the writer will produce all required mipmaps using area averaging for maximum quality rather than speed. JPEG content BLP files will default to 0.9 quality and quality can be adjusted explicitly using a ImageWriteParam. By default image dimensions will be automatically optimized to the maximum useful dimensions for the version specified.

The following standard Java will write a BufferedImage (implements RenderedImage) to a BLP file.

	BufferedImage img = ...;
	File blpFile = new File(...);
	ImageIO.write(img, "blp", blpFile);

BLP2 is not supported due to a lack of detailed specification and test examples. There are currently no plans for developing BLP2 support unless someone is found to take on the task. Until then it is recommended to use another BLP library that specifically targets BLP2.

