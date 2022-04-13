# LWJGL 3 and vlcj 5 interop

This example plays a video through vlcj and lets you use it as a texuture in lwjgl.

The steps are:
> Initialize main glfw window.
> Initialize second hidden glfw window.
> Run the video through vlcj.
> Every frame copy the texture back from vlcj.
> Use the texture in your renderer.

# Double buffering

This approach can be made more responsive by using double buffering, but that would require more memory.
In any case, if you can afford it, double buffering is very much recommended. It lets you not block the main thread every time you copy the texture from vlcj.

