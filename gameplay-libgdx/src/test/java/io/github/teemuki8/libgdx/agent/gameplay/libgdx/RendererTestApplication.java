package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.lwjgl.glfw.GLFW;

/** Each test owns its window; unregister the libGDX callback before libGDX frees it. */
final class RendererTestApplication extends Lwjgl3Application {
    RendererTestApplication(ApplicationListener listener, Lwjgl3ApplicationConfiguration configuration) {
        super(listener, configuration);
    }

    @Override protected void cleanup() {
        // libGDX 1.14.2 otherwise leaves a freed upcall pointer registered in GLFW 3.4.3.
        // The superclass owns the callback: unregister only, never free it twice.
        GLFW.glfwSetErrorCallback(null);
        super.cleanup();
    }
}
