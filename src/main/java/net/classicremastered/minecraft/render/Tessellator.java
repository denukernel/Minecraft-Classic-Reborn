package net.classicremastered.minecraft.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

public final class Tessellator {
	private static boolean convertQuadsToTriangles = false;
	private static boolean tryVBO = false;
	private ByteBuffer byteBuffer = BufferUtils.createByteBuffer(8388608);
	private int[] rawBuffer = new int[2097152];
	private int vertexCount = 0;
	private float textureU;
	private float textureV;
	private int color;
	private boolean hasColor = false;
	private boolean hasTexture = false;
	private int rawBufferIndex = 0;
	private int addedVertices = 0;
	private boolean isColorDisabled = false;
	private int drawMode;
	public static Tessellator instance = new Tessellator();
	private boolean isDrawing = false;
	private boolean useVBO = false;
	private IntBuffer vertexBuffers;
	private int vboIndex = 0;
	private int vboCount = 10;

	private Tessellator() {
		this.useVBO = false;
		if(this.useVBO) {
			this.vertexBuffers = BufferUtils.createIntBuffer(this.vboCount);
			ARBVertexBufferObject.glGenBuffersARB(this.vertexBuffers);
		}

	}

	public final void draw() {
		if(!this.isDrawing) {
			throw new IllegalStateException("Not tesselating!");
		} else {
			this.isDrawing = false;
			if(this.vertexCount > 0) {
				IntBuffer var1 = this.byteBuffer.asIntBuffer();
				FloatBuffer var2 = this.byteBuffer.asFloatBuffer();
				var1.clear();
				var1.put(this.rawBuffer, 0, this.rawBufferIndex);
				this.byteBuffer.position(0);
				this.byteBuffer.limit(this.rawBufferIndex << 2);
				if(this.useVBO) {
					this.vboIndex = (this.vboIndex + 1) % this.vboCount;
					ARBVertexBufferObject.glBindBufferARB(GL15.GL_ARRAY_BUFFER, this.vertexBuffers.get(this.vboIndex));
					ARBVertexBufferObject.glBufferDataARB(GL15.GL_ARRAY_BUFFER, this.byteBuffer, GL15.GL_STREAM_DRAW);
				}

				if(this.hasTexture) {
					if(this.useVBO) {
						GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 32, 12L);
					} else {
						var2.position(3);
						GL11.glTexCoordPointer(2, 32, (FloatBuffer)var2);
					}

					GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
				}

				if(this.hasColor) {
					if(this.useVBO) {
						GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 32, 20L);
					} else {
						this.byteBuffer.position(20);
						GL11.glColorPointer(4, true, 32, this.byteBuffer);
					}

					GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
				}

				if(this.useVBO) {
					GL11.glVertexPointer(3, GL11.GL_FLOAT, 32, 0L);
				} else {
					var2.position(0);
					GL11.glVertexPointer(3, 32, (FloatBuffer)var2);
				}

				GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
				GL11.glDrawArrays(this.drawMode, 0, this.vertexCount); // 'first' must be 0
				GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
				if(this.hasTexture) {
					GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
				}

				if(this.hasColor) {
					GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
				}
			}

			this.reset();
		}
	}

	private void reset() {
		this.vertexCount = 0;
		this.byteBuffer.clear();
		this.rawBufferIndex = 0;
		this.addedVertices = 0;
	}

	public final void startDrawingQuads() {
		this.startDrawing(7);
	}

	public final void startDrawing(int var1) {
		if(this.isDrawing) {
			throw new IllegalStateException("Already tesselating!");
		} else {
			this.isDrawing = true;
			this.reset();
			this.drawMode = var1;
			this.hasColor = false;
			this.hasTexture = false;
			this.isColorDisabled = false;
		}
	}

	public final void setColorOpaque_F(float var1, float var2, float var3) {
		this.setColorOpaque((int)(var1 * 255.0F), (int)(var2 * 255.0F), (int)(var3 * 255.0F));
	}

	public final void setColorRGBA_F(float var1, float var2, float var3, float var4) {
		this.setColorRGBA((int)(var1 * 255.0F), (int)(var2 * 255.0F), (int)(var3 * 255.0F), (int)(var4 * 255.0F));
	}

	private void setColorOpaque(int var1, int var2, int var3) {
		this.setColorRGBA(var1, var2, var3, 255);
	}

	private void setColorRGBA(int var1, int var2, int var3, int var4) {
		if(!this.isColorDisabled) {
			if(var1 > 255) {
				var1 = 255;
			}

			if(var2 > 255) {
				var2 = 255;
			}

			if(var3 > 255) {
				var3 = 255;
			}

			if(var4 > 255) {
				var4 = 255;
			}

			if(var1 < 0) {
				var1 = 0;
			}

			if(var2 < 0) {
				var2 = 0;
			}

			if(var3 < 0) {
				var3 = 0;
			}

			if(var4 < 0) {
				var4 = 0;
			}

			this.hasColor = true;
			this.color = var4 << 24 | var3 << 16 | var2 << 8 | var1;
		}
	}

	public final void addVertexWithUV(float var1, float var2, float var3, float var4, float var5) {
		this.hasTexture = true;
		this.textureU = var4;
		this.textureV = var5;
		this.addVertex(var1, var2, var3);
	}

	public final void addVertex(float var1, float var2, float var3) {
		++this.addedVertices;
		if(this.hasTexture) {
			this.rawBuffer[this.rawBufferIndex + 3] = Float.floatToRawIntBits(this.textureU);
			this.rawBuffer[this.rawBufferIndex + 4] = Float.floatToRawIntBits(this.textureV);
		}

		if(this.hasColor) {
			this.rawBuffer[this.rawBufferIndex + 5] = this.color;
		}

		this.rawBuffer[this.rawBufferIndex] = Float.floatToRawIntBits(var1);
		this.rawBuffer[this.rawBufferIndex + 1] = Float.floatToRawIntBits(var2);
		this.rawBuffer[this.rawBufferIndex + 2] = Float.floatToRawIntBits(var3);
		this.rawBufferIndex += 8;
		++this.vertexCount;
		if(this.vertexCount % 4 == 0 && this.rawBufferIndex >= 2097120) {
			this.draw();
		}

	}

	public final void setColorOpaque_I(int var1) {
		int var2 = var1 >> 16 & 255;
		int var3 = var1 >> 8 & 255;
		var1 &= 255;
		this.setColorOpaque(var2, var3, var1);
	}

	public final void disableColor() {
		this.isColorDisabled = true;
	}

	public static void setNormal(float var0, float var1, float var2) {
		GL11.glNormal3f(var0, var1, var2);
	}
}
