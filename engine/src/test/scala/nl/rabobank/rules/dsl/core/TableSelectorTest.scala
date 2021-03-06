package nl.rabobank.rules.dsl.core

import nl.rabobank.rules.dsl.core.TableSelectorGlossary._
import nl.rabobank.rules.dsl.nl.grammar.Table
import nl.rabobank.rules.utils.InternalBerekeningenTester

class TableSelectorTest extends InternalBerekeningenTester(new TableSelectorBerekening) {

  val simpleTable = new Table[String, Int, Int] {
    override def get(x: Int, y: Int): String = "Hello World"
  }

  test("eenvoudige Table Test") gegeven (
    IndexX is 1,
    IndexY is 1,
    TableFact is simpleTable
  ) verwacht (
    ResultString is "Hello World"
  )

}
