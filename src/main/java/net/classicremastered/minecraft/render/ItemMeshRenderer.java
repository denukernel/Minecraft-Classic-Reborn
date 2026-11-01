package net.classicremastered.minecraft.render;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;

/** Extruded alpha silhouette, with textured front/back and only exposed pixel edges. */
public final class ItemMeshRenderer {
    private static final Map<String, BufferedImage> images = new HashMap<>();
    private static final Map<String, BufferedImage> regions = new HashMap<>();

    private static BufferedImage image(String path) {
        return images.computeIfAbsent(path, key -> {
            try (InputStream in = ItemMeshRenderer.class.getResourceAsStream(key)) {
                return in == null ? new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB) : ImageIO.read(in);
            } catch (Exception e) { throw new IllegalStateException("Cannot load item mesh " + key,e); }
        });
    }

    private static boolean solid(BufferedImage image,int x,int y) {
        return x>=0 && y>=0 && x<image.getWidth() && y<image.getHeight() && (image.getRGB(x,y)>>>24)>25;
    }

    public static int edgeCount(String texture) {
        BufferedImage image=image(texture);int count=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(solid(image,x,y)) {
            if(!solid(image,x-1,y))count++;if(!solid(image,x+1,y))count++;
            if(!solid(image,x,y-1))count++;if(!solid(image,x,y+1))count++;
        }
        return count;
    }

    public static void render(TextureManager textures,String texture,float brightness) {
        render(textures,texture,brightness,0,1,0xffffff);
    }

    public static void renderItem(TextureManager textures,net.classicremastered.minecraft.level.itemstack.Item item,float brightness) {
        String texture=item instanceof net.classicremastered.minecraft.level.itemstack.BowItem ? "/items/bow.png" : item.getTexture();
        render(textures,texture,brightness,item.getModelTextureU0(),item.getModelTextureU1(),item.getModelTint());
    }

    private static void render(TextureManager textures,String texture,float brightness,float u0,float u1,int tint) {
        if(texture==null)return;
        BufferedImage full=image(texture);
        int left=Math.round(full.getWidth()*u0),right=Math.round(full.getWidth()*u1);
        BufferedImage image=regions.computeIfAbsent(texture+":"+u0+":"+u1,
            key->full.getSubimage(left,0,right-left,full.getHeight()));
        int w=image.getWidth(),h=image.getHeight();
        float r=((tint>>16)&255)/255f,g=((tint>>8)&255)/255f,b=(tint&255)/255f;
        float thickness=.03125f;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1f);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures.load(texture));
            GL11.glColor3f(brightness*r,brightness*g,brightness*b);
            GL11.glBegin(GL11.GL_QUADS);
            quad(-.5f,-.5f,thickness,.5f,-.5f,thickness,.5f,.5f,thickness,-.5f,.5f,thickness,u0,1,u1,0);
            quad(.5f,-.5f,-thickness,-.5f,-.5f,-thickness,-.5f,.5f,-thickness,.5f,.5f,-thickness,u1,1,u0,0);
            GL11.glColor3f(brightness*.7f*r,brightness*.7f*g,brightness*.7f*b);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(solid(image,x,y)) {
                float x0=x/(float)w-.5f,x1=(x+1)/(float)w-.5f;
                float y1=.5f-y/(float)h,y0=.5f-(y+1)/(float)h;
                float u=(left+x+.5f)/full.getWidth(),v=(y+.5f)/h;
                if(!solid(image,x-1,y))edge(x0,y0,-thickness,x0,y1,-thickness,x0,y1,thickness,x0,y0,thickness,u,v);
                if(!solid(image,x+1,y))edge(x1,y0,thickness,x1,y1,thickness,x1,y1,-thickness,x1,y0,-thickness,u,v);
                if(!solid(image,x,y-1))edge(x0,y1,thickness,x1,y1,thickness,x1,y1,-thickness,x0,y1,-thickness,u,v);
                if(!solid(image,x,y+1))edge(x0,y0,-thickness,x1,y0,-thickness,x1,y0,thickness,x0,y0,thickness,u,v);
            }
            GL11.glEnd();
        } finally {GL11.glPopAttrib();}
    }

    private static void quad(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float u0,float v0,float u1,float v1) {
        GL11.glTexCoord2f(u0,v0);GL11.glVertex3f(ax,ay,az);GL11.glTexCoord2f(u1,v0);GL11.glVertex3f(bx,by,bz);
        GL11.glTexCoord2f(u1,v1);GL11.glVertex3f(cx,cy,cz);GL11.glTexCoord2f(u0,v1);GL11.glVertex3f(dx,dy,dz);
    }
    private static void edge(float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float u,float v) {
        quad(ax,ay,az,bx,by,bz,cx,cy,cz,dx,dy,dz,u,v,u,v);
    }
}
