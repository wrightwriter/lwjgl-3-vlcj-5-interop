# LWJGL 3 and vlcj 5 interop

This example plays a video through vlcj and lets you use it as a texuture in lwjgl.
Based on the [caprica demo](https://github.com/caprica/vlcj-lwjgl-demo). The difference between the two demos is that that one draws the video to the main window and this one draws it to a texture. This gives a lot of flexibility as to how to approach displaying it.

For instance, you can display it on a quad in a 3d engine or composite multiple videos at once in a VJ tool.

# Process
> Initialize main glfw window.

> Initialize second hidden glfw window.

> Run the video through vlcj.

> Every frame copy the texture back from vlcj.

> Use the texture in your renderer.

# Double buffering

This approach can be made more responsive by using double buffering, but that would require more memory.
In any case, if you can afford it, double buffering is very much recommended. It lets you not block the main thread every time you copy the texture from vlcj.

