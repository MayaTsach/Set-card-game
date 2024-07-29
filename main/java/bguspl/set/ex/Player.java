package bguspl.set.ex;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The queue of the incoming actions.
     */
    private BlockingQueue<Integer> actions;

    /**
     * The queue of the incoming actions.
     */
    private Dealer dealer;
    
    /**
     * The class constructor.
     *
     * @param env       - the environment object.
     * @param dealer    - the dealer object.
     * @param table     - the table object.
     * @param id        - the id of the player.
     * @param human     - {@code true} iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actions = new LinkedBlockingQueue<>(3);
        this.terminate = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            // Getting the slot reffered to by player key press
            int currSlot = 0;
            try{
                currSlot = actions.take();
            } catch(InterruptedException e){};
            // Check if there is a token in the current slot - if yes, remove it
            // If not - place a token in the current slot
            if (!table.removeToken(id, currSlot)) {
                table.placeToken(id, currSlot);
            }
            // Check - if (numOfTokens == 3)
            if(table.playerToTokens[this.id].size() == 3){
                // Add yourself to the queue to be checked
                table.setClaims.add((Integer)id); 
            }           
            while (table.playerToTokens[this.id].size() == 3) {   // While you weren't checked yet interrupt the dealer        
                dealer.interruptDealer();
                synchronized(dealer.lock){
                    try {
                        dealer.lock.wait();  // Wait until the dealer checks your set
                    } catch (InterruptedException ignored) {};
                }
                // Player checks his status.
                if (table.statusAfterCheck[id] == 1) {
                    point();
                    table.statusAfterCheck[id] = 0;
                }
                else if (table.statusAfterCheck[id] == 2) {
                    penalty();
                    table.statusAfterCheck[id] = 0;
                }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // Generates a random int between 0 (inclusive) and 12 (exclusive) which represent the table card locations
                int CurrKeyPressed = ThreadLocalRandom.current().nextInt(0, 12); 
                keyPressed(CurrKeyPressed);
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        // Terminates both aiThread and Player's thread
        if (!human) {aiThread.interrupt();}
        if (playerThread != null) {playerThread.interrupt();}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        try{
            actions.put(slot);
        } catch(InterruptedException e){};

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        try {
            Thread.sleep(env.config.pointFreezeMillis); // Sleep as long as the parameter
        } catch (InterruptedException e){};  

        // Penalty freeze end - sets name back in black
        env.ui.setFreeze(id, 0);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long remainingTime = env.config.penaltyFreezeMillis;
        while (remainingTime >= 0) {
            env.ui.setFreeze(id, remainingTime);
            try {
                Thread.sleep(1000); // Sleep as long as the parameter
            } catch (InterruptedException e) {}
            remainingTime = remainingTime - 1000;
        }
        actions.clear();
    }

    public int score() {
        return score;
    }

    public Thread getThread() {
        return this.playerThread;
    }
}
