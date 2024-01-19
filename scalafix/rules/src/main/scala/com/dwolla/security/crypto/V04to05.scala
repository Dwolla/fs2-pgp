package com.dwolla.security.crypto

import scalafix.v1.*

import scala.meta.*

class V04to05 extends SemanticRule("com.dwolla.security.crypto.V04to05") {

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case t@Term.ApplyType.After_4_6_0(Term.Name("CryptoAlg"), Type.ArgClause(List(Type.Name(name)))) =>
        Patch.replaceTree(t, s"CryptoAlg.resource[$name]").atomic
      case t@Term.Apply.After_4_6_0(Term.Select(Term.Name(name), Term.Name("armor")), Term.ArgClause(List(), None)) =>
        Patch.replaceTree(t, s"$name.armor").atomic
      case Term.Apply.After_4_6_0(Term.Select(Term.Name(_), fun@Term.Name("encrypt")), t@Term.ArgClause((keyName@Term.Name(_)) :: additionalArguments, None)) =>
        migrateEncrypt(t, fun, additionalArguments, Some(keyName), offset = 1)
      case Term.Apply.After_4_6_0(Term.Select(Term.Name(_), fun@Term.Name("encrypt")), t@Term.ArgClause(arguments, None)) =>
        migrateEncrypt(t, fun, arguments, None, offset = 0)
//      case t@Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), _) =>
//        Patch.replaceToken(t.tokens.head, "ChunkSizeFromFix")
    }.asPatch

  private def migrateEncrypt(t: Term.ArgClause,
                             fun: Term.Name,
                             arguments: List[Term],
                             keyName: Option[Term],
                             offset: Int,
                            )
                            (implicit doc: SemanticDocument): Patch = {
    val map = arguments.zipWithIndex.foldLeft(keyName.map("key" -> _).toList) {
      case (s, (Term.Assign(Term.Name("key"), term), _)) => s :+ ("key" -> term)
      case (s, (Term.Assign(Term.Name("chunkSize"), Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), Term.ArgClause(List(term), _))), _)) =>
        s :+ ("ChunkSizeFromMigrateEncrypt" -> term)
      case (s, (Term.Assign(Term.Name(name), term), _)) => s :+ (name.capitalize -> term)
      case (s, (value, i)) =>
        fun.symbol.info match {
          case Some(info) =>
            info.signature match {
              case method: MethodSignature if method.parameterLists.nonEmpty =>
                val parameter = method.parameterLists.head(i + offset)
                val parameterName = parameter.displayName

                s :+ (parameterName.capitalize -> value)
            }
          case _ => s
        }
      case (s, _) => s
    }

// TODO maybe try replacing the specific arguments instead of the entire tree?
//    import scala.meta.tokens.Token.{Comma, Space}
//    Patch.addLeft(arguments.head, "EncryptionConfig()") +
//      sortKeyToEnd(map)
//        .map {
//          case ("key", t) => Patch.removeTokens(t.tokens)
//          case (s, arg) =>
//            t.tokens.foreach(t => println(s"${t.getClass} -> $t"))
//            Patch.addAround(arg, s".with$s(", ")")
//        }
//        .asPatch +
//      Patch.removeTokens(t.tokens.filter(_.is[Comma])) +
//      Patch.addRight(arguments.last, s", ${map.toMap.apply("key")}")

    Patch.replaceTree(t, sortKeyToEnd(map).foldLeft(s"(EncryptionConfig()") {
      case (s, ("key", term)) => s + s", $term)"
      case (s, (argName, Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), Term.ArgClause(List(term), _)))) => s + s".with$argName(ChunkSize($term))"
      case (s, (argName, term)) => s + s".with$argName($term)"
    })
  }

  private def sortKeyToEnd(terms: List[(String, Term)]): List[(String, Term)] = {
    val key = terms.toMap.apply("key")

    val remainder = terms
      .filterNot {
        case ("key", _) => true
        case _ => false
      }

    remainder :+ ("key" -> key)
  }


}
