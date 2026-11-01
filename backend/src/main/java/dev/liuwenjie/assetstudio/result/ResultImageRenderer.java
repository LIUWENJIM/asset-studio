package dev.liuwenjie.assetstudio.result;

import dev.liuwenjie.assetstudio.config.StorageProperties;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 用 Java2D 把用户上传的图片拼成一张结果图。
 * <p>
 * 刻意只画 ASCII 文案：容器里的 JRE 常常没有中文字体，画中文会变成方框。
 * 也刻意不引任何图像库，标准 JDK 足够。
 */
@Component
public class ResultImageRenderer {

    private static final Logger log = LoggerFactory.getLogger(ResultImageRenderer.class);

    private static final int WIDTH = 900;
    private static final int HEIGHT = 1200;
    private static final int PADDING = 44;
    private static final int HEADER_HEIGHT = 96;
    private static final int GAP = 14;
    private static final int RADIUS = 18;

    private static final Color BG_TOP = new Color(0x14, 0x17, 0x12);
    private static final Color BG_BOTTOM = new Color(0x0B, 0x0D, 0x0A);
    private static final Color ACCENT = new Color(0x93, 0xB5, 0x45);
    private static final Color SLOT = new Color(0x22, 0x26, 0x1E);

    private final Path root;

    public ResultImageRenderer(StorageProperties storageProperties) {
        this.root = Path.of(storageProperties.root()).toAbsolutePath().normalize();
    }

    /**
     * 生成结果图并返回可直接访问的相对 URL（形如 {@code /files/results/{userId}/{jobId}.png}）。
     *
     * @param storedPaths 素材在存储根目录下的相对路径，按提交顺序
     */
    public String render(String userId, String jobId, List<String> storedPaths) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            applyQuality(g);
            drawBackground(g);
            drawHeader(g);

            List<BufferedImage> images = load(storedPaths);
            int contentTop = PADDING + HEADER_HEIGHT;
            int contentHeight = HEIGHT - contentTop - PADDING;
            drawSlots(g, images, PADDING, contentTop, WIDTH - PADDING * 2, contentHeight);

            drawFooter(g);
        } finally {
            g.dispose();
        }

        String relative = "results/" + userId + "/" + jobId + ".png";
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("非法结果路径");
        }
        try {
            Files.createDirectories(target.getParent());
            ImageIO.write(canvas, "png", target.toFile());
        } catch (IOException e) {
            log.error("写入结果图失败：{}", target, e);
            throw new IllegalStateException("结果图写入失败", e);
        }
        return "/files/" + relative;
    }

    private void applyQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new java.awt.GradientPaint(0, 0, BG_TOP, 0, HEIGHT, BG_BOTTOM));
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawHeader(Graphics2D g) {
        g.setColor(ACCENT);
        g.fillRect(PADDING, PADDING + 18, 34, 4);

        g.setColor(new Color(0xE8, 0xED, 0xDF));
        drawText(g, "ASSET STUDIO", Font.BOLD, 30, PADDING + 64);

        g.setColor(new Color(0x7C, 0x84, 0x74));
        drawText(g, "Made from your photos", Font.PLAIN, 15, PADDING + 88);
    }

    private void drawFooter(Graphics2D g) {
        g.setColor(new Color(0x5E, 0x66, 0x56));
        drawText(g, "generated offline by asset-studio", Font.PLAIN, 14, HEIGHT - PADDING + 18);
    }

    /**
     * 绘制说明文字。容器镜像里没有安装系统字体，此时 Java2D 画不出字形，
     * 但结果图本身的图片合成不受影响，所以这里按「有字体就画、没有就跳过」处理，
     * 避免为了几行装饰文字而在镜像里引入 apt 依赖。
     */
    private void drawText(Graphics2D g, String text, int style, int size, int baselineY) {
        try {
            g.setFont(new Font(Font.SANS_SERIF, style, size));
            g.drawString(text, PADDING, baselineY);
        } catch (RuntimeException ignored) {
            // 缺少可用字体时静默跳过，不影响结果图产出。
        }
    }

    /** 按素材数量选择版式：1 张铺满，2 张上下分栏，3 张及以上上大下小。 */
    private void drawSlots(Graphics2D g, List<BufferedImage> images, int x, int y, int width, int height) {
        int count = images.size();
        if (count == 0) {
            drawCover(g, null, x, y, width, height);
            return;
        }
        if (count == 1) {
            drawCover(g, images.get(0), x, y, width, height);
            return;
        }
        if (count == 2) {
            int slotHeight = (height - GAP) / 2;
            drawCover(g, images.get(0), x, y, width, slotHeight);
            drawCover(g, images.get(1), x, y + slotHeight + GAP, width, slotHeight);
            return;
        }

        int topHeight = (int) (height * 0.58);
        drawCover(g, images.get(0), x, y, width, topHeight);

        List<BufferedImage> rest = images.subList(1, count);
        int restTop = y + topHeight + GAP;
        int restHeight = height - topHeight - GAP;
        int columnWidth = (width - GAP * (rest.size() - 1)) / rest.size();
        for (int i = 0; i < rest.size(); i++) {
            drawCover(g, rest.get(i), x + i * (columnWidth + GAP), restTop, columnWidth, restHeight);
        }
    }

    /** 等比裁切填满槽位，并裁成圆角。图片读不出来时画一个占位块。 */
    private void drawCover(Graphics2D g, BufferedImage image, int x, int y, int width, int height) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, width, height, RADIUS, RADIUS);

        g.setColor(SLOT);
        g.fill(shape);

        if (image == null) {
            g.setColor(new Color(0x3A, 0x40, 0x33));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(shape);
            return;
        }

        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        double scale = Math.max((double) width / sourceWidth, (double) height / sourceHeight);
        int drawWidth = (int) Math.round(sourceWidth * scale);
        int drawHeight = (int) Math.round(sourceHeight * scale);
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        Graphics2D clipped = (Graphics2D) g.create();
        try {
            clipped.setClip(shape);
            clipped.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
        } finally {
            clipped.dispose();
        }
    }

    private List<BufferedImage> load(List<String> storedPaths) {
        List<BufferedImage> images = new ArrayList<>(storedPaths.size());
        for (String storedPath : storedPaths) {
            Path path = root.resolve(storedPath).normalize();
            if (!path.startsWith(root)) {
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) {
                    images.add(image);
                }
            } catch (IOException e) {
                log.warn("读取素材失败，将使用占位块：{}", path, e);
            }
        }
        return images;
    }
}