package dev.csarsenal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    public static final String CAT = "key.categories.csarsenal";
    public static final KeyMapping RELOAD = new KeyMapping("key.csarsenal.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CAT);
    public static final KeyMapping INSPECT = new KeyMapping("key.csarsenal.inspect", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CAT);
    public static final KeyMapping WALK = new KeyMapping("key.csarsenal.walk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CAT);
    public static final KeyMapping CROUCH = new KeyMapping("key.csarsenal.crouch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CAT);
    public static final KeyMapping BUY = new KeyMapping("key.csarsenal.buy", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CAT);
    public static final KeyMapping DROP = new KeyMapping("key.csarsenal.drop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CAT);

    /** raw physical state (ignores key conflicts with vanilla bindings on the same key) */
    public static boolean rawDown(KeyMapping k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        InputConstants.Key key = k.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return InputConstants.isKeyDown(window, key.getValue());
    }

    private Keys() {
    }
}
