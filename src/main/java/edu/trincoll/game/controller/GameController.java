package edu.trincoll.game.controller;

import edu.trincoll.game.command.AttackCommand;
import edu.trincoll.game.command.CommandInvoker;
import edu.trincoll.game.command.GameCommand;
import edu.trincoll.game.command.HealCommand;
import edu.trincoll.game.model.Character;
import edu.trincoll.game.player.GameState;
import edu.trincoll.game.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main game controller that orchestrates turn-based combat.
 * <p>
 * Design Patterns Demonstrated:
 * <p>
 * - FACADE: Simplifies complex game loop interactions
 * - MEDIATOR: Coordinates between players, characters, and commands
 * - ITERATOR: Manages turn order
 * <p>
 * This class shows how multiple patterns work together:
 * <p>
 * - Players (Strategy) make decisions
 * - Commands (Command) encapsulate actions
 * - Controller (Mediator/Facade) orchestrates everything
 */
public class GameController {
    private final List<Character> team1;
    private final List<Character> team2;
    private final Map<Character, Player> playerMap;
    private final CommandInvoker invoker;
    private GameState gameState;

    public GameController(List<Character> team1,
                         List<Character> team2,
                         Map<Character, Player> playerMap) {
        this.team1 = new ArrayList<>(team1);
        this.team2 = new ArrayList<>(team2);
        this.playerMap = new HashMap<>(playerMap);
        this.invoker = new CommandInvoker();
        this.gameState = GameState.initial();
    }

    /**
     * Runs the main game loop until one team is defeated.
     * <p>
     * TODO 4: Implement game loop (15 points)
     * // DONE - Kayla
     * <p>
     * The game loop should:
     * <p>
     * 1. Check win condition (isGameOver())
     * 2. Process each character's turn in team1
     * 3. Check win condition again
     * 4. Process each character's turn in team2
     * 5. Update game state for next round
     * 6. Display round summary
     * <p>
     * For each turn:
     * <p>
     * - Get the Player for the character
     * - Call player.decideAction(character, allies, enemies, gameState)
     * - Execute the command using invoker
     * - Update game state
     * - Display action result
     * <p>
     * Hint: Use processTurn() helper method for each character
     */
    public void playGame() {
        while (!isGameOver()) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("ROUND " + gameState.roundNumber());
            System.out.println("=".repeat(60));

            for (Character character : team1) {
                if (isGameOver()) break;
                processTurn(character, team1, team2);
            }

            if (isGameOver()) break;

            for (Character character : team2) {
                if (isGameOver()) break;
                processTurn(character, team2, team1);
            }

            gameState = gameState.nextRound();
        }
    }

    /**
     * Processes a single character's turn.
     * <p>
     * TODO 5: Implement turn processing (10 points)
     * <p>
     * Steps:
     * <p>
     * 1. Get the player controlling this character
     * 2. Determine allies and enemies lists
     * 3. Call player.decideAction() to get a command
     * 4. Execute the command via invoker
     * 5. Display what happened
     * 6. Update game state
     * <p>
     * @param character the character taking their turn
     * @param allies the character's team
     * @param enemies the opposing team
     */

    private void processTurn(Character character,
                            List<Character> allies,
                            List<Character> enemies) {
        // Skip if character is defeated
        if (character.getStats().health() <= 0) {
            return;
        }

       System.out.println("\n" + character.getName() + "'s turn");

        Player player = playerMap.get(character);
        if (player == null) {
            throw  new IllegalStateException("No player found for character: " + character.getName());
        }

        GameCommand command = player.decideAction(character, allies, enemies, gameState);

        invoker.executeCommand(command);
        displayActionResult(command);
        gameState = gameState.nextTurn().withUndo(true, invoker.getCommandHistory().size());
    }

    /**
     * Displays the result of a single executed command.
     *
     * @param command the command that was executed
     */
    private void displayActionResult(GameCommand command) {
        if (command == null) return;

        // AttackCommand
        if (command instanceof AttackCommand attack) {
            String attackerName = attack.getAttacker().getName();
            String targetName = attack.getTarget().getName();
            int damage = attack.getDamage(); // you should have a getter in AttackCommand
            int targetHp = Math.max(0, attack.getTarget().getStats().health());

            System.out.printf("→ %s attacks %s for %d damage!%n", attackerName, targetName, damage);
            System.out.printf("  %s: %d HP%n", targetName, targetHp);

            if (targetHp == 0) {
                System.out.printf("  %s has been defeated!%n", targetName);
            }
        }
        // HealCommand
        else if (command instanceof HealCommand heal) {
            String healerName = heal.getHealer().getName(); // the character performing the heal
            String targetName = heal.getTarget().getName();
            int amount = heal.getAmount(); // amount healed
            int targetHp = heal.getTarget().getStats().health();

            System.out.printf("→ %s heals %s for %d HP!%n", healerName, targetName, amount);
            System.out.printf("  %s: %d HP%n", targetName, targetHp);
        }
        // Unknown command
        else {
            System.out.println("→ Unknown command executed.");
        }
    }



    /**
     * Checks if the game is over.
     * <p>
     * Game ends when all characters on one team are defeated (HP <= 0).
     *
     * @return true if game is over, false otherwise
     */
    private boolean isGameOver() {
        boolean team1Alive = team1.stream()
            .anyMatch(c -> c.getStats().health() > 0);
        boolean team2Alive = team2.stream()
            .anyMatch(c -> c.getStats().health() > 0);

        return !team1Alive || !team2Alive;
    }

    /**
     * Displays the game result.
     */
    public void displayResult() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("GAME OVER");
        System.out.println("=".repeat(60));

        boolean team1Wins = team1.stream().anyMatch(c -> c.getStats().health() > 0);

        if (team1Wins) {
            System.out.println("🏆 Team 1 wins!");
        } else {
            System.out.println("🏆 Team 2 wins!");
        }

        System.out.println("\nFinal Status:");
        System.out.println("\nTeam 1:");
        for (Character c : team1) {
            displayCharacterStatus(c);
        }

        System.out.println("\nTeam 2:");
        for (Character c : team2) {
            displayCharacterStatus(c);
        }

        System.out.println("\nTotal turns played: " + gameState.turnNumber());
        System.out.println("Total commands executed: " + gameState.commandHistorySize());
    }

    private void displayCharacterStatus(Character c) {
        String status = c.getStats().health() > 0 ? "Alive" : "Defeated";
        System.out.printf("  %s (%s): %d HP - %s%n",
            c.getName(),
            c.getType(),
            Math.max(0, c.getStats().health()),
            status);
    }
}
