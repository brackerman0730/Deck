package com.deck.config;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds Windows {@code .lnk} shortcuts that launch Deck like a normal desktop
 * app, plus the icon they display.
 *
 * <p>Shortcuts target {@code javaw.exe} directly rather than a {@code .bat} or
 * {@code run.ps1}. Both of those flash a console window on every launch — the
 * single most "this is not a real app" tell — and {@code javaw} is the
 * console-less JVM launcher precisely for GUI programs. The trade-off is that
 * startup errors go nowhere visible; run {@code run.ps1} from a terminal when
 * you need to see them.
 *
 * <p>{@code .lnk} is an OLE compound document, impractical to write by hand
 * without a dependency, so we drive {@code WScript.Shell} through PowerShell —
 * present on every Windows install.
 */
public final class WindowsShortcuts {

    /** Same JavaFX SDK location {@code run.ps1} uses. */
    private static final String JAVAFX_LIB = "C:\\javafx-sdk-26.0.1\\lib";

    /** Icon file generated into the project root on demand. */
    private static final String ICON_NAME = "Deck.ico";

    private static final int TIMEOUT_SECONDS = 20;

    /** {@code WScript.Shell} window styles. */
    public static final int WINDOW_NORMAL    = 1;
    public static final int WINDOW_MINIMIZED = 7;

    private WindowsShortcuts() { }

    // ---- launch command ----------------------------------------------------

    /** The console-less JVM launcher from the JDK currently running Deck. */
    public static Path javawExe() {
        return Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
    }

    /**
     * JVM arguments mirroring {@code run.ps1}. The classpath stays relative
     * because every shortcut sets its working directory to the project root —
     * which also keeps the argument string comfortably short.
     */
    public static String jvmArguments() {
        return String.join(" ",
                "--module-path \"" + JAVAFX_LIB + "\"",
                "--add-modules javafx.controls,javafx.media,javafx.fxml,javafx.swing",
                "-Djavax.net.ssl.trustStoreType=Windows-ROOT",
                "--enable-native-access=javafx.graphics",
                "--enable-native-access=ALL-UNNAMED",
                "-cp \"out;lib\\sqlite-jdbc-3.53.2.0.jar\"",
                "com.deck.app.Main");
    }

    /**
     * Project root Deck was launched from — where {@code out\} and {@code lib\}
     * live. Deck is always started from its own root.
     */
    public static Path projectRoot() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    // ---- desktop shortcut --------------------------------------------------

    /**
     * The user's real Desktop folder.
     *
     * <p>Deliberately <em>not</em> {@code user.home\Desktop}. OneDrive's "back
     * up your folders" redirects the Desktop to
     * {@code %USERPROFILE%\OneDrive\Desktop}, and the old literal folder often
     * still exists — so the naive path silently succeeds while putting the
     * shortcut somewhere the user never sees. {@code FileSystemView} asks the
     * Windows shell, which knows about the redirection.
     */
    public static Path desktopFolder() {
        try {
            final java.io.File shellDesktop =
                    javax.swing.filechooser.FileSystemView.getFileSystemView().getHomeDirectory();
            if (shellDesktop != null && shellDesktop.isDirectory()) {
                return shellDesktop.toPath();
            }
        } catch (Exception ignored) {
            // Fall through to the literal path below.
        }
        return Paths.get(System.getProperty("user.home"), "Desktop");
    }

    /** {@code <Desktop>\Deck.lnk}. */
    public static Path desktopShortcutPath() {
        return desktopFolder().resolve("Deck.lnk");
    }

    public static boolean isDesktopShortcutInstalled() {
        return Files.isRegularFile(desktopShortcutPath());
    }

    /** Creates or removes the Desktop shortcut. Safe to call repeatedly. */
    public static void setDesktopShortcut(final boolean enabled) throws IOException {
        if (enabled) {
            createShortcut(desktopShortcutPath(), WINDOW_NORMAL, "Deck — app launcher");
        } else {
            Files.deleteIfExists(desktopShortcutPath());
        }
    }

    // ---- shortcut creation -------------------------------------------------

    /**
     * Writes a {@code .lnk} pointing at {@code javaw} with Deck's arguments.
     *
     * @param lnk          where to write the shortcut
     * @param windowStyle  {@link #WINDOW_NORMAL} or {@link #WINDOW_MINIMIZED}
     * @param description  hover tooltip text
     */
    public static void createShortcut(final Path lnk, final int windowStyle,
                                      final String description) throws IOException {
        final Path root  = projectRoot();
        final Path outDir = root.resolve("out");
        if (!Files.isDirectory(outDir)) {
            throw new IOException("No compiled output at " + outDir
                    + " — run compile.ps1 first, and start Deck from its project folder.");
        }
        final Path javaw = javawExe();
        if (!Files.isRegularFile(javaw)) {
            throw new IOException("Can't find javaw.exe at " + javaw);
        }

        final Path icon = ensureIcon(root);
        Files.createDirectories(lnk.getParent());

        final String script = String.join(" ",
                "$s = (New-Object -ComObject WScript.Shell).CreateShortcut(" + psQuote(lnk.toString()) + ");",
                "$s.TargetPath = " + psQuote(javaw.toString()) + ";",
                "$s.Arguments = " + psQuote(jvmArguments()) + ";",
                "$s.WorkingDirectory = " + psQuote(root.toString()) + ";",
                "$s.IconLocation = " + psQuote(icon.toString()) + ";",
                "$s.WindowStyle = " + windowStyle + ";",
                "$s.Description = " + psQuote(description) + ";",
                "$s.Save()");

        runPowerShell(script);

        if (!Files.isRegularFile(lnk)) {
            throw new IOException("PowerShell reported success but no shortcut appeared at " + lnk);
        }
    }

    // ---- icon --------------------------------------------------------------

    /** Generates {@code Deck.ico} in the project root if it isn't there yet. */
    public static Path ensureIcon(final Path root) throws IOException {
        final Path ico = root.resolve(ICON_NAME);
        if (Files.isRegularFile(ico)) return ico;
        Files.write(ico, buildIco(drawTile(256)));
        return ico;
    }

    /**
     * Deck's mark: a 2x2 grid of rounded tiles on the app's dark surface, in
     * the accent colour from {@code styles.css}. Drawn rather than shipped as a
     * binary, and deliberately geometric so it needs no particular font.
     */
    private static BufferedImage drawTile(final int size) {
        final Color surface = new Color(0x1A, 0x1D, 0x24);
        final Color accent  = new Color(0x4F, 0xD1, 0xFF);

        final BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        final double pad    = size * 0.06;
        final double radius = size * 0.22;
        g.setColor(surface);
        g.fill(new RoundRectangle2D.Double(pad, pad, size - 2 * pad, size - 2 * pad, radius, radius));
        g.setColor(new Color(0x2A, 0x2E, 0x38));
        g.setStroke(new BasicStroke((float) (size * 0.015)));
        g.draw(new RoundRectangle2D.Double(pad, pad, size - 2 * pad, size - 2 * pad, radius, radius));

        // 2x2 tile grid, echoing the launcher's app grid.
        final double gap   = size * 0.07;
        final double inset = size * 0.22;
        final double cell  = (size - 2 * inset - gap) / 2.0;
        final double cellR = cell * 0.30;
        g.setColor(accent);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                // Fade the last tile so the mark reads as "a grid you add to".
                g.setColor(row == 1 && col == 1 ? new Color(0x4F, 0xD1, 0xFF, 110) : accent);
                g.fill(new RoundRectangle2D.Double(
                        inset + col * (cell + gap),
                        inset + row * (cell + gap),
                        cell, cell, cellR, cellR));
            }
        }
        g.dispose();
        return img;
    }

    /**
     * Wraps a PNG in a single-entry ICO container. Windows Vista and later read
     * PNG-compressed icon entries directly, so no BMP/DIB encoding needed.
     */
    private static byte[] buildIco(final BufferedImage img) throws IOException {
        final ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(img, "png", pngOut);
        final byte[] png = pngOut.toByteArray();

        // 256px is encoded as 0 in the single-byte width/height fields.
        final int dim = img.getWidth() >= 256 ? 0 : img.getWidth();

        final ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0);   // reserved
        header.putShort((short) 1);   // type: icon
        header.putShort((short) 1);   // image count
        header.put((byte) dim);       // width
        header.put((byte) dim);       // height
        header.put((byte) 0);         // palette size (0 = no palette)
        header.put((byte) 0);         // reserved
        header.putShort((short) 1);   // colour planes
        header.putShort((short) 32);  // bits per pixel
        header.putInt(png.length);    // bytes of image data
        header.putInt(22);            // offset: straight after this header

        final ByteArrayOutputStream ico = new ByteArrayOutputStream();
        ico.write(header.array());
        ico.write(png);
        return ico.toByteArray();
    }

    // ---- PowerShell plumbing -----------------------------------------------

    /** Runs a PowerShell snippet, throwing with output attached on failure. */
    static void runPowerShell(final String script) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(List.of(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script));
        pb.redirectErrorStream(true);

        final Process proc = pb.start();
        final String output;
        try (OutputStream ignored = proc.getOutputStream(); var in = proc.getInputStream()) {
            output = new String(in.readAllBytes()).trim();
        }

        final boolean finished;
        try {
            finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new IOException("Interrupted while writing the shortcut");
        }
        if (!finished) {
            proc.destroyForcibly();
            throw new IOException("Timed out writing the shortcut");
        }
        if (proc.exitValue() != 0) {
            throw new IOException("PowerShell failed (exit " + proc.exitValue() + ")"
                    + (output.isEmpty() ? "" : ": " + output));
        }
    }

    /**
     * Wraps a value in a PowerShell single-quoted literal, so {@code $} and
     * backticks stay literal; an embedded quote is escaped by doubling it.
     */
    static String psQuote(final String raw) {
        return "'" + raw.replace("'", "''") + "'";
    }
}
