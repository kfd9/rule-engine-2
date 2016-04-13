package org.scalarules

package object engine {

  type Context = Map[Fact[Any], Any]
  type Condition = Context => Boolean
  type Input = List[Fact[Any]]
  type Output = Fact[Any]
  type Level = List[Node]
  type Levels = List[Level]

}
