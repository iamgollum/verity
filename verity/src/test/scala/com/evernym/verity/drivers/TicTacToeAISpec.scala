package com.evernym.verity.drivers

import com.evernym.verity.protocol.actor.ActorDriverGenParam
import com.evernym.verity.protocol.engine.Driver.SignalHandler
import com.evernym.verity.protocol.engine.SimpleControllerProviderInputType
import com.evernym.verity.protocol.protocols.tictactoe.Board.{CellValue, O, X}
import com.evernym.verity.protocol.engine.ProtocolRegistry._
import com.evernym.verity.protocol.protocols.tictactoe.{Board, State, TicTacToeProtoDef}
import com.evernym.verity.util.intTimes
import com.evernym.verity.protocol.protocols.tictactoe.TicTacToeMsgFamily.{MakeMove, MakeOffer}
import com.evernym.verity.protocol.testkit.{InteractionController, ProtocolTestKit}
import com.evernym.verity.testkit.BasicSpec


import scala.util.Random

// NOTE: Tests are attempted many times due to the random nature of TicTacToeAI.
// Covering all possible scenarios is impractical, though TODO: we probably want to add deterministic tests as well.

class TicTacToeAISpec extends ProtocolTestKit(TicTacToeProtoDef) with BasicSpec {

  "TicTacToeAI.getRandomCell" - {
    "should return a valid cell" in {
      1000 times {
        assert(Board.positions.contains(TicTacToeAI.getRandomCell(Board.positions)))
      }
    }
  }

  "TicTacToeAI.chooseCell" - {
    "should return a valid cell given a valid board" in {
      1000 times {
        val board = randomBoard()
        val newCell = TicTacToeAI.chooseCell(board)
        board.isEmptyCell(newCell) shouldBe true
      }
    }
  }

  "The TicTacToeAI Protocol Driver" - {
    "should be able to play TicTacToe" in {
      20 times { // Takes too long to test more scenarios each time.
        implicit val system = new TestSystem()

        val genParam = new ActorDriverGenParam(null, null, null, null, null, null)
        val ticTacToeAI = new TicTacToeAI(genParam)

        val controllerProvider = { i: SimpleControllerProviderInputType =>
          new InteractionController(i) {
            override def signal[A]: SignalHandler[A] = {
              case sm => ticTacToeAI.signal(sm)
            }
          }
        }
        val alice = setup("alice")
        val ai = setup("ai", controllerProvider)

        interaction (alice, ai) {
          alice ~ MakeOffer()

          ai.state shouldBe a[State.Accepted]
          alice.state shouldBe a[State.Accepted]

          alice ~ MakeMove(X, TicTacToeAI.getRandomCell(Board.positions))
          alice.state shouldBe a[State.Playing]
          ai.state shouldBe a[State.Playing]

          while (alice.state.isInstanceOf[State.Playing]) {
            alice ~ MakeMove(X, TicTacToeAI.chooseCell(alice.state.asInstanceOf[State.Playing].game.board))
          }

          alice.state shouldBe a[State.Finished]
          ai.state shouldBe a[State.Finished]
        }
      }
    }
  }

  def randomBoard(): Board = {
    val random = new Random()
    val numAttempts = random.nextInt(Board.positions.length)
    var board = new Board()
    var cellValue: CellValue = O

    for (_ <- 1 to numAttempts) {
      val cell = TicTacToeAI.getRandomCell(board.emptyCells)
      cellValue = Board.getOtherCellValue(cellValue)
      board = board.updateBoard(cellValue, cell)
    }
    board
  }
}
