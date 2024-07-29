package bguspl.set.ex;

import bguspl.set.Env;


import java.util.ArrayList;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Flag to notify the dealer he got a valid set and should remove cards
     * from the set of the ID of the player.
     * {@code player ID} if the dealer got a set from player ID and found it valid.
     * After removing the correspondeing 3 cards, restored to {@code -1}.
     * {@code -1} otherwise.
     */
    private int idValidSet;

    /**
     * Lock for checking a set
     */
    protected final Object lock = new Object();

    
    /**
     * The class constructor.
     * 
     * @param env       - the environment object.
     * @param table     - the table object.
     * @param players   - Array of all the players.
     */

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.idValidSet = -1;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        
        // Starting the players' threads
        for (Player currPlayer : players) {
            Thread currPlayerThread = new Thread(currPlayer);
            currPlayerThread.start();
        }
        // Sets the timer
        updateTimerDisplay(true);

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        terminate();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // Terminates players' threads
        for (Player currPlayer : players) {
            currPlayer.terminate();
            try {
                currPlayer.getThread().join();
            } catch (InterruptedException e) {}
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if (idValidSet != -1) {
            // Remove 3 cards.
            LinkedList<Integer> slotsToRemove = table.playerToTokens[idValidSet]; // Holds appropriate 3 slots to remove cards from.
            for (int i = 0; i < 3; i++) {
                table.removeCard(slotsToRemove.remove());
            }
            // Reset timer
            updateTimerDisplay(true);

            // Cards have been deleted - restoring the flag to -1
            idValidSet = -1;
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {  
        List<Integer> emptySlots = table.getEmptySlots();
        Collections.shuffle(emptySlots);
        shuffleDeck();  // Shuffle the deck
        while(deck.size() > 0 && emptySlots.size() > 0){  // Only if the deck has cards and there are still empty slots
            table.placeCard(deck.get(0), emptySlots.get(0));  // Place a card
            // Remove from both lists
            deck.remove(0);
            emptySlots.remove(0);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // Sleep for 10 milliseconds
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {}
        
        // Pull a player Id that waits to be checked, will be null if there are none
        Integer currIdCheck = table.setClaims.poll();
        
        // Find the first player that has 3 tokens
        while (currIdCheck != null && table.playerToTokens[currIdCheck].size() < 3) {
            currIdCheck = table.setClaims.poll(); 
        }
        // Check his set (only if there is a player)
        if (currIdCheck != null && table.playerToTokens[currIdCheck].size() == 3) {
            checkSet(currIdCheck);
        }
        // Wake up all players waiting to be checked
        synchronized(lock){
            lock.notifyAll();
        }
    }

    /**
     * Checks if the 3 tokens of a player are a legal set and gives him a point/penalty
     *  @param playerId The player id to check his set
     */
    private void checkSet(int playerId){
        int[] set = setOfCards(playerId);
        if(env.util.testSet(set)) {
            table.statusAfterCheck[playerId] = 1;
            //players[playerId].point();
            idValidSet = playerId;
        } else {
            table.statusAfterCheck[playerId] = 2;
            //players[playerId].penalty();
            //remove his tokens
            table.removePlayerTokens(playerId);
        }         
    }

    /**
     * Converts the 3 tokens of the player to an array of cards to be sent to the function "testSet"
     * Assuming there are 3 tokens for the player
     * @param playerId The player id to check his tokens
     */
    private int[] setOfCards(int playerId){
        int[] finalSet = new int[3];
        LinkedList<Integer> tokens = table.playerToTokens[playerId];
        for(int i = 0; i < 3; i++){
            finalSet[i] = table.slotToCard[tokens.get(i)];
        }
        return finalSet;
    }


    protected void interruptDealer(){
        Thread.currentThread().interrupt();
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;  //if needs to reset update the re-shuffle time
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        } 
        // Change clock to red
        else if (reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis) {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        }
        else{
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false); 
        }
    }

    /**
     * Returns all the cards from the table to the deck.x
     */
    private void removeAllCardsFromTable() {
        List<Integer> slots = new LinkedList<>();
        for(int i = 0; i < 12 ; i++)
            slots.add(i);
        Collections.shuffle(slots);

        while(!slots.isEmpty()){  //run on all slots
            int currSlot = slots.remove(0);
            if(table.slotToCard[currSlot] != null){ //if it's not empty- puts it in the deck
                deck.add(table.slotToCard[currSlot]);
                table.removeCard(currSlot);
            }
        } 
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        Integer highestScore = 0;
        List<Integer> currWinners = new ArrayList<>(); //saves the current winners
        int[] winners; //what will be sent to the ui method
        //find the winners
        for(Player currPlayer : players){
            if(currPlayer.score() > highestScore){
                highestScore = currPlayer.score();  //update the highest score
                currWinners.clear();  //clear the winners found until now
                currWinners.add(currPlayer.id);  //add the player as winner
            }
            else if(currPlayer.score() == highestScore)  //if equal to the highest score add to the list
                currWinners.add(currPlayer.id);
        }
        
        //move from the list to the array the winners
        winners = new int[currWinners.size()];
        int i = 0;
        for(Integer CurrWinPlayer : currWinners){
            winners[i] = CurrWinPlayer;
            i = i + 1;
        }

        env.ui.announceWinner(winners);  //display the winners
    }

    private void shuffleDeck(){
        Collections.shuffle(deck);
    }
}
