# Shared resources

`branding/` contains the original PlateLab icon and design notes. macOS packaging creates its `.icns` from this source; Android keeps its required launcher resolutions in its own `app/src/main/res/` directory.

There is no shared runtime code at present. Apple Vision/Swift and ML Kit/Java are implemented and tested in their respective platform folders.
