import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING;

public class Engine {
	public static void main(String args[]){
		Engine engine = new Engine();
		engine.start();
	}

	// The main glfw window.
	private Long glfwWindow;

	// The video.
	private final Video video;


	public Engine(){
		initGlfwAndLwjgl();

		// Load a video.
		video = new Video("D:\\video.mp4", glfwWindow);
	}


	private void start(){
		// Set up a simple textured fullscreen quad to display the video on.
		setupTexturedFullScreenQuad();


		while(!GLFW.glfwWindowShouldClose(glfwWindow)){
			// Draw the video as a textured quad.
			GL46.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4 );

			GLFW.glfwSwapBuffers(glfwWindow);
			GLFW.glfwPollEvents();

			// Get the current video frame as texture from vlcj.
			video.getCurrFrame();
		}

	}

	private void initGlfwAndLwjgl(){
		if(!GLFW.glfwInit()) { throw new RuntimeException ("Unable to initialize GLFW");  }

		// Set the GL version  and window hints.
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

		// Create window.
		glfwWindow = GLFW.glfwCreateWindow(800,800, "lwjgl-vlcj", 0L,0L);
		if(glfwWindow == 0L)
			throw new RuntimeException("Failed to create GLFW window.");

		// Make window and context current.
		GLFW.glfwShowWindow(glfwWindow);
		GLFW.glfwMakeContextCurrent(glfwWindow);

		// Init lwjgl.
		GL.createCapabilities();

		// Create dummy VAO.
		GL46.glBindVertexArray(GL46.glCreateVertexArrays());
	}

	private void setupTexturedFullScreenQuad(){
		// Create fullscreen quad VBO.
		final float[] quadTriangleStrip = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
		final int vbo = GL46.glCreateBuffers();
		final int vao = GL46.glGetInteger(GL_VERTEX_ARRAY_BINDING);
		GL46.glNamedBufferStorage(vbo,quadTriangleStrip, GL44.GL_DYNAMIC_STORAGE_BIT);
		GL46.glVertexArrayVertexBuffer(vao, 0, vbo, 0, 2 * 4);
		GL46.glEnableVertexArrayAttrib( vao, 0);
		GL46.glVertexArrayAttribFormat( vao, 0, 2, GL_FLOAT, false,0 );
		GL46.glVertexArrayAttribBinding(vao,0, 0);

		// Create shader to display the texture.
		final String vertShaderString =
			"#version 430 \n" +
			"in vec2 p; " +
			"out vec2 vUv; " +
			"void main(){ " +
				"gl_Position = vec4(p,0,1); " +
				"vUv = (p.xy + 1.)/2.; " +
			"}" ;
		final String fragShaderString =
			"#version 430 \n" +
			"in vec2 vUv;" +
			"uniform sampler2D tex;" +
			"out vec4 C;" +
			"void main(){" +
				"C = texture(tex, vUv);" +
			"}" ;

		final int fragPid = GL46.glCreateShader(GL_FRAGMENT_SHADER);
		final int vertPid = GL46.glCreateShader(GL_VERTEX_SHADER);

		GL46.glShaderSource(fragPid, fragShaderString);
		GL46.glShaderSource(vertPid, vertShaderString);

		GL46.glCompileShader(fragPid);
		GL46.glCompileShader(vertPid);

		final int shaderProgram = glCreateProgram();
		GL46.glAttachShader(shaderProgram, fragPid );
		GL46.glAttachShader(shaderProgram, vertPid );

		GL46.glLinkProgram(shaderProgram);

		GL46.glUseProgram(shaderProgram);

		// Bind video texture to texture unit 0.
		GL46.glBindTextureUnit(0, video.texture);
		GL46.glUniform1i(GL46.glGetUniformLocation(shaderProgram, "tex"),0);
	}
}
