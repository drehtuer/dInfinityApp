# Release builds are minified and shrunk (see .claude/CLAUDE.md). Rules are
# added here as real code arrives; the defaults from
# proguard-android-optimize.txt cover the skeleton.

# Commons Compress names every compressor it *can* use, including several whose
# libraries are optional and are not on this app's classpath. R8 sees the
# references and stops; the classes are genuinely absent and genuinely never
# reached, because `SafeExtractor` reads tar, zip and gzip and nothing else
# (`docs/dice-sets.md`).
#
# Listed one by one rather than silenced with a wildcard over the whole
# library: a compressor that starts being referenced for some *other* reason
# should fail the build rather than disappear into a blanket rule.
-dontwarn com.github.luben.zstd.**
