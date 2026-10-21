import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Draws the Baca icon on the 108-unit adaptive-icon grid (same coordinates as
 * android/src/main/res/drawable/ic_launcher_foreground.xml) and writes the
 * desktop icons. Regenerate from the repo root:
 *
 *   java desktop/icons/BacaIcon.java /tmp/baca-icon desktop
 *   iconutil -c icns /tmp/baca-icon/baca.iconset -o desktop/icons/baca.icns
 *   cp /tmp/baca-icon/baca.ico /tmp/baca-icon/baca.png desktop/icons/
 *   cp /tmp/baca-icon/baca.png desktop/src/main/resources/baca.png
 */
public class BacaIcon {
    static final Color TEAL = new Color(0x0F766E), PAPER = new Color(0xF7F3EA), RIBBON = new Color(0xF6D860), SHADE = new Color(0xE3DCCB);

    static Path2D leftPage() { Path2D p = new Path2D.Float(); p.moveTo(52.5, 41); p.quadTo(40, 33.5, 25, 36); p.lineTo(25, 71); p.quadTo(40, 68.5, 52.5, 76); p.closePath(); return p; }
    static Path2D rightPage() { Path2D p = new Path2D.Float(); p.moveTo(55.5, 41); p.quadTo(68, 33.5, 83, 36); p.lineTo(83, 71); p.quadTo(68, 68.5, 55.5, 76); p.closePath(); return p; }
    static Path2D ribbon() { Path2D p = new Path2D.Float(); p.moveTo(68, 36.6); p.lineTo(74, 35.7); p.lineTo(74, 56); p.lineTo(71, 53); p.lineTo(68, 56); p.closePath(); return p; }

    /** size px; full = macOS-style rounded square with margins, else full-bleed square (adaptive preview). */
    static BufferedImage render(int size, boolean roundedSquare) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double scale;
        if (roundedSquare) {
            double margin = size * 100.0 / 1024, side = size - 2 * margin, radius = side * 0.225;
            g.setColor(TEAL);
            g.fill(new RoundRectangle2D.Double(margin, margin, side, side, radius * 2, radius * 2));
            // the 108 grid's 86-unit middle fills the rounded square: the book keeps a margin
            scale = side / 86.0;
            g.translate(margin - 11 * scale, margin - 11 * scale);
        } else {
            g.setColor(TEAL); g.fillRect(0, 0, size, size);
            scale = size / 108.0;
        }
        g.scale(scale, scale);
        g.setColor(PAPER); g.fill(leftPage()); g.fill(rightPage());
        // a little depth toward the spine
        g.setColor(SHADE);
        Path2D l = new Path2D.Float(); l.moveTo(52.5, 41); l.quadTo(49.5, 39.4, 46.5, 38.3); l.lineTo(46.5, 73); l.quadTo(49.5, 74.2, 52.5, 76); l.closePath(); g.fill(l);
        Path2D r = new Path2D.Float(); r.moveTo(55.5, 41); r.quadTo(58.5, 39.4, 61.5, 38.3); r.lineTo(61.5, 73); r.quadTo(58.5, 74.2, 55.5, 76); r.closePath(); g.fill(r);
        g.setColor(RIBBON); g.fill(ribbon());
        g.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        ImageIO.write(render(512, true), "png", new File(out, "preview_desktop.png"));
        // adaptive preview: full bleed masked to a circle like a launcher would
        BufferedImage full = render(432, false), circle = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = circle.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Double(0, 0, 432, 432)); g.drawImage(full, 0, 0, null); g.dispose();
        ImageIO.write(circle, "png", new File(out, "preview_android.png"));
        if (args.length > 1) {
            // desktop assets: iconset for iconutil, plain PNG, ICO with PNG entries
            File set = new File(out, "baca.iconset"); set.mkdirs();
            int[] sizes = {16, 32, 128, 256, 512};
            for (int s : sizes) {
                ImageIO.write(render(s, true), "png", new File(set, "icon_" + s + "x" + s + ".png"));
                ImageIO.write(render(s * 2, true), "png", new File(set, "icon_" + s + "x" + s + "@2x.png"));
            }
            ImageIO.write(render(512, true), "png", new File(out, "baca.png"));
            writeIco(new File(out, "baca.ico"), new int[]{16, 32, 48, 256});
        }
    }

    static void writeIco(File file, int[] sizes) throws IOException {
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) { ByteArrayOutputStream b = new ByteArrayOutputStream(); ImageIO.write(render(sizes[i], true), "png", b); pngs[i] = b.toByteArray(); }
        try (DataOutputStream o = new DataOutputStream(new FileOutputStream(file))) {
            o.write(le16(0)); o.write(le16(1)); o.write(le16(sizes.length));
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i] >= 256 ? 0 : sizes[i];
                o.writeByte(s); o.writeByte(s); o.writeByte(0); o.writeByte(0);
                o.write(le16(1)); o.write(le16(32)); o.write(le32(pngs[i].length)); o.write(le32(offset));
                offset += pngs[i].length;
            }
            for (byte[] p : pngs) o.write(p);
        }
    }
    static byte[] le16(int v) { return new byte[]{(byte) v, (byte) (v >> 8)}; }
    static byte[] le32(int v) { return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}; }
}
