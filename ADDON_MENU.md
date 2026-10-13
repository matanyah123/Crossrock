# Add-ons discovery and resource reloads

Opening Add-ons reads only manifests (including pack settings), icons and the
English metadata language file. Discovery results are shared across screen
instances in a bounded cache, invalidated by metadata file size, modification
time or file identity. Unpacked pack assets are not traversed. Nested `.mcpack`
metadata is sought through the ZIP central directory rather than inflating each
asset; the enclosing nested archive must still be read on a cold cache miss.

Selection/configuration changes only mark a reload pending. Done applies them
through Minecraft's resource reload; closing an unchanged screen does nothing.
The virtual pack's primary/icon handle reads the existing snapshot. Only its full
resource handle generates translations and capability diagnostics. F3+T still
regenerates resources, even when metadata has not changed.

The Errors button displays the immutable diagnostics from the last resource
reload, not a newly generated audit. Direct UI operation failures (such as failing
to copy a dropped pack) remain local to the screen. Metadata discovery itself
does not write pack validation warnings to the log.

Folder watching covers pack/container directories and metadata language folders,
not entire asset trees. Use resource reload for edits inside unpacked asset
folders. Menu regression checks assert unchanged resource/error snapshot identity
and generation counts when opening and closing, including with a large local
reference pack. Repository tests cover cache invalidation and skipped/corrupt
unrelated nested assets.
