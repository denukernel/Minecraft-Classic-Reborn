package net.classicremastered.minecraft;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GLContext;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

final class FabulousTransparency {
    private int framebuffer, color, depth;
    private int width, height;
    private int destination;
    private boolean failed;

    boolean prepare(GameSettings settings, int width, int height) {
        if (settings.graphicsMode != GameSettings.GraphicsMode.FABULOUS || settings.anaglyph) {
            release();
            return false;
        }
        settings.fabulousUnavailable = failed || !GLContext.getCapabilities().OpenGL30;
        if (settings.fabulousUnavailable || width <= 0 || height <= 0) return false;
        if (framebuffer != 0 && this.width == width && this.height == height) return true;

        release();
        this.width = width;
        this.height = height;
        int old = glGetInteger(GL_FRAMEBUFFER_BINDING);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int oldRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        int depthBits = glGetInteger(GL_DEPTH_BITS);
        boolean stencil = glGetInteger(GL_STENCIL_BITS) > 0;
        framebuffer = glGenFramebuffers();
        color = glGenTextures();
        depth = glGenRenderbuffers();
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glBindTexture(GL_TEXTURE_2D, color);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    width,
                    height,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
            glBindRenderbuffer(GL_RENDERBUFFER, depth);
            int depthFormat =
                    stencil
                            ? (depthBits > 24 ? GL_DEPTH32F_STENCIL8 : GL_DEPTH24_STENCIL8)
                            : depthBits <= 16
                                    ? GL_DEPTH_COMPONENT16
                                    : depthBits <= 24 ? GL_DEPTH_COMPONENT24 : GL_DEPTH_COMPONENT32;
            glRenderbufferStorage(GL_RENDERBUFFER, depthFormat, width, height);
            glFramebufferRenderbuffer(
                    GL_FRAMEBUFFER,
                    stencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT,
                    GL_RENDERBUFFER,
                    depth);
            failed = glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE;
            if (glGetError() != GL_NO_ERROR) failed = true;
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, old);
            glBindRenderbuffer(GL_RENDERBUFFER, oldRenderbuffer);
            glBindTexture(GL_TEXTURE_2D, oldTexture);
        }
        if (failed) deleteTarget();
        settings.fabulousUnavailable = failed;
        return !failed;
    }

    boolean begin(GameSettings settings) {
        glPushAttrib(
                GL_COLOR_BUFFER_BIT
                        | GL_DEPTH_BUFFER_BIT
                        | GL_ENABLE_BIT
                        | GL_CURRENT_BIT
                        | GL_TEXTURE_BIT);
        destination = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, destination);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glBlitFramebuffer(
                0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        if (glGetError() != GL_NO_ERROR) {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination);
            failed = settings.fabulousUnavailable = true;
            deleteTarget();
            glPopAttrib();
            return false;
        }
        glColorMask(true, true, true, true);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        glDepthMask(false);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, .003f);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        return true;
    }

    void composite() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination);
        try {
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_ALPHA_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_FOG);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, color);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(1, 1, 1, 1);
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0);
            glVertex2f(-1, -1);
            glTexCoord2f(1, 0);
            glVertex2f(1, -1);
            glTexCoord2f(1, 1);
            glVertex2f(1, 1);
            glTexCoord2f(0, 1);
            glVertex2f(-1, 1);
            glEnd();
            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
        } finally {
            glPopAttrib();
        }
    }

    void release() {
        deleteTarget();
        failed = false;
    }

    private void deleteTarget() {
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (depth != 0) glDeleteRenderbuffers(depth);
        if (color != 0) glDeleteTextures(color);
        framebuffer = color = depth = 0;
    }
}
