package com.dwolla.security.crypto

import scalafix.v1.*

import scala.annotation.tailrec
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
      case t@Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), _) if !isEncryptAParent(t) =>
        Patch.replaceToken(t.tokens.head, "ChunkSize")
    }.asPatch

  @tailrec
  private def isEncryptAParent(t: Tree): Boolean =
    t.parent match {
      case Some(Term.Apply.After_4_6_0(Term.Select(_, Term.Name("encrypt")), _)) => true
      case Some(other) => isEncryptAParent(other)
      case _ => false
    }

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
        s :+ ("ChunkSize" -> term)
      case (s, (Term.Assign(Term.Name(name), term), _)) => s :+ (name.capitalize -> term)
      case (s, (value, i)) =>
        fun.symbol.info match {
          case Some(info) =>
            info.signature match {
              case method: MethodSignature if method.parameterLists.nonEmpty =>
                val parameter = method.parameterLists.head(i + offset)
                val parameterName = parameter.displayName

                s :+ (parameterName.capitalize -> value)
              case _ => throw new RuntimeException(s"Unexpected method signature ${info.signature} (how did the code being modified ever compile?)")
            }
          case _ => s
        }
      case (s, _) => s
    }

    Patch.replaceTree(t, sortKeyToEnd(map).foldLeft(s"(EncryptionConfig()") {
      case (s, ("key", term)) => s + s", $term)"
      case (s, (argName, term)) =>
        tagChunkSizeTermReplacement(term)
          .map(updatedChunkSize => s + s".with$argName($updatedChunkSize)")
          .getOrElse(s + s".with$argName($term)")
    })
  }

  private def tagChunkSizeTermReplacement(term: Term): Option[String] = {
    val chunkSizeTerms = term.collect {
      case t@Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), Term.ArgClause(List(_), _)) => t.pos
    }

    val updatedChunkSize =
      chunkSizeTerms
        .foldLeft(new StringBuilder) { case (acc, t) =>
          val tokens = term.tokens.toString()

          val replacementLength = "tagChunkSize".length
          val start = t.start - term.pos.start + replacementLength
          val end = tokens.length

          acc
            .append(tokens.slice(0, t.start - term.pos.start).mkString)
            .append("ChunkSize")
            .append(tokens.slice(start, end).mkString)
        }
        .toString()

    if (chunkSizeTerms.isEmpty) None
    else Some(updatedChunkSize)
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
