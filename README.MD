
![Mixin Logo](docs/javadoc/resources/logo.png)

**CleanMix** is a fork of **Fabric Mixin** which is a more cutting-edge fork of **SpongePowered Mixin**.

**CleanMix** aims to target more specifically 1.12.2 environments, for Forge (with MixinBooter) and Cleanroom natively.

**Mixin** itself is a trait/mixin framework for Java using [ASM](https://asm.ow2.io/)
and hooking into the runtime classloading process via a set of pluggable
built-in or user-provided services.

### Documentation

The main documentation for **Mixin** can be found in the
[Wiki](https://github.com/SpongePowered/Mixin/wiki).

Additional documentation for individual features and annotations can be found in
the extensive [Javadoc](http://jenkins.liteloader.com/job/Mixin/javadoc/). For
additional help, feel free to ask on the [`#general-dev` channel on the CleanroomMC Discord](https://discord.gg/f2K4aSpG4F).

### Binaries

CleanMix will be available via Maven soon:

* https://repo.cleanroommc.com/#/releases - Viewable repository
* https://maven.cleanroommc.com - Maven endpoint

### Tooling

For handling obfuscation tasks, Mixin provides an [Annotation Processor](https://github.com/SpongePowered/Mixin/wiki/Using-the-Mixin-Annotation-Processor)
which works at compile time to generate obfuscation mappings for your toolchain
to apply. If using Gradle 5 or later, annotation processors are no longer
automatically loaded from `compile` configurations and must be specified
explicitly via `annotationProcessor` configurations. For this purpose, Mixin
provides "fat jar" artefacts containing all required dependencies via the
`:processor` classifier. For example if your build uses the dependency
`com.cleanroommc:cleanmix:1.2.3` then your annotationProcessor configuration
should specify dependency `com.cleanroommc:cleanmix:1.2.3:processor`.

### Integration with Eclipse

When developing using **Mixin**, you can use the **Mixin Annotation Processor**
within Eclipse to provide context-sensitive errors and warnings to help you more
easily troubleshoot your mixins. To do so:

1. Run the `gradle build` command to generate the mixin jar
2. Open the properties of your eclipse project and navigate to `Java Compiler`
  -> `Annotation Processing` -> `Factory Path`  
3. Check the `Enable project specific settings` checkbox
4. Click the `Add External JARs` button and select the generated mixin jar with
 the suffix **-processor** (hint: it should be in `Mixin/build/libs`)
5. Navigate up one level to `Java Compiler` -> `Annotation Processing`
6. Check the `Enable project specific settings` checkbox
7. Check the `Enable annotation processing` checkbox
8. Click the `New...` button next to the `Processor options` box
 * Set `Key` to **reobfSrgFile**
 * Set `Value` to the fully-qualified path to the `mcp-srg.srg` file (the
   location of the mapping file varies by platform, if you are unsure where to
   find it please follow the discord link below). 
9. Click `OK` to apply the changes

### Integration with IntelliJ IDEA

Enhanced functionality for working with **Mixin** in IntelliJ IDEA is available
via the [Minecraft Development for IntelliJ IDEA](https://plugins.jetbrains.com/idea/plugin/8327)
plugin developed by [DemonWav](https://github.com/demonwav).  
