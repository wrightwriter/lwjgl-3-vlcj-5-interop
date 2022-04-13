import com.sun.jna.Pointer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL46;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoEngineVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.videoengine.VideoEngine;
import uk.co.caprica.vlcj.player.embedded.videosurface.videoengine.VideoEngineCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.videoengine.VideoEngineCallbackAdapter;
import uk.co.caprica.vlcjinfo.MediaInfo;
import uk.co.caprica.vlcjinfo.Section;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL45.glCheckNamedFramebufferStatus;

public class Video {
	// Do NOT use this framebuffer on another context.
	// Nvidia driver permits it, but it is undefined behaviour.
	// Use the texture below instead.
	private int framebuffer;

	// Shared texture, will be used in the rendering context.
	public int texture;

	// Media player can be used to control the video playback.
	private EmbeddedMediaPlayer mediaPlayer;
	private MediaPlayerFactory mediaPlayerFactory;
	private VideoEngineVideoSurface videoSurface;
	private VideoEngineCallback videoEngineCallback;

	// Semaphore for ownership of the glfw/lwjgl context.
	private final Semaphore contextSemaphore = new Semaphore(0);

	// Worker thread which copies the video texture from VLC.
	private final VideoManagerThread videoManagerThread;

	// Video resolution.
	public final Integer[] resolution = {0,0};

	// Lock is used to wait for the glfw/gl contexts to get initialized.
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition condInitialized = lock.newCondition();

	// The glfw context used by vlcj.
	private long glfwWindowVideo;
	// The glfw context used by the main thread.
	private final long glfwWindowMain;

	// Video mrl.
	private final String mrl;

	public Video(String mrl, Long glfwWindowMain){
		this.glfwWindowMain = glfwWindowMain;
		this.mrl = mrl;

		// Get file resolution.
		// Make sure to have set the jna library location.
		final Section mediaInfo = MediaInfo.mediaInfo(mrl).first("Video");
		resolution[0] = mediaInfo.integer("Width");
		resolution[1] = mediaInfo.integer("Height");

		// Context needs to be unbound to share resources another one.
		GLFW.glfwMakeContextCurrent(0L);

		videoManagerThread = new VideoManagerThread();

		// Wait for glfw/lwjgl context to get created.
		lock.lock();
		try{
			videoManagerThread.start();
			condInitialized.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}

		// Bind original context.
		GLFW.glfwMakeContextCurrent(glfwWindowMain);
		contextSemaphore.release();
	}

	public void destroy(){
		// We need to make sure the callbacks stop before we disappear, otherwise a fatal JVM crash may occur
		mediaPlayer.release();
		mediaPlayerFactory.release();

		// Free the window callbacks and destroy the window
		glfwFreeCallbacks(glfwWindowVideo);
		glfwDestroyWindow(glfwWindowVideo);
	}


	public void getCurrFrame()  {
		try {
			// Block the vlc glfw context.
			contextSemaphore.acquire();

			// glFinish() needs to be done when doing resource sharing between OpenGL contexts.
			// This is described in the OpenGL specification.
			GL46.glFinish();

			final long prevContext = glfwGetCurrentContext();
			glfwMakeContextCurrent(glfwWindowVideo);

			// Copy current frame from vlc to the framebuffer.
			GL46.glBlitNamedFramebuffer(
					0, framebuffer,
					0,0, resolution[0], resolution[1],
					0,0, resolution[0], resolution[1],
					GL11C.GL_COLOR_BUFFER_BIT, GL_NEAREST
			);

			// glFinish() again, because of aforementioned reason.
			GL46.glFinish();

			// Switch back to original context.
			// Release the vlc context.
			glfwMakeContextCurrent(prevContext);
			contextSemaphore.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}



	class VideoManagerThread extends Thread{
		VideoManagerThread(){
			// Set up GLFW and OpenGL hints.
			glfwDefaultWindowHints();
			glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
			glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
			glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
			glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
			glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

			// Create hidden window.
			glfwWindowVideo = glfwCreateWindow(resolution[0], resolution[1], "vlcj", 0L, glfwWindowMain);
		}
		@Override
		public synchronized void run(){
			// Initialize vlcj.
			videoEngineCallback = new VideoEngineHandler();
			mediaPlayerFactory = new MediaPlayerFactory("--quiet");
			mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
			videoSurface= mediaPlayerFactory.videoSurfaces().newVideoSurface(VideoEngine.libvlc_video_engine_opengl, videoEngineCallback);

			mediaPlayer.videoSurface().set(videoSurface);

			// Acquire the context from the managing thread.
			glfwMakeContextCurrent(glfwWindowVideo);

			// Disable vsync.
			glfwSwapInterval(0);

			// Initialize LWJGL context.
			GL.createCapabilities();

			// Create a framebuffer and texture to keep the video frame in.
			// This can be improved with double buffering.
			final int internalFormat = GL_RGBA8;
			framebuffer = GL46.glCreateFramebuffers();
			texture = GL46.glCreateTextures(GL_TEXTURE_2D);
			GL46.glTextureStorage2D(texture, 1, internalFormat, resolution[0], resolution[1]);
			GL46.glNamedFramebufferTexture(framebuffer, GL_COLOR_ATTACHMENT0, texture, 0);
			final int fbStatus = glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER);
			if(fbStatus != GL_FRAMEBUFFER_COMPLETE){
				throw new RuntimeException("Error: FB Incomplete");
			}

			// Set video playback settings.
			mediaPlayer.controls().setRepeat(true);
			mediaPlayer.audio().setMute(true);
			mediaPlayer.media().play(mrl);

			// Signal to calling thread that initilization is finished.
			lock.lock();
			try {
				condInitialized.signal();
			} finally {
				lock.unlock();
			}
		}

	}

	class VideoEngineHandler extends VideoEngineCallbackAdapter {
		@Override
		public void onSwap(Pointer opaque) {
			// No need to swap buffers, because the glfw window is hidden.
		}

		@Override
		public boolean onMakeCurrent(Pointer opaque, boolean enter) {
			if (enter) {
				try {
					contextSemaphore.acquire();
					glfwMakeContextCurrent(glfwWindowVideo);
				} catch (InterruptedException e) {
					return false;
				} catch (Exception e) {
					glfwMakeContextCurrent(0L);
					contextSemaphore.release();
					return false;
				}
			} else {
				try {
					glfwMakeContextCurrent(0L);
				} finally {
					contextSemaphore.release();
				}
			}
			return true;
		}

		@Override
		public long onGetProcAddress(Pointer opaque, String functionName) {
			return glfwGetProcAddress(functionName);
		}
	}

}
