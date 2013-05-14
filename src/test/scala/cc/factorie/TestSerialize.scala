package cc.factorie

import app.chain.ChainModel
import app.nlp.ner.ChainNerLabel
import cc.factorie.la.{Tensors, Tensor, DenseTensor1, UniformTensor1}
import org.scalatest.junit.JUnitSuite
import org.junit.Test
import java.io._
import cc.factorie.app.nlp

class TestSerialize extends JUnitSuite  with cc.factorie.util.FastLogging{

  class MyChainNerFeatures(val token: nlp.Token, override val domain: CategoricalDimensionTensorDomain[String])
    extends BinaryFeatureVectorVariable[String] {
    override def skipNonCategories = true
  }

  class OntoNerLabel(t: nlp.Token, ta: String, override val domain: CategoricalDomain[String]) extends ChainNerLabel(t, ta) {
    type ContainedVariableType = this.type
  }

  @Test def testChainModelSerialization(): Unit = {

    val modelFile = java.io.File.createTempFile("FactorieTestFile", "serialize-chain-model").getAbsolutePath

    logger.debug("creating toy model with random weights")

    object MyChainNerFeaturesDomain extends CategoricalDimensionTensorDomain[String]
    MyChainNerFeaturesDomain.dimensionDomain ++= Seq("A","B","C")

    object OntoNerLabelDomain extends CategoricalDomain[String]
    OntoNerLabelDomain ++= Seq("Hello","GoodBye")

    val model = makeModel(MyChainNerFeaturesDomain, OntoNerLabelDomain)
    model.bias.weightsTensor.:=(Array.fill[Double](model.bias.weightsTensor.length)(random.nextDouble()))
    model.obs.weightsTensor.:=(Array.fill[Double](model.obs.weightsTensor.length)(random.nextDouble()))
    model.markov.weightsTensor.:=(Array.fill[Double](model.markov.weightsTensor.length)(random.nextDouble()))
    logger.debug("serializing chain model")
    model.serialize(modelFile)

    val deserialized = deserializeChainModel(modelFile)

    assertSameWeights(model, deserialized)

    logger.debug("successfully deserialized")
  }

  def getWeights(model: Model with Weights): Seq[Tensor] = model.weights.values.toSeq

  def assertSameWeights(model1: Model with Weights, model2: Model with Weights): Unit = {
    val weights1 = getWeights(model1)
    val weights2 = getWeights(model2)
    assert(weights1.size == weights2.size,
      "Number of families didn't match: model1 had %d, model2 had %d" format (weights1.size, weights2.size))
    for ((w1, w2) <- weights1.zip(weights2)) {
      logger.debug("# active elements in w1: " + w1.activeDomainSize)
      logger.debug("# active elements in w2: " + w2.activeDomainSize)
      assert(w1.activeDomainSize == w2.activeDomainSize)
      for (((a1, a2), (b1, b2)) <- w1.activeElements.toSeq.zip(w2.activeElements.toSeq)) {
        assert(a1 == b1, "Index %d from w1 not equal to %d from w2" format (a1, b1))
        assert(a2 == b2, "Value %f at index %d from w1 not equal to value %f at index %d from w2" format (a2, a1, b2, b1))
      }
    }
  }

  def makeModel(featuresDomain: CategoricalDimensionTensorDomain[String],
    labelDomain: CategoricalDomain[String]): ChainModel[OntoNerLabel, MyChainNerFeatures, nlp.Token] = {
    object model extends ChainModel[OntoNerLabel, MyChainNerFeatures, nlp.Token](
      labelDomain, featuresDomain, l => l.token.attr[MyChainNerFeatures], l => l.token, t => t.attr[OntoNerLabel])
    model.useObsMarkov = false
    model
  }

  def deserializeChainModel(fileName: String): ChainModel[OntoNerLabel, MyChainNerFeatures, nlp.Token] = {
    object MyChainNerFeaturesDomain extends CategoricalDimensionTensorDomain[String]
    object OntoNerLabelDomain extends CategoricalDomain[String]
    val model = makeModel(MyChainNerFeaturesDomain, OntoNerLabelDomain)
    model.deSerialize(fileName)
    model
  }

  @Test def testModelSerializationWithDomains(): Unit = {
    object domain1 extends CategoricalDomain[String]
    val words = "The quick brown fox jumped over the lazy dog".split(" ")
    words.foreach(domain1.index(_))

    class Model1(d: CategoricalDomain[String]) extends Model with Weights {
      object family1 extends DotFamilyWithStatistics1[CategoricalVariable[String]] {
        lazy val weightsTensor = new DenseTensor1(d.length)
      }
      def families = Seq(family1)
      lazy val weights = new Tensors(Seq((family1,family1.weightsTensor)))
      def factors(v: Var) = Nil
    }
    val model = new Model1(domain1)
    model.family1.weightsTensor(6) = 12

    val fileName1 = java.io.File.createTempFile("foo", "domain")
    val domainFile = new File(fileName1.getAbsolutePath)
    val domainCubbie = new CategoricalDomainCubbie(domain1)
    BinarySerializer.serialize(domainCubbie, domainFile)

    val fileName2 = java.io.File.createTempFile("foo", "model")
    val modelFile = new File(fileName2.getAbsolutePath)
    val modelCubbie = new WeightsCubbie(model)
    BinarySerializer.serialize(modelCubbie, modelFile)

    object domain2 extends CategoricalDomain[String]
    val model2 = new Model1(domain2)

    val domainFile2 = new File(fileName1.getAbsolutePath)
    val domainCubbie2 = new CategoricalDomainCubbie(domain2)
    BinarySerializer.deserialize(domainCubbie2, domainFile2)

    val modelFile2 = new File(fileName2.getAbsolutePath)
    val modelCubbie2 = new WeightsCubbie(model2)
    BinarySerializer.deserialize(modelCubbie2, modelFile2)

    assertSameWeights(model, model2)
  }

  @Test def testMultipleSerialization(): Unit = {
    val file = java.io.File.createTempFile("foo", "multi")
    object MyChainNerFeaturesDomain extends CategoricalDimensionTensorDomain[String]
    MyChainNerFeaturesDomain.dimensionDomain ++= Seq("A","B","C")

    object OntoNerLabelDomain extends CategoricalDomain[String]
    OntoNerLabelDomain ++= Seq("Hello","GoodBye")

    val model = makeModel(MyChainNerFeaturesDomain, OntoNerLabelDomain)
    model.bias.weightsTensor.:=(Array.fill[Double](model.bias.weightsTensor.length)(random.nextDouble()))
    model.obs.weightsTensor.:=(Array.fill[Double](model.obs.weightsTensor.length)(random.nextDouble()))
    model.markov.weightsTensor.:=(Array.fill[Double](model.markov.weightsTensor.length)(random.nextDouble()))

    BinarySerializer.serialize(MyChainNerFeaturesDomain, OntoNerLabelDomain, model, file)

    object featDomain2 extends CategoricalDimensionTensorDomain[String]
    object labelDomain2 extends CategoricalDomain[String]
    val model2 = makeModel(featDomain2, labelDomain2)

    BinarySerializer.deserialize(featDomain2, labelDomain2, model2,  file)

    assertSameWeights(model2, model)
  }

  // NOTE: this is a hack to get around broken Manifest <:< for singleton types
  // this is fixed in 2.10 so once we upgrade we can remove this hack (that assumes all params are covariant!)
  def checkCompat(m1: Manifest[_], m2: Manifest[_]): Boolean =
    m2.erasure.isAssignableFrom(m1.erasure) && (m1.typeArguments.zip(m2.typeArguments).forall({case (l,r) => checkCompat(l, r)}))

  @Test def testClassifierPosSerialization() {
    val model = new app.nlp.pos.POS3
    val fileName = java.io.File.createTempFile("FactorieTestFile", "classifier-pos").getAbsolutePath
    model.serialize(fileName)
    val otherModel = app.nlp.pos.POS3.fromFilename(fileName)
  }

  @Test def testInstanceSerialize(): Unit = {
    import app.classify._
    val fileName = java.io.File.createTempFile("FactorieTestFile", "serialize-instances").getAbsolutePath
    val ll = new LabelList[app.classify.Label, Features](_.features)
    val labelDomain = new CategoricalDomain[String] { }
    val featuresDomain = new CategoricalDimensionTensorDomain[String] { }
    for (i <- 1 to 100) {
      val labelName = (i % 2).toString
      val features = new BinaryFeatures(labelName, i.toString, featuresDomain, labelDomain)
      (1 to 100).shuffle.take(50).map(_.toString).foreach(features +=)
      ll += new app.classify.Label(labelName, features, labelDomain)
    }
    val llFile = new File(fileName)
    val llCubbie = new LabelListCubbie(featuresDomain, labelDomain, true)
    llCubbie.store(ll)
    BinarySerializer.serialize(llCubbie, llFile)

    val newllCubbie = new LabelListCubbie(featuresDomain, labelDomain, true)
    BinarySerializer.deserialize(newllCubbie, llFile)
    val newll = newllCubbie.fetch()

    assert(newll.zip(ll).forall({
      case (newl, oldl) =>
        newl.labelName == oldl.labelName &&
        newl.features.tensor.activeElements.sameElements(oldl.features.tensor.activeElements)
    }))
  }

  @Test def test(): Unit = {
    val fileName = java.io.File.createTempFile("FactorieTestFile", "serialize-model").getAbsolutePath
    val fileName2 = java.io.File.createTempFile("FactorieTestFile", "serialize-domain").getAbsolutePath
    // Read data and create Variables
    val sentences = for (string <- data.toList) yield {
      val sentence = new Sentence
      var beginword = true
      for (c <- string.toLowerCase) {
        if (c >= 'a' && c <= 'z') {
          sentence += new Token(c, beginword)
          beginword = false
        } else
          beginword = true
      }
      for (token <- sentence.links) {
        if (token.hasPrev) token += (token.prev.char + "@-1") else token += "START@-1"
        if (token.hasNext) token += (token.next.char + "@1") else token += "END@+1"
      }
      sentence
    }
    logger.debug("TokenDomain.dimensionDomain.size=" + TokenDomain.dimensionDomain.size)

    val model = new SegmenterModel
    model.bias.weightsTensor += new UniformTensor1(model.bias.weightsTensor.dim1, 1.0)
    model.obs.weightsTensor += new la.UniformTensor2(model.obs.weightsTensor.dim1, model.obs.weightsTensor.dim2, 1.0)

    val modelFile = new File(fileName)

    BinarySerializer.serialize(new WeightsCubbie(model), modelFile)

    val deserializedModel = new SegmenterModel
    BinarySerializer.deserialize(new WeightsCubbie(deserializedModel), modelFile)

    val domainFile = new File(fileName2)

    BinarySerializer.serialize(new CategoricalDimensionTensorDomainCubbie(TokenDomain), domainFile)

    logger.debug("Original model family weights: ")
    getWeights(model).foreach(s => logger.debug(s.toString))
    logger.debug("Deserialized model family weights: ")
    getWeights(deserializedModel).foreach(s => logger.debug(s.toString))

    assertSameWeights(model, deserializedModel)

    logger.debug("Original domain:")
    logger.debug(TokenDomain.dimensionDomain.toSeq.mkString(","))
    logger.debug("Deserialized domain:")
    val newDomain = new CategoricalDimensionTensorDomain[String] { }
    val cubbie = new CategoricalDimensionTensorDomainCubbie(newDomain)
    BinarySerializer.deserialize(cubbie, domainFile)
    logger.debug(newDomain.dimensionDomain.toSeq.mkString(","))

    assert(TokenDomain.dimensionDomain.toSeq.map(_.category).sameElements(newDomain.dimensionDomain.toSeq.map(_.category)))
  }

  class Label(b: Boolean, val token: Token) extends LabeledBooleanVariable(b)
  object TokenDomain extends CategoricalDimensionTensorDomain[String]
  class Token(val char: Char, isWordStart: Boolean) extends BinaryFeatureVectorVariable[String] with ChainLink[Token, Sentence] {
    def domain = TokenDomain
    val label = new Label(isWordStart, this)
    this += char.toString
    if ("aeiou".contains(char)) this += "VOWEL"
  }
  class Sentence extends Chain[Sentence, Token]

  class SegmenterModel extends ModelWithContext[Seq[Label]] with Weights {
    object bias extends DotFamilyWithStatistics1[Label] {
      factorName = "Label"
      lazy val weightsTensor = new la.DenseTensor1(BooleanDomain.size)
    }
    object obs extends DotFamilyWithStatistics2[Label, Token] {
      factorName = "Label,Token"
      lazy val weightsTensor = new la.DenseTensor2(BooleanDomain.size, TokenDomain.dimensionSize)
    }
    def families: Seq[Family] = Seq(bias, obs)
    lazy val weights = new Tensors(Seq((bias,bias.weightsTensor), (obs,obs.weightsTensor)))
    def factorsWithContext(label: Seq[Label]): Iterable[Factor] = {
      Seq.empty[Factor]
    }
    def factors(v:Var) = throw new Error("Not yet implemented.")
  }

  val data = Array(
    "Free software is a matter of the users' freedom to run, copy, distribute, study, change and improve the software. More precisely, it refers to four kinds of freedom, for the users of the software.",
    "The freedom to run the program, for any purpose.",
    "The freedom to study how the program works, and adapt it to your needs.",
    "The freedom to redistribute copies so you can help your neighbor.",
    "The freedom to improve the program, and release your improvements to the public, so that the whole community benefits.",
    "A program is free software if users have all of these freedoms. Thus, you should be free to redistribute copies, either with or without modifications, either gratis or charging a fee for distribution, to anyone anywhere. Being free to do these things means (among other things) that you do not have to ask or pay for permission.",
    "You should also have the freedom to make modifications and use them privately in your own work or play, without even mentioning that they exist. If you do publish your changes, you should not be required to notify anyone in particular, or in any particular way.",
    "In order for the freedoms to make changes, and to publish improved versions, to be meaningful, you must have access to the source code of the program. Therefore, accessibility of source code is a necessary condition for free software.",
    "Finally, note that criteria such as those stated in this free software definition require careful thought for their interpretation. To decide whether a specific software license qualifies as a free software license, we judge it based on these criteria to determine whether it fits their spirit as well as the precise words. If a license includes unconscionable restrictions, we reject it, even if we did not anticipate the issue in these criteria. Sometimes a license requirement raises an issue that calls for extensive thought, including discussions with a lawyer, before we can decide if the requirement is acceptable. When we reach a conclusion about a new issue, we often update these criteria to make it easier to see why certain licenses do or don't qualify.",
    "In order for these freedoms to be real, they must be irrevocable as long as you do nothing wrong; if the developer of the software has the power to revoke the license, without your doing anything to give cause, the software is not free.",
    "However, certain kinds of rules about the manner of distributing free software are acceptable, when they don't conflict with the central freedoms. For example, copyleft (very simply stated) is the rule that when redistributing the program, you cannot add restrictions to deny other people the central freedoms. This rule does not conflict with the central freedoms; rather it protects them.",
    "Thus, you may have paid money to get copies of free software, or you may have obtained copies at no charge. But regardless of how you got your copies, you always have the freedom to copy and change the software, even to sell copies.",
    "Rules about how to package a modified version are acceptable, if they don't effectively block your freedom to release modified versions. Rules that ``if you make the program available in this way, you must make it available in that way also'' can be acceptable too, on the same condition. (Note that such a rule still leaves you the choice of whether to publish the program or not.) It is also acceptable for the license to require that, if you have distributed a modified version and a previous developer asks for a copy of it, you must send one.",
    "Sometimes government export control regulations and trade sanctions can constrain your freedom to distribute copies of programs internationally. Software developers do not have the power to eliminate or override these restrictions, but what they can and must do is refuse to impose them as conditions of use of the program. In this way, the restrictions will not affect activities and people outside the jurisdictions of these governments.",
    "Finally, note that criteria such as those stated in this free software definition require careful thought for their interpretation. To decide whether a specific software license qualifies as a free software license, we judge it based on these criteria to determine whether it fits their spirit as well as the precise words. If a license includes unconscionable restrictions, we reject it, even if we did not anticipate the issue in these criteria. Sometimes a license requirement raises an issue that calls for extensive thought, including discussions with a lawyer, before we can decide if the requirement is acceptable. When we reach a conclusion about a new issue, we often update these criteria to make it easier to see why certain licenses do or don't qualify.",
    "The GNU Project was launched in 1984 to develop a complete Unix-like operating system which is free software: the GNU system.")

}