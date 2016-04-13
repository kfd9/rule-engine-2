package org.scalarules.engine

import org.scalacheck.{Arbitrary, Gen}
import org.scalarules.engine.FactEngineTestGlossary._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import DerivationTools._

class FactEngineTestComputeInputs extends FlatSpec with Matchers {

  val derivations = List(FactEngineTestValues.derivationOne, FactEngineTestValues.derivationTwo, FactEngineTestValues.derivationRedefiningOutput)

  it should "output all unique inputs" in {
    val resultingInputs: Set[Fact[Any]] = computeAllInputs(derivations)

    resultingInputs should have size 5
    resultingInputs should contain (PurchaseAmount)
    resultingInputs should contain (TaxationValue)
    resultingInputs should contain (Costs)
    resultingInputs should contain (GoodsValue)
    resultingInputs should contain (InterestValue)
  }

}

class FactEngineTestComputeOutputs extends FlatSpec with Matchers {

  val derivationsOK = List(FactEngineTestValues.derivationOne, FactEngineTestValues.derivationTwo)
  val derivationsNOK = List(FactEngineTestValues.derivationOne, FactEngineTestValues.derivationTwo, FactEngineTestValues.derivationRedefiningOutput)

  it should "output all outputs" in {
    val resultingOutputs: Set[Fact[Any]] = computeAllOutputs(derivationsOK)

    resultingOutputs should have size 2
    resultingOutputs should contain (BuildingValue)
    resultingOutputs should contain (ConstructionValue)
  }

  it should "detect a doubly defined output and break" in {
    intercept[IllegalStateException] {
      computeAllOutputs(derivationsNOK)
    }
  }

}

class FactEngineTestConstructGraph extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  it should "detect a direct loop between two derivations" in {
    intercept[IllegalStateException] {
      FactEngine.constructGraph(FactEngineTestValues.directLoopDefaultDerivations)
    }
  }

  it should "detect an indirect loop between three derivations" in {
    intercept[IllegalStateException] {
      FactEngine.constructGraph(FactEngineTestValues.indirectLoopDefaultDerivationsOrders.head)
    }
  }

  it should "parse a series of nodes correctly" in {
    implicit val arbitraryDefaultDerivations: Arbitrary[List[DefaultDerivation]] = Arbitrary(FactEngineTestValues.derivationsGeneration)

    forAll((derivations: List[DefaultDerivation]) => {
      val nodes = FactEngine.constructGraph(derivations)

      nodes should have size derivations.size
    })
  }

  it should "not allow DefaultDerivations with 0 inputs" in {
    intercept[IllegalArgumentException] {
      FactEngine.constructGraph(List(FactEngineTestValues.derivationWithoutInputs))
    }
  }

}

class FactEngineTestRunDefaultDerivations extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  def resolve[A](f: Fact[A], c: Context): A = f.toFunc(c).get

  val contextGenerator: Gen[Context] = for {
    purchaseAmount <- Gen.posNum[Long]
    taxationValue <- Gen.posNum[Long]
    costs <- Gen.posNum[Long]
  } yield Map(PurchaseAmount -> BigDecimal(purchaseAmount),
    TaxationValue -> BigDecimal(taxationValue),
    Costs -> BigDecimal(costs)
  )

  implicit val arbitraryContext: Arbitrary[Context] = Arbitrary(contextGenerator)

  it should "calculate values correctly when all input values are available" in {
    forAll( (c: Context) => {
      val result = FactEngine.runNormalDerivations(c, FactEngineTestValues.normalDefaultDerivations)

      result should have size (c.size + 3)
    } )
  }

}

class FactEngineTestRunSubRunDefaultDerivations extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  def resolve[A](f: Fact[A], c: Context): A = f.toFunc(c).get

  private val inputs: List[BigDecimal] = (for (input <- 1 to 5) yield BigDecimal(input)).toList
  private val context: Context = Map(SubRunInput -> inputs)

  it should "calculate values correctly when all input values are available" in {
    val result = FactEngine.runNormalDerivations(context, List(FactEngineTestValues.derivationSubRun))

    result should have size (context.size + 1)
    result(SubRunOutput) should equal (inputs.map(i => i + 10))
  }

}

object FactEngineTestValues {

  // --- DefaultDerivation generators to use in property based testing
  val derivationGeneratorLevelOne = for {
    inputs <- Gen.nonEmptyListOf(Gen.oneOf(PurchaseAmount, InterestValue, GoodsValue, Costs,
      BuildingCost, ConstructionCost, TaxationValue))
    output <- Gen.oneOf(MarketValueAdded, MarketValueReduced, BuildingValue, ConstructionValue)
  } yield DefaultDerivation(inputs, output, Conditions.trueCondition, new NoopEvaluation[BigDecimal])

  val derivationGenerationLevelTwo = for {
    inputs <- Gen.nonEmptyListOf(Gen.oneOf(MarketValueAdded, MarketValueReduced, BuildingValue,
      ConstructionValue))
    output <- Gen.oneOf(EstimatedNewValue, EstimatedValue, MarketValueAlternative)
  } yield DefaultDerivation(inputs, output, Conditions.trueCondition, new NoopEvaluation[BigDecimal])

  val derivationGenerationLevelThree = for {
    inputs <- Gen.nonEmptyListOf(Gen.oneOf(EstimatedNewValue, EstimatedValue,
      MarketValueAlternative))
    output <- Gen.oneOf(PropertyValue, MarketValue)
  } yield DefaultDerivation(inputs, output, Conditions.trueCondition, new NoopEvaluation[BigDecimal])

  val derivationsGeneration = for {
    levelOne <- derivationGeneratorLevelOne
    levelTwo <- derivationGenerationLevelTwo
    levelThree <- derivationGenerationLevelThree
  } yield List(levelOne, levelTwo, levelThree)

  // --- Three derivations to properly construct a graph
  val derivationOne = DefaultDerivation(input = List(PurchaseAmount, TaxationValue),
    output = BuildingValue,
    condition = Conditions.trueCondition,
    operation = new Evaluation[BigDecimal]{
      override def apply(c: Context): Option[BigDecimal] = Some(PurchaseAmount.toFunc(c).get + TaxationValue.toFunc(c).get)
    })

  val derivationTwo = DefaultDerivation(input = List(PurchaseAmount, Costs),
    output = ConstructionValue,
    condition = Conditions.trueCondition,
    operation = new Evaluation[BigDecimal]{
      override def apply(c: Context): Option[BigDecimal] = Some(PurchaseAmount.toFunc(c).get - TaxationValue.toFunc(c).get)
    })

  val derivationThree = DefaultDerivation(input = List(ConstructionValue, BuildingValue),
    output = MarketValue,
    condition = Conditions.trueCondition,
    operation = new Evaluation[BigDecimal]{
      override def apply(c: Context): Option[BigDecimal] = Some(ConstructionValue.toFunc(c).get.min(BuildingValue.toFunc(c).get))
    })

  val nodeThree = Node(derivationThree, List())
  val nodeTwo = Node(derivationTwo, List(nodeThree))
  val nodeOne = Node(derivationOne, List(nodeThree))

  val normalDefaultDerivations = List(derivationOne, derivationTwo, derivationThree)

  // --- DefaultDerivation that redefines the output of derivationTwo
  val derivationRedefiningOutput = DefaultDerivation(input = List(GoodsValue, InterestValue),
    output = ConstructionValue,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  // --- DefaultDerivation that has no inputs (currently unsupported)
  val derivationWithoutInputs = DefaultDerivation(input = List(),
    output = MarketValueAdded,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  // --- DefaultDerivations specifically designed to hold loops

  /* The Direct Loop DefaultDerivations model the situation where A => B and B => A. */
  val directLoopDefaultDerivationA = DefaultDerivation(input = List(PurchaseAmount),
    output = LumpSum,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  val directLoopDefaultDerivationB = DefaultDerivation(input = List(LumpSum),
    output = PurchaseAmount,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  val directLoopDefaultDerivations = List(directLoopDefaultDerivationA, directLoopDefaultDerivationB)

  /* The Indirect Loop DefaultDerivations model the situation where A => B, B => C and C => A. */
  val indirectLoopDefaultDerivationA = DefaultDerivation(input = List(PurchaseAmount),
    output = LumpSum,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  val indirectLoopDefaultDerivationB = DefaultDerivation(input = List(LumpSum),
    output = Costs,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  val indirectLoopDefaultDerivationC = DefaultDerivation(input = List(Costs),
    output = PurchaseAmount,
    condition = Conditions.trueCondition,
    operation = new NoopEvaluation[BigDecimal])

  val indirectLoopDefaultDerivationsOrders: List[List[DefaultDerivation]] = List(indirectLoopDefaultDerivationA, indirectLoopDefaultDerivationB, indirectLoopDefaultDerivationC)
    .permutations.toList

  val derivationSubRun = SubRunDerivation(inputs = List(SubRunInput),
    output = SubRunOutput,
    condition = Conditions.trueCondition,
    subRunData = new SubRunData[BigDecimal, BigDecimal] (
      contextAdditions = x => Map(SubRunIntermediateInput -> x),
      inputList = SubRunInput,
      yieldFact = SubRunIntermediateResult,
      derivations = List(DefaultDerivation(input = List(SubRunIntermediateInput),
          output = SubRunIntermediateResult,
          condition = Conditions.trueCondition,
          operation = new Evaluation[BigDecimal]{
            override def apply(c: Context): Option[BigDecimal] = SubRunIntermediateInput.toFunc(c).map(_ + 10)
          })
        )
    ))
}
