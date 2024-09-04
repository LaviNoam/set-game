package bguspl.set.ex;


import bguspl.set.Env;

//added
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
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
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    //added
    public boolean waitPoint=false;
    public boolean waitPenalty =false;
    public boolean wait = false;
    private final long defaultState = 0;
    private final long powerNap =10;
    private Queue<Integer> actionQueue;//queue of actions
    private Queue <Integer> cardPressed;//card pressed
    private Dealer dealer;

    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.actionQueue = new LinkedBlockingQueue<>(table.maxToken);
        this.cardPressed =new LinkedBlockingQueue<>(table.maxToken);
        this.dealer=dealer;
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
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
            //main player loop
            if(waitPoint){//get point
                point();
            }
            else if(waitPenalty){//penalty for ilegal set 
                penalty();
            }
            else {
                env.ui.setFreeze(id,defaultState);//default timer
                while(table.getPlayerTokens(id).size()<=table.maxToken && !wait && !terminate){//do actions
                    if(!actionQueue.isEmpty()){
                        doAction(actionQueue.poll());
                    }
                    else {                    
                        try {
                        synchronized (this){//wait for action
                            wait();
                        }
                        } catch (InterruptedException ignored) {}         
                    }
                    }            
                while((table.getPlayerTokens(id).size()== table.maxToken && wait) &&!terminate){//wait for dealer check
                    dealer.notifyTheards();
                    try {
                        synchronized (this){
                            wait();
                        }
                    } catch (InterruptedException ignored) {}
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
                // player key press simulator
                if(waitPoint){//get point
                    point();
                }
                else if(waitPenalty){//penalty for ilegal set 
                    penalty();
                }
                else {
                    env.ui.setFreeze(id,defaultState);//default timer
                    aiRandomPress();
                    while(table.getPlayerTokens(id).size()<=table.maxToken && !wait &&!actionQueue.isEmpty() && !terminate){//do actions
                            doAction(actionQueue.poll());
                        }            
                    while((table.getPlayerTokens(id).size()== table.maxToken && wait && !terminate)){//wait for dealer check
                            dealer.notifyTheards();
                        try {
                            synchronized (this){
                                wait();
                            }
                        } catch (InterruptedException ignored) {}
                    }
                }
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
        dealer.notifyTheards();
        notifyThreads();
        try {
            playerThread.join();
        } catch (InterruptedException ignored){}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot){
        if(human){
            notifyThreads();
        }
        if(actionQueue.size()<table.maxToken && cardPressed.size()<table.maxToken && (!waitPenalty) && (!waitPoint)){
            int card=table.getSlotToCard(slot);
            if(card!=table.notExits){
                cardPressed.add(card);
                actionQueue.add(slot);
            }
        }
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
        cardPressed.clear();
        actionQueue.clear();
        long startSleep=System.currentTimeMillis();
        while(System.currentTimeMillis()<env.config.pointFreezeMillis+startSleep){
            try {
                env.ui.setFreeze(id,env.config.pointFreezeMillis+startSleep-System.currentTimeMillis());
                Thread.sleep(powerNap);
            } catch (InterruptedException e) {}
        }
        waitPoint=false;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        cardPressed.clear();
        actionQueue.clear();
        long startSleep=System.currentTimeMillis();
        while(System.currentTimeMillis()<env.config.penaltyFreezeMillis+startSleep){
        try {
            env.ui.setFreeze(id,env.config.penaltyFreezeMillis+startSleep-System.currentTimeMillis());
            Thread.sleep(powerNap);
        } catch (InterruptedException ignored){}
        }
        waitPenalty=false;    
    }
    
    public int score() {
        return score;
    }
    
    //added functions
    public void aiRandomPress(){//press random card slot
            List <Integer> currentCardonTable = table.currentCardonTable();
            if(!currentCardonTable.isEmpty()){
                Collections.shuffle(currentCardonTable);
                int card = currentCardonTable.get(table.first);
                currentCardonTable.remove(table.first);   
                int slot = table.getCardToSlot(card);
                if(slot!=table.notExits){
                    keyPressed(slot);
                }
                return;
        }
    }
    public void doAction(int action){
        table.SemAcquire();//semphore so the card will not be deleted in the middle of action by the dealer   
        if(action==table.getCardToSlot(cardPressed.poll())){//check if same card when we pressed on it
            if(!table.getPlayerTokens(id).contains(action)){
                if(table.getPlayerTokens(id).size()!=table.maxToken){
                table.placeToken(id, action);
                    if(table.getPlayerTokens(id).size()== table.maxToken){
                        wait=true;
                        table.playersCardCheck.add(id);
                    }                
                }
            }
            else{ table.removeToken(id, action);
            }      
        }
        table.sem.release();
    }
    public synchronized void notifyThreads() {
        notifyAll();
    }

} 
