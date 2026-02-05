package net.classicremastered.minecraft.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import net.classicremastered.minecraft.GameSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// TODO.
public final class SoundPlayer implements Runnable {

   public boolean running = false;
   public SourceDataLine dataLine;
   private List audioQueue = new ArrayList();
   public GameSettings settings;


   public SoundPlayer(GameSettings var1) {
      this.settings = var1;
   }

   public final void play(Audio var1) {
      if(this.running) {
         List var2 = this.audioQueue;
         synchronized(this.audioQueue) {
            this.audioQueue.add(var1);
         }
      }
   }

   private Thread thread;

   /** Open a 44.1kHz, 16-bit, stereo, BIG-ENDIAN PCM line (matches your byte order). */
   public void openLine() throws LineUnavailableException {
       if (dataLine != null && dataLine.isOpen()) return;
       AudioFormat fmt = new AudioFormat(
               /* sampleRate   */ 44100f,
               /* sampleSize   */ 16,
               /* channels     */ 2,
               /* signed       */ true,
               /* bigEndian    */ true   // IMPORTANT: you write (hi,lo) in run()
       );
       DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
       dataLine = (SourceDataLine) AudioSystem.getLine(info);
       dataLine.open(fmt, 17640 * 4); // ~0.4s buffer; bigger avoids underruns
   }

   public void startAudio() {
       if (running) return;
       try {
           openLine();
       } catch (LineUnavailableException e) {
           e.printStackTrace();
           return;
       }
       dataLine.start();
       running = true;
       thread = new Thread(this, "SoundPlayer");
       thread.setDaemon(true);
       thread.start();
   }

   // (optional) quick sanity beep so you know the path is alive
   public void debugBeep() {
       final int frames = 44100 / 1; // 0.25s
       final int[] L = new int[frames], R = new int[frames];
       final double w = 2*Math.PI*440/44100.0;
       for (int i=0;i<frames;i++) { int s=(int)(Math.sin(i*w)*8000); L[i]=s; R[i]=s; }

   }

   public void stopAudio() {
       running = false;
       if (thread != null) try { thread.join(200); } catch (InterruptedException ignored) {}
       if (dataLine != null) {
           try { dataLine.drain(); } catch (Exception ignored) {}
           try { dataLine.stop();  } catch (Exception ignored) {}
           try { dataLine.close(); } catch (Exception ignored) {}
       }
       dataLine = null;
   }

   // …keep your existing run(), play(), etc.


   public final void run() {
      int[] var1 = new int[4410];
      int[] var2 = new int[4410];

      for(byte[] var3 = new byte[17640]; this.running; this.dataLine.write(var3, 0, 17640)) {
         try {
            Thread.sleep(1L);
         } catch (InterruptedException var10) {
            var10.printStackTrace();
         }

         Arrays.fill(var1, 0, 4410, 0);
         Arrays.fill(var2, 0, 4410, 0);
         boolean var4 = true;
         int[] var5 = var2;
         int[] var6 = var1;
         List var12 = this.audioQueue;
         List var7 = this.audioQueue;
         synchronized(this.audioQueue) {
            int var8 = 0;

            while(true) {
               if(var8 >= var12.size()) {
                  break;
               }

 

               ++var8;
            }
         }

         int var13;
 
      }

   }
}
