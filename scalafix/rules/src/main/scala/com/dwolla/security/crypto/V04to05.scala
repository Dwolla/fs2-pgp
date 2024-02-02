package com.dwolla.security.crypto

import scalafix.v1.*

import scala.annotation.tailrec
import scala.meta.*
import scala.meta.inputs.Input.{File, VirtualFile}

class V04to05 extends SemanticRule("com.dwolla.security.crypto.V04to05") {

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      /*
       * `Crypto[F]` -> `Crypto.resource[F]`
       */
      case t@Term.ApplyType.After_4_6_0(Term.Name("CryptoAlg"), Type.ArgClause(List(Type.Name(name)))) if t.symbol.normalized.value == "com.dwolla.security.crypto.CryptoAlg." =>
        Patch.replaceTree(t, s"CryptoAlg.resource[$name]").atomic

      /*
       * `cryptoAlg.armor()` -> `cryptoAlg.armor`
       */
      case t@Term.Apply.After_4_6_0(Term.Select(Term.Name(name), Term.Name("armor")), Term.ArgClause(List(), None)) if t.symbol.normalized.value == "com.dwolla.security.crypto.CryptoAlg.armor." =>
        Patch.replaceTree(t, s"$name.armor").atomic

      /*
       * Rewrite the various named and optional parameters of the `encrypt` method to use
       * the `EncryptionConfig` object.
       */
      case t@Term.Apply.After_4_6_0(Term.Select(Term.Name(_), fun@Term.Name("encrypt")), argClause@Term.ArgClause(_, _)) if t.symbol.normalized.value == "com.dwolla.security.crypto.CryptoAlg.encrypt." =>
        try {
          argClause match {
            case Term.ArgClause(arguments@Term.Assign(_, _) :: _, None) => // encrypt(…) where all arguments are named parameters
              migrateEncrypt(argClause, fun, arguments, None, offset = 0)
            case Term.ArgClause(key :: additionalArguments, None) => // encrypt(key, …), i.e. where the unnamed first argument is the key
              migrateEncrypt(argClause, fun, additionalArguments, Some(key), offset = 1)
            case other =>
              throw pathedException(other)(s => new RuntimeException(s"Unexpected argument clause for encrypt at $s"))
          }
        } catch {
          case ex: NoSuchElementException if ex.getMessage == "key not found: key" =>
            throw pathedException(fun)(s => new RuntimeException(s"Key not found at $s"))
        }

      /*
       * `tagChunkSize(foo)` -> `ChunkSize(foo)`
       */
      case t@Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), _) if !isEncryptAParent(t) && t.symbol.normalized.value == "com.dwolla.security.crypto.package.tagChunkSize." =>
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
                             keyValue: Option[Term],
                             offset: Int,
                            )
                            (implicit doc: SemanticDocument): Patch = {
    val map = arguments.zipWithIndex.foldLeft(keyValue.map("key" -> _).toList) {
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
              case _ => throw pathedException(fun)(s => new RuntimeException(s"Unexpected signature ${info.signature} at $s"))
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

  private def pathedException(tree: Tree)
                             (f: String => Exception): Exception =
    tree.pos.input match {
      case File(path, _) =>
        f(s"$path:${tree.pos.startLine + 1}:${tree.pos.startColumn + 1}–${tree.pos.endLine + 1}:${tree.pos.endColumn + 1}")
      case VirtualFile(path, _) =>
        f(s"$path:${tree.pos.startLine + 1}:${tree.pos.startColumn + 1}–${tree.pos.endLine + 1}:${tree.pos.endColumn + 1}")
      case _ =>
        f(tree.pos.toString)
    }
}
