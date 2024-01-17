package com.dwolla.security.crypto

import scalafix.v1.*
import scala.meta.*

class V04to05 extends SemanticRule("com.dwolla.security.crypto.V04to05") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
      case t@Term.ApplyType.After_4_6_0(Term.Name("CryptoAlg"), Type.ArgClause(List(Type.Name(name)))) =>
        Patch.replaceTree(t, s"CryptoAlg.resource[$name]").atomic
      case t@Term.Apply.After_4_6_0(Term.Select(Term.Name(name), Term.Name("armor")), Term.ArgClause(List(), None)) =>
        Patch.replaceTree(t, s"$name.armor").atomic
      case t@Term.Apply.After_4_6_0(Term.Name("tagChunkSize"), _) =>
        Patch.renameSymbol(t.symbol, "ChunkSize")
      case t@Term.Apply.After_4_6_0(
        Term.Name("PosInt"),
        Term.ArgClause(List(Lit.Int(posIntArg)), None)
      ) => Patch.replaceTree(t, s"PosInt.unsafeFrom($posIntArg)").atomic
      case t@
        Term.Apply.After_4_6_0(
          Term.Select(Term.Name(cryptoRName), Term.Name("encrypt")),
          Term.ArgClause(
            List(
              Term.Name(keyName),
              Term.Assign( Term.Name("fileName"), fileNameTerm )
            ),
            None
          )
        ) =>
        Patch.replaceTree(t, s"$cryptoRName.encrypt(EncryptionConfig().withFileName($fileNameTerm), $keyName)")
    }.asPatch
  }
}
