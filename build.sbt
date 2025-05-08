lazy val `fs2-pgp-root` = (project in file("."))
  .enablePlugins(BouncyCastlePlugin, NoPublishPlugin)
  .aggregate(allProjects.map(_.project) *)
