package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Mapping between a player and the tokens he put (kept by slots)
     */
    protected LinkedList<Integer>[] playerToTokens;

    /**
     * A queue for the players' ids that claim a set to be checked
     */
    protected volatile ConcurrentLinkedQueue<Integer> setClaims;

    /**
     * Array to hold status after check.
     * After the dealer checks the set claimed by a player, he changes {@code statusAfterCheck[playerId]}.
     * 0 - default.
     * 1 - point.
     * 2 - penalty.
     * After taking action by player thread, the value is reset to 0.
     */
    protected int[] statusAfterCheck;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.setClaims = new ConcurrentLinkedQueue<>();
        this.playerToTokens = new LinkedList[this.env.config.players];
        this.statusAfterCheck = new int[this.env.config.players];
        for (int i = 0; i < env.config.players; i++) {
            this.playerToTokens[i] = new LinkedList<>();
            this.statusAfterCheck[i] = 0;
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
         
        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;

        env.ui.removeCard(slot);

        // Removing the tokens on it
        Integer token = slot;
        for (int i = 0; i < playerToTokens.length; i++){
            playerToTokens[i].remove(token);
            env.ui.removeToken(i, slot);
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {

        if (slotToCard[slot] != null) {
            // Adding the token to the appropriate player.
            Integer token = slot;
            if (!isTokenExists(player, token)) {
                playerToTokens[player].add(token);
                env.ui.placeToken(player, slot);
            }
        }
        
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {

        if (slotToCard[slot] != null) {
            // Removing the token to the appropriate player.
            Integer token = slot;
            if (isTokenExists(player, token)) {
                playerToTokens[player].remove(token);
                env.ui.removeToken(player, slot);
                return true; // Slot exists in the player's tokens list
            }         
        }
        return false;
    }

    /**
     * Returns the slotToCard array. 
     * @return       - slotToCard array.
     */
    public Integer[] getSlotToCard() {
        return slotToCard;
    }


    /**
     * Returns all empty slots on the table. 
     * @return       - a list of the slots.
     */
    public List<Integer> getEmptySlots() {
        List<Integer> emptySlots = new ArrayList<>();
        for(int i = 0; i < slotToCard.length; i++) //run on all slots
            if(slotToCard[i] == null) //if a slot is empty (null) add to the list
                emptySlots.add(i);
        return emptySlots;
    }

    /**
     * Removes all tokens of the playerId given
     * @param playerId The player id to remove his tokens
     * 
     */
    public void removePlayerTokens(int playerId){
        for (int i = 0; i < 3; i++){
            removeToken(playerId, playerToTokens[playerId].getFirst());
        }            
    }


     /**
     * Checks whether a token exists for a specified player in a given slot.
     *
     * @param player The player index for whom the token existence needs to be checked.
     *               This value should be within the range of valid player indices.
     * @param slot   The slot number to check for the existence of a token.
     * @return {@code true} if a token exists for the specified player in the given slot,
     *         {@code false} otherwise.
     */
    public boolean isTokenExists(int player, int slot) {
        LinkedList<Integer> tokensList = playerToTokens[player];

        // If no tokens list exists for the specified player, return false
        if (tokensList == null) {
            return false;
        }

        // Iterate through the player's tokens list to check for the existence of the slot
        for (Integer token : tokensList) {
            if (token == slot) {
                return true; // Slot exists in the player's tokens list
            }
        }

        // Slot does not exist in the player's tokens list
        return false;
    }
}
