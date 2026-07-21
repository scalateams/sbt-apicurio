// Reference the plugin from the parent project
lazy val root   = Project("plugins", file(".")).dependsOn(plugin)
lazy val plugin = RootProject(file("..").getCanonicalFile.toURI)
