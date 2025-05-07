lazy val `fs2-pgp-root` = (project in file("."))
  .settings(
    publish / skip := true,
    publishArtifact := false,
  )
  .enablePlugins(BouncyCastlePlugin)
  .aggregate(allProjects.map(_.project) *)
