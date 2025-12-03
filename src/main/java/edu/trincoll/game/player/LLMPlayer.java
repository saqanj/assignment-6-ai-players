package edu.trincoll.game.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.trincoll.game.command.AttackCommand;
import edu.trincoll.game.command.GameCommand;
import edu.trincoll.game.command.HealCommand;
import edu.trincoll.game.model.Character;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Comparator;
import java.util.List;

/**
 * LLM-based AI player using Spring AI.
 * <p>
 * This class demonstrates how to integrate Large Language Models
 * into game AI using the Strategy pattern. The LLM acts as the
 * decision-making algorithm, making this player fundamentally
 * different from rule-based AI.
 * <p>
 * Design Patterns:
 * <p>
 * - STRATEGY: Implements Player interface with LLM-based decisions
 * - ADAPTER: Adapts LLM output format to game commands
 * - FACADE: Simplifies complex LLM interaction
 * <p>
 * Students will implement the prompt engineering and response parsing.
 */
public class LLMPlayer implements Player {
    private final ChatClient chatClient;
    private final String modelName;

    public LLMPlayer(ChatClient chatClient, String modelName) {
        this.chatClient = chatClient;
        this.modelName = modelName;
    }

    @Override
    public GameCommand decideAction(Character self,
                                    List<Character> allies,
                                    List<Character> enemies,
                                    GameState gameState) {
        String prompt = buildPrompt(self, allies, enemies, gameState);
        Decision decision = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(Decision.class);

        // TODO 3: Convert Decision to GameCommand (10 points)
        // DONE - AJ
        // Based on the decision.action(), create the appropriate GameCommand:
        // - "attack" -> new AttackCommand(self, target)
        // - "heal" -> new HealCommand(self, target)
        //
        // Use findCharacterByName() to locate the target character
        // Hint: Use a switch expression or if-else to handle different actions
        if (decision == null || decision.action() == null || decision.target() == null) {
            System.out.println("[" + modelName + "] Invalid decision from LLM. Using fallback action.");
            return defaultAction(self, enemies, allies);
        }

        Character targetCharacter = switch (decision.action().toLowerCase()) {
            case "attack" -> findCharacterByName(decision.target(), enemies);
            case "heal" -> findCharacterByName(decision.target(), allies);
            default -> null;
        };

        if (targetCharacter == null) {
            System.out.println("[" + modelName + "] Invalid decision from LLM. Using default.");
            return defaultAction(self, enemies, allies);
        }

        return switch (decision.action().toLowerCase()) {
            case "attack" -> new AttackCommand(self, targetCharacter);
            case "heal" -> new HealCommand(self,targetCharacter, 30);
            default -> defaultAction(self, enemies, allies);
        };
    }

    private GameCommand defaultAction(Character self, List<Character> enemies, List<Character> allies) {
        Character allyToHeal = allies.stream()
                .filter(c -> c.getStats().health() < c.getStats().maxHealth() * 0.3).findFirst().orElse(null);
        if (allyToHeal == null) {
            System.out.println("[" + modelName + "] Default action: healing " + allyToHeal.getName());
            return new HealCommand(self, allyToHeal, 30);
        }

        Character weakestEnemy = enemies.stream()
                .filter(c -> c.getStats().health() > 0)
                .min(Comparator.comparingInt(c -> c.getStats().health()))
                .orElse(enemies.get(0));
        System.out.println("[" + modelName + "] Default action: attacking " + weakestEnemy.getName());
        return new AttackCommand(self, weakestEnemy);
    }
    /**
     *
     * A good prompt should include:
     * 1. Role definition: "You are a [character type] in a tactical RPG..."
     * 2. Current situation: HP, mana, position in battle
     * 3. Allies: Who's on your team and their status
     * 4. Enemies: Who you're fighting and their status
     * 5. Available actions: attack (with damage estimate) or heal
     * 6. Strategic guidance: "Consider focus fire, protect wounded allies..."
     * 7. Output format: JSON structure expected
     *
     * Example structure:
     * """
     * You are {character_name}, a {type} warrior in a turn-based RPG battle.
     *
     * YOUR STATUS:
     * - HP: {current}/{max} ({percent}%)
     * - Mana: {current}/{max}
     * - Attack Power: {attack}
     * - Defense: {defense}
     *
     * YOUR TEAM:
     * {list allies with HP and status}
     *
     * ENEMIES:
     * {list enemies with HP and status}
     *
     * AVAILABLE ACTIONS:
     * 1. attack <target_name> - Deal ~{estimate} damage
     * 2. heal <target_name> - Restore 30 HP
     *
     * STRATEGY TIPS:
     * - Focus fire on weak enemies to reduce enemy actions
     * - Heal allies below 30% HP to prevent deaths
     * - Consider your character type's strengths
     *
     * Respond with JSON:
     * {
     *   "action": "attack" or "heal",
     *   "target": "character name",
     *   "reasoning": "brief explanation"
     * }
     * """
     *
     * @param self your character
     * @param allies your team
     * @param enemies opponent team
     * @param gameState current game state
     * @return prompt string for the LLM
     */
    private String buildPrompt(Character self,
                               List<Character> allies,
                               List<Character> enemies,
                               GameState gameState) {

        StringBuilder sb = new StringBuilder();
        int selfHp = self.getStats().health();
        int selfMaxHp = self.getStats().maxHealth();
        double selfHpPercent = (double) selfHp / selfMaxHp * 100.0;

        int selfMana = self.getStats().mana();
        int selfMaxMana = self.getStats().maxMana();

        int atk = self.getStats().attackPower();
        int def = self.getStats().defense();
        String attackStrategyName = self.getAttackStrategy() != null
                ? self.getAttackStrategy().getClass().getSimpleName()
                : "UnknownAttackStrategy";
        String defenseStrategyName = self.getDefenseStrategy() != null
                ? self.getDefenseStrategy().getClass().getSimpleName()
                : "UnknownDefenseStrategy";
        sb.append("You are ")
                .append(self.getName())
                .append(", a ")
                .append(self.getType())
                .append(" in tactical turn-based RPG combat.")
                .append("\n\n");
        sb.append("YOUR STATUS:\n");
        sb.append(String.format("- HP: %d/%d (%.0f%%)%n", selfHp, selfMaxHp, selfHpPercent));
        sb.append(String.format("- Mana: %d/%d%n", selfMana, selfMaxMana));
        sb.append(String.format("- Attack Power: %d, Defense: %d%n", atk, def));
        sb.append(String.format("- Strategies: %s, %s%n", attackStrategyName, defenseStrategyName));
        sb.append("\n");
        sb.append("YOUR TEAM (allies):\n");
        if (allies == null || allies.isEmpty()) {
            sb.append("  - (no allies)\n");
        } else {
            sb.append(formatCharacterList(allies));
        }
        sb.append("\n");
        sb.append("ENEMIES:\n");
        if (enemies == null || enemies.isEmpty()) {
            sb.append("  - (no enemies)\n");
        } else {
            sb.append(formatCharacterList(enemies));
        }
        sb.append("\n");
        sb.append("AVAILABLE ACTIONS:\n");
        if (enemies != null && !enemies.isEmpty()) {
            for (Character enemy : enemies) {
                int estimatedDamage = estimateDamage(self, enemy);
                sb.append(String.format(
                        "  - attack \"%s\" - Estimated damage: ~%d%n",
                        enemy.getName(),
                        estimatedDamage
                ));
            }
        } else {
            sb.append("  - (no valid attack targets)\n");
        }

        if (allies != null && !allies.isEmpty()) {
            for (Character ally : allies) {
                sb.append(String.format(
                        "  - heal \"%s\" - Restores ~30 HP%n",
                        ally.getName()
                ));
            }
        } else {
            sb.append("  - heal <ally_name> - (no allies available to heal)\n");
        }
        sb.append("\n");
        sb.append("TACTICAL GUIDANCE:\n");
        sb.append("- Focus fire: Attack wounded enemies to eliminate threats quickly.\n");
        sb.append("- Protect allies: Prefer healing teammates below 30% HP.\n");
        sb.append("- Consider your role: ")
                .append(getRoleAdvice(self))
                .append("\n\n");
        sb.append("Respond ONLY with JSON in the following format (no extra text):\n");
        sb.append("{\n");
        sb.append("  \"action\": \"attack\" | \"heal\",\n");
        sb.append("  \"target\": \"character_name\",\n");
        sb.append("  \"reasoning\": \"brief tactical explanation\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Simple helper to give type-specific strategic advice.
     */
    private String getRoleAdvice(Character self) {
        String type = self.getType().toString().toLowerCase();

        if (type.contains("mage") || type.contains("wizard")) {
            return "Use your high damage output to finish off low-HP enemies, but avoid wasting turns on very tanky foes.";
        } else if (type.contains("healer") || type.contains("cleric") || type.contains("support")) {
            return "Prioritize healing critically wounded allies; only attack when your team is relatively safe.";
        } else if (type.contains("tank") || type.contains("warrior") || type.contains("knight")) {
            return "Focus on dangerous enemies that threaten your fragile allies and keep your HP high.";
        } else {
            return "Balance attacking vulnerable enemies with healing low-HP allies depending on who is in more danger.";
        }
    }


    /**
     * Formats a list of characters for display in the prompt.
     *
     * Helper method provided to students.
     */
    private String formatCharacterList(List<Character> characters) {
        StringBuilder sb = new StringBuilder();
        for (Character c : characters) {
            double healthPercent = (double) c.getStats().health() / c.getStats().maxHealth() * 100;
            sb.append(String.format("  - %s (%s): %d/%d HP (%.0f%%), %d ATK, %d DEF%n",
                c.getName(),
                c.getType(),
                c.getStats().health(),
                c.getStats().maxHealth(),
                healthPercent,
                c.getStats().attackPower(),
                c.getStats().defense()));
        }
        return sb.toString();
    }

    /**
     * Estimates damage this character would deal to a target.
     *
     * Helper method provided to students.
     */
    private int estimateDamage(Character attacker, Character target) {
        // Rough estimate using attack strategy
        int baseDamage = attacker.attack(target);
        return target.getDefenseStrategy()
            .calculateDamageReduction(target, baseDamage);
    }

    /**
     * Finds a character by name in a list.
     *
     * Helper method provided to students.
     */
    private Character findCharacterByName(String name, List<Character> characters) {
        return characters.stream()
            .filter(c -> c.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(characters.getFirst()); // Fallback to first if not found
    }

    /**
     * Record for parsing LLM JSON response.
     *
     * Uses Jackson annotations for JSON deserialization.
     * This is provided to students as a reference for JSON structure.
     */
    public record Decision(
        @JsonProperty(required = true) String action,
        @JsonProperty(required = true) String target,
        @JsonProperty String reasoning
    ) {}
}
