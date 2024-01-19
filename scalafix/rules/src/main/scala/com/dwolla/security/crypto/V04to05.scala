package com.dwolla.security.crypto

import scalafix.v1.*

import scala.meta.*

class V04to05 extends SemanticRule("com.dwolla.security.crypto.V04to05") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    Patch.replaceSymbols(
      "com.dwolla.security.crypto.tagChunkSize" -> "com.dwolla.security.crypto.ChunkSize"
    ) ++
    doc.tree.collect {
      case t@Term.ApplyType.After_4_6_0(Term.Name("CryptoAlg"), Type.ArgClause(List(Type.Name(name)))) =>
        Patch.replaceTree(t, s"CryptoAlg.resource[$name]").atomic
      case t@Term.Apply.After_4_6_0(Term.Select(Term.Name(name), Term.Name("armor")), Term.ArgClause(List(), None)) =>
        Patch.replaceTree(t, s"$name.armor").atomic
      case t@
        Term.Apply.After_4_6_0(
          Term.Select(Term.Name(cryptoRName), fun@Term.Name("encrypt")),
            Term.ArgClause(
            (keyName@Term.Name(_)) :: additionalArguments,
            None
          )
        ) => migrateEncrypt(t, fun, additionalArguments, cryptoRName, Some(keyName), offset = 1)
      case t@Term.Apply.After_4_6_0(
        Term.Select(Term.Name(cryptoRName), fun@Term.Name("encrypt")),
        Term.ArgClause(arguments, None)
      ) => migrateEncrypt(t, fun, arguments, cryptoRName, None, offset = 0)
    }
  }

  private def migrateEncrypt(t: Term,
                             fun: Term.Name,
                             arguments: List[Term],
                             algName: String,
                             keyName: Option[Term],
                             offset: Int,
                            )
                            (implicit doc: SemanticDocument): Patch = {
    val map = arguments.zipWithIndex.foldLeft(keyName.map("key" -> _).toList) {
      case (s, (Term.Assign(Term.Name("key"), term), _)) => s :+ ("key" -> term)
      case (s, (Term.Assign(Term.Name("fileName"), term), _)) => s :+ ("FileName" -> term)
      case (s, (Term.Assign(Term.Name("chunkSize"), term), _)) => s :+ ("ChunkSize" -> term)
      case (s, (Term.Assign(Term.Name("encryption"), term), _)) => s :+ ("Encryption" -> term)
      case (s, (Term.Assign(Term.Name("compression"), term), _)) => s :+ ("Compression" -> term)
      case (s, (Term.Assign(Term.Name("packetFormat"), term), _)) => s :+ ("PacketFormat" -> term)
      case (s, (Term.Assign(Term.Name("packetFormat"), term), _)) => s :+ ("PacketFormat" -> term)
      case (s, (value, i)) =>
        fun.symbol.info match {
          case Some(info) =>
            info.signature match {
              case method: MethodSignature if method.parameterLists.nonEmpty =>
                val parameter = method.parameterLists.head(i + offset)
                val parameterName = parameter.displayName

                s :+ (parameterName.capitalize -> value)
              case _ => s
            }
          case _ => s
        }
      case (s, _) => s
    }

    val key = map.toMap.apply("key")

    Patch.replaceTree(t, map.filterNot {
      case ("key", _) => true
      case _ => false
    }.foldLeft(s"$algName.encrypt(EncryptionConfig()") {
      case (s, (argName, term)) => s + s".with$argName($term)"
    } + s", $key)")
  }

}
