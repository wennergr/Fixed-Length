package com.github.atais.fixedlength

import com.github.atais.util.{Read, Write}
import shapeless.{::, Generic, HList, HNil}

trait Codec[A] extends Encoder[A] with Decoder[A] with Serializable

object Codec {

  def fixed[A: Read : Write](start: Int, end: Int, align: Alignment = Alignment.Left, padding: Char = ' '): Codec[A] = {

    new Codec[A] {
      override def decode(str: String): Either[Throwable, A] =
        Decoder.decode(str)(Decoder.fixed[A](start, end, align, padding))

      override def encode(obj: A): String =
        Encoder.encode(obj)(Encoder.fixed[A](start, end, align, padding))
    }
  }

  val hnilCodec = new Codec[HNil] {
    override def decode(str: String): Either[Throwable, HNil] = Decoder.hnilDecoder.decode(str)

    override def encode(obj: HNil): String = Encoder.hnilEncoder.encode(obj)
  }

  final implicit class HListCodecEnrichedWithHListSupport[L <: HList](val self: Codec[L]) extends Serializable {
    def <<:[B](bCodec: Codec[B]): Codec[B :: L] = new Codec[B :: L] {

      override def decode(str: String): Either[Throwable, ::[B, L]] = {
        for {
          a <- bCodec.decode(str).right
          b <- self.decode(str).right
        } yield a :: b
      }

      override def encode(obj: ::[B, L]): String =
        bCodec.encode(obj.head) + self.encode(obj.tail)
    }

    def as[B](implicit gen: Generic.Aux[B, L]): Codec[B] = new Codec[B] {
      override def decode(str: String): Either[Throwable, B] = {
        for {
          d <- self.decode(str).right
        } yield gen.from(d)
      }

      override def encode(obj: B): String = self.encode(gen.to(obj))
    }
  }

  final implicit class CodecEnrichedWithHListSupport[A](val self: Codec[A]) extends Serializable {
    def <<:[B](codecB: Codec[B]): Codec[B :: A :: HNil] = {

      val lastCodec = new Codec[A] {
        override def decode(str: String): Either[Throwable, A] = {
          self.extraCharsAfterEnd(str) match {
            case None => self.decode(str)
            case Some(extra) => Left(new LineLongerThanExpectedException(str, extra))
          }
        }

        override def encode(obj: A): String = self.encode(obj)
      }

      codecB <<: lastCodec <<: hnilCodec
    }
  }

}