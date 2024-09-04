package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
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
    
    //added fileds
    private long lastResetTime;
    private final long powerNap = 10; 
    private Queue <Integer> removedCards;
    
    
    public Dealer(Env env, Table table, Player[] players) {
        this.removedCards=new ArrayDeque<Integer>();
        this.reshuffleTime = env.config.turnTimeoutMillis;
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.terminate=false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

         for(int i=0;i<players.length;i++){//added create Threads
             Thread playerThread =new Thread(players[i],env.config.playerNames[i]);
             playerThread.start();
         }
        while (!shouldFinish()) {
            table.SemAcquire();
            placeCardsOnTable();
            table.sem.release();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && ((System.currentTimeMillis() - lastResetTime < reshuffleTime)||(reshuffleTime<=0)
        &&!table.notExistSetinTable())&&!(table.notExistSetinTable()&&deck.isEmpty())) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            table.SemAcquire();   
            checkQueueSet();
            removeCardsFromTable();
            placeCardsOnTable();
            table.sem.release();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate=true;
        for (int i=players.length-1; i>=0; i--){
            players[i].terminate();
        }
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
        if(!removedCards.isEmpty()){
            int id = removedCards.poll();
            int slot =table.getCardToSlot(id);
            table.removeCard(slot);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        if(!deck.isEmpty() && table.countCards() < env.config.tableSize){
            updateTimerDisplay(true);
            while(!deck.isEmpty() && table.countCards() < env.config.tableSize){        
                int AvailableSlot=table.getAvailableSlot();
                if(AvailableSlot!=table.notExits){
                    table.placeCard(deck.remove(table.first),AvailableSlot);
                }
            }
            if(env.config.hints)
                table.hints();
            updateTimerDisplay(true);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {//sleep for fixed time=0.01 sec
        if(0<=env.config.turnTimeoutMillis){
            try {
                Thread.sleep(powerNap);
            } catch (InterruptedException ignored) {};
        }
        else {try {
            synchronized (this){//wait for action
                wait();
            }
            } catch (InterruptedException ignored) {}         
        }
    }
     
    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            lastResetTime = System.currentTimeMillis();
            if(env.config.turnTimeoutMillis==0){
                env.ui.setElapsed(0);
            }
            if(env.config.turnTimeoutMillis>0){
                env.ui.setCountdown(env.config.turnTimeoutMillis,false);
            }
        }
        else {
            if(env.config.turnTimeoutMillis > 0){
                if (env.config.turnTimeoutMillis-(System.currentTimeMillis()-lastResetTime) <= env.config.turnTimeoutWarningMillis){
                    env.ui.setCountdown(env.config.turnTimeoutMillis-(System.currentTimeMillis()-lastResetTime),true);
                    if(env.config.turnTimeoutMillis-(System.currentTimeMillis()-lastResetTime)<=0){
                        env.ui.setCountdown(0,false);
                    }
                }
                else env.ui.setCountdown(env.config.turnTimeoutMillis-(System.currentTimeMillis()-lastResetTime),false);
            }
            else if(env.config.turnTimeoutMillis==0){//time from last reset
                env.ui.setElapsed(System.currentTimeMillis()-lastResetTime);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.SemAcquire();
        List<Integer> cardsOnTable =table.currentCardonTable();
        Collections.shuffle(cardsOnTable);
        for (Integer card : cardsOnTable) {
            if(card!=table.notExits){
                deck.add(card);
                table.removeCard(table.getCardToSlot(card));
            }
        }
        table.sem.release();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List <Integer> winnerList=new ArrayList<Integer>();
        int currentWinnerScore=0;
        for(Player player :players){
            if(currentWinnerScore<player.score()){//first winner
                currentWinnerScore=player.score();
                winnerList.clear();//clear before winner list
                winnerList.add(player.id);
            }
            else if( player.score() == currentWinnerScore){//if more than one winner
                winnerList.add(player.id);
            }
        }    
        int [] winner=winnerList.stream().mapToInt(i->i).toArray(); 
        //ui update the winner list
        env.ui.announceWinner(winner);
    }
    //add function
    private void checkQueueSet(){
        while(!table.playersCardCheck.isEmpty()){//have players waiting for set check
            boolean removedCard=false;
            int PlayerId = table.playersCardCheck.poll();//get player id
            List<Integer> playertokens = table.getPlayerTokens(PlayerId);//get player token list
            int[] setOfCards =new int[env.config.featureSize]; 
            int i=0;
            for(int slots: playertokens){//check players tokens
                setOfCards[i]=table.getSlotToCard(slots);//set
                if(removedCards.contains(setOfCards[i])){// check if card already removed from other player set
                    removedCard=true;
                }
                i++;
            }
            if(removedCard==false){//exists cards
                if(env.util.testSet(setOfCards)){
                    players[PlayerId].waitPoint=true;//add point in player loop
                    for(int j = 0 ; j <env.config.featureSize;j++){
                        removedCards.add(setOfCards[j]);//add card id to removed
                    }
                }
                else {
                    players[PlayerId].waitPenalty=true;//Penalty player loop
                }
            }
            players[PlayerId].wait=false;
            players[PlayerId].notifyThreads();//notify all waiting players threads
        }
    }
    public synchronized void notifyTheards(){
        notifyAll();
    }
    
}

