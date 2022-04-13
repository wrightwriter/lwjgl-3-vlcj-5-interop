# LWJGL 3 and vlcj 5 interop

This example plays a video through vlcj and lets you use it as a texuture in lwjgl.
Based on the [caprica demo](https://github.com/caprica/vlcj-lwjgl-demo). The difference between the two demos is that that one draws the video to the main window and this one draws it to a texture. 

This gives a lot of flexibility as to how to approach displaying it.
For instance, you can display it on a quad in a 3d engine or composite multiple videos at once in a VJ tool.
# Prerequisite 
Install vlc 4.0.0 as described in the [caprica demo](https://github.com/caprica/vlcj-lwjgl-demo).

Download the [MediaInfo](https://mediaarea.net/en/MediaInfo/Download/Windows) binaries to get the video metadata and resolution at runtime with the [vlcj-info](https://github.com/caprica/vlcj-info) bindings.

Set the jna library path, where the MediaInfo binaries are. This can be done through code: 
`System.setProperty("jna.library.path", "path-to-binaries");` or with this JRE command line: `-Djna.library.path=path-to-binaries`

# Process
> Initialize main  glfw window / gl context.

> Initialize second hidden glfw window / gl context.

> Run the video through vlcj.

> Every frame copy the texture back from vlcj to the secondary context.

> Use the texture in your renderer.

# Double buffering

This approach can be made more responsive by using double buffering, but that would require more memory.
In any case, if you can afford it, double buffering is very much recommended. It lets you not block the render thread every time you copy the texture from vlcj.

