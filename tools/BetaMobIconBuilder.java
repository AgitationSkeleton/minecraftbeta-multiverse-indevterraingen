import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class BetaMobIconBuilder {
    private static final class Face {
        final String name;
        final int x, y, width, height;
        Face(String name, int x, int y, int width, int height) {
            this.name = name; this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }

    private static final Face[] FACES = new Face[] {
        new Face("char", 8, 8, 8, 8),
        new Face("zombie", 8, 8, 8, 8),
        new Face("skeleton", 8, 8, 8, 8),
        new Face("creeper", 8, 8, 8, 8),
        new Face("pigzombie", 8, 8, 8, 8),
        new Face("pig", 8, 8, 8, 8),
        new Face("cow", 6, 6, 8, 8),
        new Face("sheep", 8, 8, 6, 6),
        new Face("chicken", 3, 3, 4, 6),
        new Face("wolf", 4, 4, 6, 6),
        new Face("spider", 40, 12, 8, 8),
        new Face("squid", 12, 12, 12, 16),
        new Face("ghast", 16, 16, 16, 16),
        new Face("slime", 8, 8, 8, 8)
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("texture-directory output-directory");
        File source = new File(args[0]);
        File output = new File(args[1]);
        output.mkdirs();
        for (Face face : FACES) {
            BufferedImage texture = ImageIO.read(new File(source, face.name + ".png"));
            BufferedImage crop = texture.getSubimage(face.x, face.y, face.width, face.height);
            BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = icon.createGraphics();
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(crop, 0, 0, 16, 16, null);
            if (face.name.equals("pigzombie")) {
                BufferedImage hat = texture.getSubimage(40, 8, 8, 8);
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.drawImage(hat, 0, 0, 16, 16, null);
            }
            graphics.dispose();
            ImageIO.write(icon, "png", new File(output, face.name + ".png"));
        }
    }
}
