package io.github.teemuki8.libgdx.agent.gameplay.fixture;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Desktop entry point for interactive, smoke, and exclusive stdio MCP launch modes. */
public final class Launcher {
    private Launcher() {
    }

    /** Parses the bounded canonical flags and starts the application. */
    public static void main(String[] args) {
        boolean mcp = false;
        int smokeFrames = 0;
        String screenshotPath = "build/smoke.png";
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--mcp" -> mcp = true;
                case "--smoke" -> {
                    requireValue(args, index, "--smoke");
                    smokeFrames = Integer.parseInt(args[++index]);
                    if (smokeFrames < 1 || smokeFrames > 36_000) {
                        throw new IllegalArgumentException(
                                "--smoke must be between 1 and 36000 frames");
                    }
                }
                case "--screenshot" -> {
                    requireValue(args, index, "--screenshot");
                    screenshotPath = args[++index];
                }
                default -> throw new IllegalArgumentException(
                        "unknown argument: " + args[index]);
            }
        }
        if (mcp && smokeFrames > 0) {
            throw new IllegalArgumentException("--mcp and --smoke are exclusive launch modes");
        }
        Lwjgl3ApplicationConfiguration configuration =
                new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("libGDX Agent Arena");
        configuration.setWindowedMode(
                ArenaWorldFactory.VIEWPORT_WIDTH, ArenaWorldFactory.VIEWPORT_HEIGHT);
        configuration.setForegroundFPS(60);
        configuration.useVsync(true);
        new Lwjgl3Application(
                new ArenaApplication(mcp, smokeFrames, screenshotPath), configuration);
    }

    private static void requireValue(String[] args, int index, String flag) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
    }
}
